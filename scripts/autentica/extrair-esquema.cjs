/* Reproduz as migrações em memória; não abre conexão e não lê arquivos de credenciais. */
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const origem=process.env.AUTENTICA_ROOT||'/home/aluno/autentica/zapio';
const S=require(path.join(origem,'model/node_modules/sequelize'));
const instancia=new S('extracao','sem-acesso','sem-acesso',{dialect:'postgres',logging:false});
const gerador=instancia.getQueryInterface().queryGenerator;
const saida=process.argv[2]||path.resolve('src/main/resources');
const todos={},fontes=[];
async function extrair(schema,pastas,modelos){
 const tabelas={};
 const q={createTable:async(n,a)=>{if(tabelas[n])throw Error('Tabela repetida '+n);tabelas[n]={colunas:{...a},indices:[]};},addColumn:async(t,n,a)=>{if(!tabelas[t])throw Error('Tabela ausente '+t);tabelas[t].colunas[n]={...a};},removeColumn:async(t,n)=>{delete tabelas[t].colunas[n];},addIndex:async(t,fields,o)=>tabelas[t].indices.push({fields,...o})};
 for(const pasta of pastas)for(const f of fs.readdirSync(path.join(origem,pasta)).filter(n=>n.endsWith('.js')).sort()){
  const arquivo=path.join(origem,pasta,f);fontes.push({arquivo:path.relative(origem,arquivo),sha256:crypto.createHash('sha256').update(fs.readFileSync(arquivo)).digest('hex')});await require(arquivo).up(q,S);
 }
 const noop=()=>{};
 const fake={define(n,attrs,opts){if(!tabelas[n])throw Error('Modelo sem migração '+n);for(const [campo,a]of Object.entries(attrs))if(!tabelas[n].colunas[campo])tabelas[n].colunas[campo]={...a};
  for(const idx of opts?.indexes||[])if(!tabelas[n].indices.some(v=>JSON.stringify(v.fields)===JSON.stringify(idx.fields)))tabelas[n].indices.push(idx);
  return new Proxy({},{get:()=>noop});}};
 for(const f of fs.readdirSync(path.join(origem,modelos)).filter(n=>n.endsWith('.js'))){const arquivo=path.join(origem,modelos,f);fontes.push({arquivo:path.relative(origem,arquivo),sha256:crypto.createHash('sha256').update(fs.readFileSync(arquivo)).digest('hex')});const contexto={module:{exports:{}},require:n=>n==='sequelize'?S:new Proxy(()=>{},{get:()=>()=>{}}),console};require('node:vm').runInNewContext(fs.readFileSync(arquivo,'utf8'),contexto,{filename:arquivo});contexto.module.exports(fake,S);}
 todos[schema]=tabelas;
}
(async()=>{
 await extrair('autentica',['model/sequelize/migrations'],'model/src/tabelas');
 await extrair('autentica_identidade',['model/sequelize_AutenticaAI/migrations'],'autenticaai/src/Tabelas');
 const carga={};for(const [arquivo,chave,exportacao]of [['Perfis.js','perfis','Perfis'],['Permissoes.js','permissoes','Permissoes'],['PermissaoPerfil.js','vinculos','PermissaoPerfil']]){const f=path.join(origem,'model/src/AutenticaAI/CargaInicial',arquivo);carga[chave]=require(f)[exportacao];fontes.push({arquivo:path.relative(origem,f),sha256:crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex')});}
 for(const relativo of ['model/src/database/connector.js','autenticaai/src/BancoDeDados/Conector.js','autenticaai/src/Estrategias/Local/autenticacao-local.js']){fontes.push({arquivo:relativo,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(origem,relativo))).digest('hex')});}
 const sql=['-- Gerado das migrações e modelos do Autentica. Reproduzir: scripts/autentica/extrair-esquema.cjs','-- Não altera nem apaga as tabelas anteriores da FIRMA.'];const refs=[],indices=[],metadata={fontes,tabelas:[]};
 for(const [schema,tabelas]of Object.entries(todos)){
  sql.push(`CREATE SCHEMA ${gerador.quoteIdentifier(schema)};`);
  for(const [nome,tabela]of Object.entries(tabelas)){
   const attrs={},colunas=[];
   for(const [coluna,original]of Object.entries(tabela.colunas)){
    const a=instancia.normalizeAttribute({...original});if(a.references){refs.push({schema,tabela:nome,coluna,alvo:typeof a.references.model==='string'?a.references.model:a.references.model.tableName,chave:a.references.key||'id',onDelete:a.onDelete||'NO ACTION',onUpdate:a.onUpdate||'NO ACTION'});delete a.references;delete a.onDelete;delete a.onUpdate;}
    attrs[coluna]=a;const tipo=typeof a.type==='string'?a.type:a.type.toSql();colunas.push({nome:coluna,tipo,obrigatorio:a.allowNull===false,primaria:!!a.primaryKey,automatica:!!a.autoIncrement});
   }
   sql.push(gerador.createTableQuery({tableName:nome,schema},gerador.attributesToSQL(attrs,{table:nome,context:'createTable'}),{}));
   for(const idx of tabela.indices){const {fields,...opts}=idx;indices.push(gerador.addIndexQuery({tableName:nome,schema},fields,opts)+';');}
   metadata.tabelas.push({schema,nome,colunas,indices:tabela.indices});
  }
 }
 for(const r of refs){const table=`${gerador.quoteIdentifier(r.schema)}.${gerador.quoteIdentifier(r.tabela)}`;
  sql.push(`ALTER TABLE ${table} ADD FOREIGN KEY (${gerador.quoteIdentifier(r.coluna)}) REFERENCES ${gerador.quoteIdentifier(r.schema)}.${gerador.quoteIdentifier(r.alvo)} (${gerador.quoteIdentifier(r.chave)}) ON DELETE ${r.onDelete} ON UPDATE ${r.onUpdate};`);}
 sql.push(...indices);metadata.relacionamentos=refs;
 fs.mkdirSync(path.join(saida,'autentica'),{recursive:true});fs.writeFileSync(path.join(saida,'autentica/carga-inicial.json'),JSON.stringify(carga,null,2)+'\n');fs.mkdirSync(path.join(saida,'db/migration'),{recursive:true});
 fs.writeFileSync(path.join(saida,'db/migration/V25__estrutura_autentica.sql'),sql.join('\n\n')+'\n');fs.writeFileSync(path.join(saida,'autentica/esquema.json'),JSON.stringify(metadata,null,2)+'\n');
 console.log(JSON.stringify({tabelas:metadata.tabelas.length,colunas:metadata.tabelas.reduce((n,t)=>n+t.colunas.length,0),relacionamentos:refs.length,indices:indices.length,fontes:fontes.length}));await instancia.close();
})().catch(e=>{console.error(e);process.exit(1)});
