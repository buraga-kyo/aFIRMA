#!/usr/bin/env python3
"""Verifica a migração em um banco descartável. Não consulta a configuração de produção."""
from pathlib import Path
import subprocess,os,json,uuid,zlib
root=Path(__file__).resolve().parents[1]
container=os.environ.get('FIRMA_TEST_POSTGRES_CONTAINER','firma-autentica-test-db')
usuario=os.environ.get('FIRMA_TEST_POSTGRES_USER','firma_autentica')
db='firma_migracao_'+uuid.uuid4().hex[:12]
def sql(text,database=db):
 r=subprocess.run(['docker','exec','-i',container,'psql','-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-U',usuario,'-d',database],input=text,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
sql('CREATE DATABASE '+db+';',os.environ.get('FIRMA_TEST_POSTGRES_DB','firma_autentica'))
try:
 files=sorted((root/'src/main/resources/db/migration').glob('V*.sql'),key=lambda f:int(f.name.split('__')[0][1:]))
 # V28 já foi aplicada: sua evolução deve ocorrer em migrações posteriores.
 v28=next(f for f in files if f.name.startswith('V28__'))
 assert zlib.crc32(''.join(v28.read_text().splitlines()).encode())==355706706,'Checksum histórico da V28 alterado'
 for f in files:
  if int(f.name.split('__')[0][1:])<=24:sql(f.read_text())
 sql('''UPDATE chat_plano SET valor_centavos=12345 WHERE nome='Interno';
 INSERT INTO chat_setor(id,nome,empresa_id) VALUES(101,'Comercial',1);
 INSERT INTO chat_canal(id,nome,telefone,setor_padrao_id,empresa_id) VALUES('11111111-1111-1111-1111-111111111111','Canal migrado','5511999990001',101,1);
 INSERT INTO chat_atendente(id,identidade,nome,empresa_id) VALUES(201,'usuario-migrado','Atendente migrado',1);
 INSERT INTO chat_atendente_setor(atendente_id,setor_id,empresa_id) VALUES(201,101,1);
 INSERT INTO chat_contato(id,telefone,nome,email,empresa_id) VALUES(301,'5511999990002',repeat('Contato ',12),'contato@example.test',1);
 INSERT INTO chat_atendimento(id,canal_id,contato_id,setor_id,atendente_id,estado,empresa_id,protocolo) VALUES('22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111',301,101,201,'EM_ANDAMENTO',1,'TESTE-MIGRACAO');
 INSERT INTO chat_mensagem(id,atendimento_id,canal_id,identificador_externo,sentido,estado,texto,empresa_id) VALUES(401,'22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','msg-migrada','ENTRADA','RECEBIDA','Histórico preservado',1);
 INSERT INTO chat_lista_contatos(id,empresa_id,nome) VALUES(701,1,'Lista migrada');
 INSERT INTO chat_lista_destinatario(lista_id,contato_id,empresa_id) VALUES(701,301,1);
 INSERT INTO chat_campanha(id,empresa_id,nome,canal_id,lista_id,texto,estado) VALUES('44444444-4444-4444-4444-444444444444',1,'Campanha migrada','11111111-1111-1111-1111-111111111111',701,'Aviso de teste','CONCLUIDA');
 INSERT INTO chat_campanha_envio(empresa_id,campanha_id,contato_id,estado,identificador_externo,enviado_em) VALUES(1,'44444444-4444-4444-4444-444444444444',301,'ENVIADO','envio-migrado',now());
 INSERT INTO chat_acao(id,atendimento_id,ator,tipo,descricao,empresa_id) VALUES(501,'22222222-2222-2222-2222-222222222222','atendente','ASSUMIU','Atendimento assumido',1);
 INSERT INTO chat_etiqueta(id,empresa_id,nome) VALUES(601,1,'Importante');
 INSERT INTO chat_atendimento_etiqueta(atendimento_id,etiqueta_id,empresa_id) VALUES('22222222-2222-2222-2222-222222222222',601,1);
 ''')
 for f in files:
  if f.name.startswith('V38__'):
   sql("UPDATE chat_canal SET ativo=false WHERE id='11111111-1111-1111-1111-111111111111'; INSERT INTO firma_autentica_exclusao(cliente_id,tabela,registro_id,usuario_id) SELECT cliente_id,'Canal',destino_id,0 FROM firma_autentica_vinculo WHERE origem='chat_canal' AND origem_id='11111111-1111-1111-1111-111111111111';")
  if int(f.name.split('__')[0][1:])>24:sql(f.read_text())
 assert sql("SELECT count(*) FROM chat_canal WHERE id='11111111-1111-1111-1111-111111111111' AND excluido_em IS NOT NULL")=='1'
 sql("INSERT INTO chat_canal(id,nome,telefone,empresa_id) VALUES('55555555-5555-5555-5555-555555555555','Número reutilizado','5511999990001',1);")
 assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='firma_autentica_envio' AND column_name='requisicao'")=='1'
 assert sql("SELECT count(*) FROM pg_indexes WHERE tablename='firma_autentica_envio' AND indexdef LIKE 'CREATE UNIQUE INDEX% (cliente_id, requisicao)'")=='1'
 esperado=json.loads((root/'src/main/resources/autentica/esquema.json').read_text())
 n=int(sql("SELECT count(*) FROM information_schema.tables WHERE table_schema IN ('autentica','autentica_identidade') AND table_type='BASE TABLE'"));assert n==len(esperado['tabelas']),(n,'tabelas')
 n=int(sql("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname IN ('autentica','autentica_identidade') AND c.contype='f'"));assert n==len(esperado['relacionamentos']),(n,'relacionamentos')
 n=int(sql("SELECT count(*) FROM information_schema.columns WHERE table_schema IN ('autentica','autentica_identidade')"));assert n==sum(len(t['colunas'])for t in esperado['tabelas']),(n,'colunas')
 assert sql('''SELECT count(*) FROM autentica."Atendimento" a JOIN autentica."Contato" c ON c.id=a."ContatoId" JOIN autentica."Canal" ca ON ca.id=a."CanalId" JOIN autentica."Setor" s ON s.id=a."SetorId" JOIN autentica."Atendente" at ON at.id=a."AtendenteId" WHERE a."AtendimentoStatus"='emandamento' AND ca."CanalGUID"='11111111-1111-1111-1111-111111111111' ''')=='1'
 assert sql('''SELECT "MensagemTexto" FROM autentica."Mensagem" m JOIN autentica."Atendimento" a ON a.id=m."AtendimentoId" WHERE a."AtendimentoProtocolo"='TESTE-MIGRACAO' ''')=='Histórico preservado'
 assert sql("SELECT dados_origem->>'nome' FROM firma_autentica_vinculo WHERE origem='chat_contato' AND origem_id='301'")==('Contato '*12).strip()
 assert sql("SELECT count(*) FROM chat_mensagem WHERE id=401")=='1'
 assert sql("""SELECT \"PlanoValor\" FROM autentica.\"Plano\" WHERE \"PlanoNome\"='Interno'""")=='123.45'
 assert sql("""SELECT count(*) FROM autentica.\"Cliente\" c JOIN autentica.\"Plano\" p ON p.id=c.\"PlanoId\" WHERE p.\"PlanoNome\"='Interno'""")=='1'
 assert sql("""SELECT \"ListaContatoQuantidadeContatos\" FROM autentica.\"ListaContato\" WHERE \"ListaContatoNome\"='Lista migrada'""")=='1'

 # A escrita por um callback antigo deve aparecer imediatamente no contrato novo.
 sql('''INSERT INTO chat_mensagem(id,atendimento_id,canal_id,identificador_externo,sentido,estado,texto,empresa_id) VALUES(402,'22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','msg-apos-migracao','ENTRADA','RECEBIDA','Callback sincronizado',1);''')
 assert sql('''SELECT count(*) FROM autentica."Mensagem" WHERE "MensagemTexto"='Callback sincronizado' ''')=='1'
 sql("UPDATE chat_contato SET nome='Contato atualizado' WHERE id=301")
 assert sql('''SELECT "ContatoNome" FROM autentica."Contato" WHERE id=firma_autentica_id('chat_contato','301')''')=='Contato atualizado'
 # Datas não podem virar DATE: horário e fuso fazem parte do histórico.
 assert sql("SELECT data_type FROM information_schema.columns WHERE table_schema='autentica' AND table_name='Mensagem' AND column_name='createdAt'")=='timestamp with time zone'
 assert sql("SELECT \"DisparoMassaQuantidadeEnviado\" FROM autentica.\"DisparoMassa\" WHERE \"DisparoMassaConteudo\"='Aviso de teste'")=='1'
 sql("UPDATE chat_campanha_envio SET estado='FALHOU' WHERE campanha_id='44444444-4444-4444-4444-444444444444';")
 assert sql("SELECT \"DisparoMassaQuantidadeFalha\" FROM autentica.\"DisparoMassa\" WHERE \"DisparoMassaConteudo\"='Aviso de teste'")=='1'
 sql("UPDATE chat_plano SET limite_canais=0,limite_atendentes=0,permite_integracao=false,permite_webhook=false WHERE nome='Interno';")
 sql("INSERT INTO chat_canal(id,nome,telefone,empresa_id) VALUES('33333333-3333-3333-3333-333333333333','Sem plano','5511999990003',1);")
 sql("INSERT INTO chat_atendente(identidade,nome,empresa_id) VALUES('sem-plano','Sem plano',1);")
 assert sql("SELECT count(*) FROM pg_trigger WHERE tgname IN ('chat_canal_limite','chat_atendente_limite')")=='0'
 print('OK: 33 tabelas, 383 colunas, 57 FKs; vínculos, IDs, histórico, dados originais e callbacks preservados.')
finally:sql('DROP DATABASE '+db+';',os.environ.get('FIRMA_TEST_POSTGRES_DB','firma_autentica'))
