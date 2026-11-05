/* Lê as definições das tabelas sem abrir conexão com o banco nem ler senhas. */
const arquivosEmDisco = require('node:fs'),
  caminhos = require('node:path'),
  criptografia = require('node:crypto');
const origem = process.env.FIRMA_REFERENCIA_ROOT || '/home/aluno/autentica/zapio';
const tiposBanco = require(caminhos.join(origem, 'model/node_modules/sequelize'));
const instancia = new tiposBanco('extracao', 'sem-acesso', 'sem-acesso', {
  dialect: 'postgres',
  logging: false
});
const gerador = instancia.getQueryInterface().queryGenerator;
const saida = process.argv[2] || caminhos.resolve('/tmp/firma-referencias');
const todos = {},
  fontes = [];
async function extrair(esquema, pastas, modelos) {
  const tabelas = {};
  const consulta = {
    createTable: async (valor, registro) => {
      if (tabelas[valor]) throw Error('Tabela repetida ' + valor);
      tabelas[valor] = {
        colunas: {
          ...registro
        },
        indices: []
      };
    },
    addColumn: async (item, valor, registro) => {
      if (!tabelas[item]) throw Error('Tabela ausente ' + item);
      tabelas[item].colunas[valor] = {
        ...registro
      };
    },
    removeColumn: async (item, valor) => {
      delete tabelas[item].colunas[valor];
    },
    addIndex: async (item, campos, opcao) => tabelas[item].indices.push({
      fields: campos,
      ...opcao
    })
  };
  for (const pasta of pastas) for (const campoAtual of arquivosEmDisco.readdirSync(caminhos.join(origem, pasta)).filter(valor => valor.endsWith('.js')).sort()) {
    const arquivo = caminhos.join(origem, pasta, campoAtual);
    fontes.push({
      arquivo: caminhos.relative(origem, arquivo),
      sha256: criptografia.createHash('sha256').update(arquivosEmDisco.readFileSync(arquivo)).digest('hex')
    });
    await require(arquivo).up(consulta, tiposBanco);
  }
  const ignorar = () => {};
  const simulador = {
    define(valor, atributos, opcoes) {
      if (!tabelas[valor]) throw Error('Modelo sem migração ' + valor);
      for (const [campo, registro] of Object.entries(atributos)) if (!tabelas[valor].colunas[campo]) tabelas[valor].colunas[campo] = {
        ...registro
      };
      for (const idx of opcoes?.indexes || []) if (!tabelas[valor].indices.some(valorAtual => JSON.stringify(valorAtual.fields) === JSON.stringify(idx.fields))) tabelas[valor].indices.push(idx);
      return new Proxy({}, {
        get: () => ignorar
      });
    }
  };
  for (const campoAtual of arquivosEmDisco.readdirSync(caminhos.join(origem, modelos)).filter(valor => valor.endsWith('.js'))) {
    const arquivo = caminhos.join(origem, modelos, campoAtual);
    fontes.push({
      arquivo: caminhos.relative(origem, arquivo),
      sha256: criptografia.createHash('sha256').update(arquivosEmDisco.readFileSync(arquivo)).digest('hex')
    });
    const contexto = {
      module: {
        exports: {}
      },
      require: valor => valor === 'sequelize' ? tiposBanco : new Proxy(() => {}, {
        get: () => () => {}
      }),
      console
    };
    require('node:vm').runInNewContext(arquivosEmDisco.readFileSync(arquivo, 'utf8'), contexto, {
      filename: arquivo
    });
    contexto.module.exports(simulador, tiposBanco);
  }
  todos[esquema] = tabelas;
}
(async () => {
  await extrair('autentica', ['model/sequelize/migrations'], 'model/src/tabelas');
  await extrair('autentica_identidade', ['model/sequelize_AutenticaAI/migrations'], 'autenticaai/src/Tabelas');
  const carga = {};
  for (const [arquivo, chave, exportacao] of [['Perfis.js', 'perfis', 'Perfis'], ['Permissoes.js', 'permissoes', 'Permissoes'], ['PermissaoPerfil.js', 'vinculos', 'PermissaoPerfil']]) {
    const campoAtual = caminhos.join(origem, 'model/src/AutenticaAI/CargaInicial', arquivo);
    carga[chave] = require(campoAtual)[exportacao];
    fontes.push({
      arquivo: caminhos.relative(origem, campoAtual),
      sha256: criptografia.createHash('sha256').update(arquivosEmDisco.readFileSync(campoAtual)).digest('hex')
    });
  }
  for (const relativo of ['model/src/database/connector.js', 'autenticaai/src/BancoDeDados/Conector.js', 'autenticaai/src/Estrategias/Local/autenticacao-local.js']) {
    fontes.push({
      arquivo: relativo,
      sha256: criptografia.createHash('sha256').update(arquivosEmDisco.readFileSync(caminhos.join(origem, relativo))).digest('hex')
    });
  }
  const sql = ['-- Estrutura de referência para conferir as tabelas daFIRMA.', '-- Não altera nem apaga as tabelas anteriores da FIRMA.'];
  const referencias = [],
    indices = [],
    metadados = {
      fontes,
      tabelas: []
    };
  for (const [esquema, tabelas] of Object.entries(todos)) {
    sql.push(`CREATE SCHEMA ${gerador.quoteIdentifier(esquema)};`);
    for (const [nome, tabela] of Object.entries(tabelas)) {
      const atributos = {},
        colunas = [];
      for (const [coluna, original] of Object.entries(tabela.colunas)) {
        const registro = instancia.normalizeAttribute({
          ...original
        });
        if (registro.references) {
          referencias.push({
            schema: esquema,
            tabela: nome,
            coluna,
            alvo: typeof registro.references.model === 'string' ? registro.references.model : registro.references.model.tableName,
            chave: registro.references.key || 'id',
            onDelete: registro.onDelete || 'NO ACTION',
            onUpdate: registro.onUpdate || 'NO ACTION'
          });
          delete registro.references;
          delete registro.onDelete;
          delete registro.onUpdate;
        }
        atributos[coluna] = registro;
        const tipo = typeof registro.type === 'string' ? registro.type : registro.type.toSql();
        colunas.push({
          nome: coluna,
          tipo,
          obrigatorio: registro.allowNull === false,
          primaria: !!registro.primaryKey,
          automatica: !!registro.autoIncrement
        });
      }
      sql.push(gerador.createTableQuery({
        tableName: nome,
        schema: esquema
      }, gerador.attributesToSQL(atributos, {
        table: nome,
        context: 'createTable'
      }), {}));
      for (const idx of tabela.indices) {
        const {
          fields: campos,
          ...opcoes
        } = idx;
        indices.push(gerador.addIndexQuery({
          tableName: nome,
          schema: esquema
        }, campos, opcoes) + ';');
      }
      metadados.tabelas.push({
        schema: esquema,
        nome,
        colunas,
        indices: tabela.indices
      });
    }
  }
  for (const registroAtual of referencias) {
    const table = `${gerador.quoteIdentifier(registroAtual.schema)}.${gerador.quoteIdentifier(registroAtual.tabela)}`;
    sql.push(`ALTER TABLE ${table} ADD FOREIGN KEY (${gerador.quoteIdentifier(registroAtual.coluna)}) REFERENCES ${gerador.quoteIdentifier(registroAtual.schema)}.${gerador.quoteIdentifier(registroAtual.alvo)} (${gerador.quoteIdentifier(registroAtual.chave)}) ON DELETE ${registroAtual.onDelete} ON UPDATE ${registroAtual.onUpdate};`);
  }
  sql.push(...indices);
  metadados.relacionamentos = referencias;
  arquivosEmDisco.mkdirSync(caminhos.join(saida, 'autentica'), {
    recursive: true
  });
  arquivosEmDisco.writeFileSync(caminhos.join(saida, 'autentica/carga-inicial.json'), JSON.stringify(carga, null, 2) + '\n');
  arquivosEmDisco.mkdirSync(caminhos.join(saida, 'db/migration'), {
    recursive: true
  });
  arquivosEmDisco.writeFileSync(caminhos.join(saida, 'db/migration/V25__estrutura_autentica.sql'), sql.join('\n\n') + '\n');
  arquivosEmDisco.writeFileSync(caminhos.join(saida, 'autentica/esquema.json'), JSON.stringify(metadados, null, 2) + '\n');
  console.log(JSON.stringify({
    tabelas: metadados.tabelas.length,
    colunas: metadados.tabelas.reduce((valor, item) => valor + item.colunas.length, 0),
    relacionamentos: referencias.length,
    indices: indices.length,
    fontes: fontes.length
  }));
  await instancia.close();
})().catch(evento => {
  console.error(evento);
  process.exit(1);
});
