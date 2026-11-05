-- Identidade física e contratos operacionais daFIRMA.
ALTER SCHEMA autentica RENAME TO firma;
ALTER SCHEMA autentica_identidade RENAME TO firma_identidade;
ALTER TABLE firma."Atendente" RENAME COLUMN "AtendenteAutenticaAIUsuarioId" TO "AtendenteUsuarioId";
ALTER TABLE firma."Mensagem" RENAME COLUMN "MensagemEnviadaForaZapio" TO "MensagemEnviadaExternamente";
DO $firma$
DECLARE r record;
BEGIN
 FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'firma_autentica_%' LOOP
  EXECUTE format('ALTER TABLE public.%I RENAME TO %I',r.tablename,replace(r.tablename,'firma_autentica_','firma_relacionamento_'));
 END LOOP;
 FOR r IN SELECT p.oid::regprocedure AS assinatura,p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname LIKE 'firma_autentica_%' LOOP
  EXECUTE format('ALTER FUNCTION %s RENAME TO %I',r.assinatura,replace(r.proname,'firma_autentica_','firma_relacionamento_'));
 END LOOP;
END $firma$;

CREATE OR REPLACE FUNCTION firma_relacionamento_id(tabela TEXT, identificador TEXT) RETURNS INTEGER LANGUAGE SQL STABLE AS $$
 SELECT destino_id FROM firma_relacionamento_vinculo WHERE origem=tabela AND origem_id=identificador
$$;

CREATE OR REPLACE FUNCTION firma_relacionamento_sincronizar() RETURNS TRIGGER LANGUAGE plpgsql AS $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN IF current_setting('firma.relacionamento_direcao',true)='canonico' THEN RETURN NEW; END IF; SELECT to_jsonb(NEW) AS dados INTO r;
IF TG_TABLE_NAME='empresa' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Cliente" ("ClienteNome","ClienteStatus","ClienteRazaoSocial","ClienteCpfCnpj","ClienteTelefone","ClienteEmail","createdAt","updatedAt") VALUES (left(r.dados->>'nome',255),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'razao_social',100),left(r.dados->>'documento',20),left(r.dados->>'telefone',50),left(r.dados->>'email',50),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := novo;
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Cliente',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Cliente" SET "ClienteNome"=left(r.dados->>'nome',255),"ClienteStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteRazaoSocial"=left(r.dados->>'razao_social',100),"ClienteCpfCnpj"=left(r.dados->>'documento',20),"ClienteTelefone"=left(r.dados->>'telefone',50),"ClienteEmail"=left(r.dados->>'email',50),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_plano' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Plano" ("PlanoNome","PlanoStatus","PlanoValor","PlanoRenovacaoDias","PlanoQtdDisparos","PlanoQtdCanais","PlanoQtdAtendentes","PlanoPermiteIntegracao","PlanoPermiteWebhook","createdAt","updatedAt") VALUES (left(r.dados->>'nome',128),NULLIF(r.dados->>'ativo','')::BOOLEAN,(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2),NULLIF(r.dados->>'renovacao_dias','')::INTEGER,NULLIF(r.dados->>'limite_envios_dia','')::INTEGER,NULLIF(r.dados->>'limite_canais','')::INTEGER,NULLIF(r.dados->>'limite_atendentes','')::INTEGER,NULLIF(r.dados->>'permite_integracao','')::BOOLEAN,NULLIF(r.dados->>'permite_webhook','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Plano',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Plano" SET "PlanoNome"=left(r.dados->>'nome',128),"PlanoStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"PlanoValor"=(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2),"PlanoRenovacaoDias"=NULLIF(r.dados->>'renovacao_dias','')::INTEGER,"PlanoQtdDisparos"=NULLIF(r.dados->>'limite_envios_dia','')::INTEGER,"PlanoQtdCanais"=NULLIF(r.dados->>'limite_canais','')::INTEGER,"PlanoQtdAtendentes"=NULLIF(r.dados->>'limite_atendentes','')::INTEGER,"PlanoPermiteIntegracao"=NULLIF(r.dados->>'permite_integracao','')::BOOLEAN,"PlanoPermiteWebhook"=NULLIF(r.dados->>'permite_webhook','')::BOOLEAN,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_canal' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Canal" ("CanalNome","CanalGUID","CanalNumeroCelular","CanalStatus","CanalStatusUso","CanalProvedorAPI","CanalMsgEncerramentoAtendimento","CanalMetaNumeroIdentificador","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'id','')::UUID,left(r.dados->>'telefone',40),left(r.dados->>'conexao',40),NULLIF(r.dados->>'ativo','')::BOOLEAN,CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END,r.dados->>'mensagem_encerramento',left(r.dados->>'identificador_meta',100),firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Canal',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Canal" SET "CanalNome"=left(r.dados->>'nome',60),"CanalGUID"=NULLIF(r.dados->>'id','')::UUID,"CanalNumeroCelular"=left(r.dados->>'telefone',40),"CanalStatus"=left(r.dados->>'conexao',40),"CanalStatusUso"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"CanalProvedorAPI"=CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END,"CanalMsgEncerramentoAtendimento"=r.dados->>'mensagem_encerramento',"CanalMetaNumeroIdentificador"=left(r.dados->>'identificador_meta',100),"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_setor' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Setor" ("SetorNome","SetorStatus","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Setor',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Setor" SET "SetorNome"=left(r.dados->>'nome',70),"SetorStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendente' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Atendente" ("AtendenteNome","AtendenteStatus","AtendenteStatusChat","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,left(r.dados->>'disponibilidade',20),firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Atendente',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Atendente" SET "AtendenteNome"=left(r.dados->>'nome',70),"AtendenteStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"AtendenteStatusChat"=left(r.dados->>'disponibilidade',20),"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_contato' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Contato" ("ContatoNome","ContatoNumero","ContatoEmail","ContatoInformacaoAdicional","ContatoAvatarChave","ContatoJidWhatsApp","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),left(r.dados->>'telefone',40),left(r.dados->>'email',100),r.dados->'informacoes',r.dados->>'avatar_chave',r.dados->>'identificador_whatsapp',firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Contato',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Contato" SET "ContatoNome"=left(r.dados->>'nome',70),"ContatoNumero"=left(r.dados->>'telefone',40),"ContatoEmail"=left(r.dados->>'email',100),"ContatoInformacaoAdicional"=r.dados->'informacoes',"ContatoAvatarChave"=r.dados->>'avatar_chave',"ContatoJidWhatsApp"=r.dados->>'identificador_whatsapp',"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendimento' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Atendimento" ("AtendimentoProtocolo","AtendimentoStatus","AtendimentoAvaliacao","AtendimentoQtdMensagensNaoLidas","AtendimentoBOTExterno","AtendimentoDataFinalizado","AtendimentoReceptivo","AtendimentoDuracao","AtendimentoTempoPendente","AtendimentoContextoIA","AtendimentoDataUltimaMensagemCliente","ClienteId","CanalId","ContatoId","SetorId","AtendenteId","createdAt","updatedAt") VALUES (left(r.dados->>'protocolo',16),CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END,NULLIF(r.dados->>'avaliacao','')::INTEGER,NULLIF(r.dados->>'mensagens_nao_lidas','')::INTEGER,NULLIF(r.dados->>'bot_externo','')::BOOLEAN,NULLIF(r.dados->>'finalizado_em','')::TIMESTAMP WITH TIME ZONE,NULLIF(r.dados->>'receptivo','')::BOOLEAN,NULLIF(r.dados->>'duracao_segundos','')::INTEGER,NULLIF(r.dados->>'espera_segundos','')::INTEGER,r.dados->>'contexto_ia',NULLIF(r.dados->>'ultima_mensagem_cliente_em','')::TIMESTAMP WITH TIME ZONE,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),firma_relacionamento_id('chat_contato',r.dados->>'contato_id'),firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),firma_relacionamento_id('chat_atendente',r.dados->>'atendente_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Atendimento',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Atendimento" SET "AtendimentoProtocolo"=left(r.dados->>'protocolo',16),"AtendimentoStatus"=CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END,"AtendimentoAvaliacao"=NULLIF(r.dados->>'avaliacao','')::INTEGER,"AtendimentoQtdMensagensNaoLidas"=NULLIF(r.dados->>'mensagens_nao_lidas','')::INTEGER,"AtendimentoBOTExterno"=NULLIF(r.dados->>'bot_externo','')::BOOLEAN,"AtendimentoDataFinalizado"=NULLIF(r.dados->>'finalizado_em','')::TIMESTAMP WITH TIME ZONE,"AtendimentoReceptivo"=NULLIF(r.dados->>'receptivo','')::BOOLEAN,"AtendimentoDuracao"=NULLIF(r.dados->>'duracao_segundos','')::INTEGER,"AtendimentoTempoPendente"=NULLIF(r.dados->>'espera_segundos','')::INTEGER,"AtendimentoContextoIA"=r.dados->>'contexto_ia',"AtendimentoDataUltimaMensagemCliente"=NULLIF(r.dados->>'ultima_mensagem_cliente_em','')::TIMESTAMP WITH TIME ZONE,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"ContatoId"=firma_relacionamento_id('chat_contato',r.dados->>'contato_id'),"SetorId"=firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),"AtendenteId"=firma_relacionamento_id('chat_atendente',r.dados->>'atendente_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_arquivo' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Arquivo" ("ArquivoNome","ArquivoMimeType","ArquivoSize","ArquivoChave","ArquivoExtensao","ArquivoThumbnailChave","ArquivoLatitude","ArquivoLongitude","createdAt","updatedAt") VALUES (r.dados->>'nome',left(r.dados->>'tipo_mime',500),NULLIF(r.dados->>'tamanho','')::INTEGER,r.dados->>'chave_armazenamento',left(r.dados->>'extensao',10),r.dados->>'miniatura_chave',left(r.dados->>'latitude',100),left(r.dados->>'longitude',100),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Arquivo',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Arquivo" SET "ArquivoNome"=r.dados->>'nome',"ArquivoMimeType"=left(r.dados->>'tipo_mime',500),"ArquivoSize"=NULLIF(r.dados->>'tamanho','')::INTEGER,"ArquivoChave"=r.dados->>'chave_armazenamento',"ArquivoExtensao"=left(r.dados->>'extensao',10),"ArquivoThumbnailChave"=r.dados->>'miniatura_chave',"ArquivoLatitude"=left(r.dados->>'latitude',100),"ArquivoLongitude"=left(r.dados->>'longitude',100),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_mensagem' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Mensagem" ("MensagemTexto","MensagemTipo","MensagemIdWhatsApp","MensagemACK","MensagemEditada","MensagemDeletada","MensagemReacao","MensagemInterativa","AtendimentoId","CanalId","ArquivoId","MensagemDataHoraWhatsApp","MensagemFromMe","MensagemEnviadaPeloAtendente","MensagemEnviadaPeloBot","MensagemRAW","createdAt","updatedAt") VALUES (r.dados->>'texto',left(r.dados->>'tipo',60),left(r.dados->>'identificador_externo',100),NULLIF(r.dados->>'confirmacao','')::INTEGER,NULLIF(r.dados->>'editada','')::BOOLEAN,NULLIF(r.dados->>'excluida','')::BOOLEAN,r.dados->'reacoes',r.dados->'conteudo_interativo',firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),firma_relacionamento_id('chat_arquivo',r.dados->>'arquivo_id'),COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz),(r.dados->>'sentido'='SAIDA'),(r.dados->>'origem'='ATENDENTE'),(r.dados->>'origem'='BOT'),(r.dados->'dados_externos')::text,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Mensagem',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Mensagem" SET "MensagemTexto"=r.dados->>'texto',"MensagemTipo"=left(r.dados->>'tipo',60),"MensagemIdWhatsApp"=left(r.dados->>'identificador_externo',100),"MensagemACK"=NULLIF(r.dados->>'confirmacao','')::INTEGER,"MensagemEditada"=NULLIF(r.dados->>'editada','')::BOOLEAN,"MensagemDeletada"=NULLIF(r.dados->>'excluida','')::BOOLEAN,"MensagemReacao"=r.dados->'reacoes',"MensagemInterativa"=r.dados->'conteudo_interativo',"AtendimentoId"=firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"ArquivoId"=firma_relacionamento_id('chat_arquivo',r.dados->>'arquivo_id'),"MensagemDataHoraWhatsApp"=COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz),"MensagemFromMe"=(r.dados->>'sentido'='SAIDA'),"MensagemEnviadaPeloAtendente"=(r.dados->>'origem'='ATENDENTE'),"MensagemEnviadaPeloBot"=(r.dados->>'origem'='BOT'),"MensagemRAW"=(r.dados->'dados_externos')::text,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendente_setor' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."AtendenteSetor" ("AtendenteId","SetorId","createdAt","updatedAt") VALUES (firma_relacionamento_id('chat_atendente',r.dados->>'atendente_id'),firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'AtendenteSetor',novo,cliente,r.dados);
  ELSE
   UPDATE firma."AtendenteSetor" SET "AtendenteId"=firma_relacionamento_id('chat_atendente',r.dados->>'atendente_id'),"SetorId"=firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_acao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."AtendimentoAcao" ("AtendimentoAcaoTipo","AtendimentoAcaoTexto","AtendimentoId","createdAt","updatedAt") VALUES (left(r.dados->>'tipo',40),left(r.dados->>'descricao',200),firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'AtendimentoAcao',novo,cliente,r.dados);
  ELSE
   UPDATE firma."AtendimentoAcao" SET "AtendimentoAcaoTipo"=left(r.dados->>'tipo',40),"AtendimentoAcaoTexto"=left(r.dados->>'descricao',200),"AtendimentoId"=firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_etiqueta' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Tag" ("TagNome","TagStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Tag',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Tag" SET "TagNome"=left(r.dados->>'nome',60),"TagStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendimento_etiqueta' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."AtendimentoTag" ("AtendimentoId","TagId","createdAt","updatedAt") VALUES (firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),firma_relacionamento_id('chat_etiqueta',r.dados->>'etiqueta_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'AtendimentoTag',novo,cliente,r.dados);
  ELSE
   UPDATE firma."AtendimentoTag" SET "AtendimentoId"=firma_relacionamento_id('chat_atendimento',r.dados->>'atendimento_id'),"TagId"=firma_relacionamento_id('chat_etiqueta',r.dados->>'etiqueta_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_mensagem_rapida' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."MensagemRapida" ("MensagemRapidaTexto","ClienteId","SetorId","createdAt","updatedAt") VALUES (r.dados->>'texto',firma_relacionamento_id('empresa',r.dados->>'empresa_id'),firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'MensagemRapida',novo,cliente,r.dados);
  ELSE
   UPDATE firma."MensagemRapida" SET "MensagemRapidaTexto"=r.dados->>'texto',"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"SetorId"=firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_modelo_mensagem' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."MensagemTemplate" ("MensagemTemplateNome","MensagemTemplateCategoria","MensagemTemplateCorpo","MensagemTemplateStatusMeta","MensagemTemplateStatusUso","MensagemTemplateCabecalhoConteudo","MensagemTemplateRodape","MensagemTemplateBotoes","MensagemTemplateMetaId","MensagemTemplateCabecalhoTipo","MensagemTemplateCabecalhoVariaveis","MensagemTemplateCorpoVariaveis","MensagemTemplateMotivoRejeicaoMeta","MensagemTemplateMetodoEnvio","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END,left(r.dados->>'corpo',1024),left(r.dados->>'estado',60),true,r.dados->'cabecalho',left(r.dados->>'rodape',60),r.dados->'botoes',left(r.dados->>'identificador_meta',50),left(r.dados->>'cabecalho_tipo',40),r.dados->'cabecalho_variaveis',r.dados->'corpo_variaveis',r.dados->>'motivo_rejeicao',left(r.dados->>'metodo_envio',40),firma_relacionamento_id('empresa',r.dados->>'empresa_id'),firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'MensagemTemplate',novo,cliente,r.dados);
  ELSE
   UPDATE firma."MensagemTemplate" SET "MensagemTemplateNome"=left(r.dados->>'nome',100),"MensagemTemplateCategoria"=CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END,"MensagemTemplateCorpo"=left(r.dados->>'corpo',1024),"MensagemTemplateStatusMeta"=left(r.dados->>'estado',60),"MensagemTemplateStatusUso"=true,"MensagemTemplateCabecalhoConteudo"=r.dados->'cabecalho',"MensagemTemplateRodape"=left(r.dados->>'rodape',60),"MensagemTemplateBotoes"=r.dados->'botoes',"MensagemTemplateMetaId"=left(r.dados->>'identificador_meta',50),"MensagemTemplateCabecalhoTipo"=left(r.dados->>'cabecalho_tipo',40),"MensagemTemplateCabecalhoVariaveis"=r.dados->'cabecalho_variaveis',"MensagemTemplateCorpoVariaveis"=r.dados->'corpo_variaveis',"MensagemTemplateMotivoRejeicaoMeta"=r.dados->>'motivo_rejeicao',"MensagemTemplateMetodoEnvio"=left(r.dados->>'metodo_envio',40),"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"SetorId"=firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_lista_contatos' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."ListaContato" ("ListaContatoNome","ListaContatoStatus","ListaContatoFonteContatos","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'fonte',50),firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'ListaContato',novo,cliente,r.dados);
  ELSE
   UPDATE firma."ListaContato" SET "ListaContatoNome"=left(r.dados->>'nome',100),"ListaContatoStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ListaContatoFonteContatos"=left(r.dados->>'fonte',50),"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_integracao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Integracao" ("IntegracaoNome","IntegracaoDados","IntegracaoStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),r.dados->'configuracao',NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Integracao',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Integracao" SET "IntegracaoNome"=left(r.dados->>'nome',100),"IntegracaoDados"=r.dados->'configuracao',"IntegracaoStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_agente_ia' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."InteligenciaArtificial" ("InteligenciaArtificialNome","InteligenciaArtificialStatus","InteligenciaArtificialProvedor","InteligenciaArtificialModelo","InteligenciaArtificialTextoPrompt","InteligenciaArtificialAtivador","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'provedor',50),left(r.dados->>'modelo',50),r.dados->>'instrucoes',left(r.dados->>'ativador',50),firma_relacionamento_id('empresa',r.dados->>'empresa_id'),firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'InteligenciaArtificial',novo,cliente,r.dados);
  ELSE
   UPDATE firma."InteligenciaArtificial" SET "InteligenciaArtificialNome"=left(r.dados->>'nome',100),"InteligenciaArtificialStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"InteligenciaArtificialProvedor"=left(r.dados->>'provedor',50),"InteligenciaArtificialModelo"=left(r.dados->>'modelo',50),"InteligenciaArtificialTextoPrompt"=r.dados->>'instrucoes',"InteligenciaArtificialAtivador"=left(r.dados->>'ativador',50),"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"SetorId"=firma_relacionamento_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_bot_definicao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."ChatbotDefinicao" ("ChatbotDefinicaoNome","ChatbotDefinicaoStatus","ChatbotDefinicaoFuncionamento","ChatbotDefinicaoFlow","ChatbotDefinicaoProvedor","ChatbotDefinicaoN8nURL","CanalId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativo','')::BOOLEAN,NULLIF(r.dados->>'funcionamento','')::JSON,NULLIF(r.dados->>'fluxo','')::JSON,left(r.dados->>'provedor',50),r.dados->>'endereco_externo',firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'ChatbotDefinicao',novo,cliente,r.dados);
  ELSE
   UPDATE firma."ChatbotDefinicao" SET "ChatbotDefinicaoNome"=left(r.dados->>'nome',100),"ChatbotDefinicaoStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"ChatbotDefinicaoFuncionamento"=NULLIF(r.dados->>'funcionamento','')::JSON,"ChatbotDefinicaoFlow"=NULLIF(r.dados->>'fluxo','')::JSON,"ChatbotDefinicaoProvedor"=left(r.dados->>'provedor',50),"ChatbotDefinicaoN8nURL"=r.dados->>'endereco_externo',"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_webhook' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."Webhook" ("WebhookURL","WebhookTodosCanais","WebhookEvento","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'destino',2000),NULLIF(r.dados->>'todos_canais','')::BOOLEAN,(r.dados->'eventos')::text,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'Webhook',novo,cliente,r.dados);
  ELSE
   UPDATE firma."Webhook" SET "WebhookURL"=left(r.dados->>'destino',2000),"WebhookTodosCanais"=NULLIF(r.dados->>'todos_canais','')::BOOLEAN,"WebhookEvento"=(r.dados->'eventos')::text,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_webhook_entrega' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."WebhookLog" ("WebhookLogEvento","WebhookLogDados","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'evento',100),r.dados->>'dados',firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'WebhookLog',novo,cliente,r.dados);
  ELSE
   UPDATE firma."WebhookLog" SET "WebhookLogEvento"=left(r.dados->>'evento',100),"WebhookLogDados"=r.dados->>'dados',"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_chave_integracao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."ChaveAPI" ("ChaveAPIDescricao","ChaveAPIStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'ChaveAPI',novo,cliente,r.dados);
  ELSE
   UPDATE firma."ChaveAPI" SET "ChaveAPIDescricao"=left(r.dados->>'nome',100),"ChaveAPIStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_campanha' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_relacionamento_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO firma."DisparoMassa" ("DisparoMassaDataHoraInicio","DisparoMassaStatus","DisparoMassaIntervaloEntreDisparos","DisparoMassaConteudo","DisparoMassaMaxMensagemDia","ClienteId","CanalId","MensagemTemplateId","ListaContatoId","createdAt","updatedAt") VALUES (NULLIF(r.dados->>'agendada_em','')::TIMESTAMP WITH TIME ZONE,left(r.dados->>'estado',50),NULLIF(r.dados->>'intervalo_segundos','')::INTEGER,r.dados->>'texto',NULLIF(r.dados->>'maximo_diario','')::INTEGER,firma_relacionamento_id('empresa',r.dados->>'empresa_id'),firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),firma_relacionamento_id('chat_modelo_mensagem',r.dados->>'modelo_id'),firma_relacionamento_id('chat_lista_contatos',r.dados->>'lista_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_relacionamento_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_relacionamento_vinculo VALUES(TG_TABLE_NAME,chave,'DisparoMassa',novo,cliente,r.dados);
  ELSE
   UPDATE firma."DisparoMassa" SET "DisparoMassaDataHoraInicio"=NULLIF(r.dados->>'agendada_em','')::TIMESTAMP WITH TIME ZONE,"DisparoMassaStatus"=left(r.dados->>'estado',50),"DisparoMassaIntervaloEntreDisparos"=NULLIF(r.dados->>'intervalo_segundos','')::INTEGER,"DisparoMassaConteudo"=r.dados->>'texto',"DisparoMassaMaxMensagemDia"=NULLIF(r.dados->>'maximo_diario','')::INTEGER,"ClienteId"=firma_relacionamento_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_relacionamento_id('chat_canal',r.dados->>'canal_id'),"MensagemTemplateId"=firma_relacionamento_id('chat_modelo_mensagem',r.dados->>'modelo_id'),"ListaContatoId"=firma_relacionamento_id('chat_lista_contatos',r.dados->>'lista_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_relacionamento_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
RETURN NEW; END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_metricas() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE assumido TIMESTAMPTZ; respostas RECORD;
BEGIN
 IF NEW."AtendimentoStatus"='finalizado' THEN
  NEW."AtendimentoDataFinalizado"=COALESCE(NEW."AtendimentoDataFinalizado",now());
  NEW."AtendimentoContextoIA"=NULL;
  NEW."AtendimentoDuracao"=GREATEST(0,floor(extract(epoch FROM NEW."AtendimentoDataFinalizado"-NEW."createdAt")))::integer;
  SELECT min("createdAt") INTO assumido FROM firma."AtendimentoAcao" WHERE "AtendimentoId"=NEW.id AND "AtendimentoAcaoTipo" IN ('Assumir','assumir');
  NEW."AtendimentoTempoPendente"=CASE WHEN NEW."AtendimentoBOTExterno" THEN 0 WHEN assumido IS NOT NULL THEN GREATEST(0,floor(extract(epoch FROM assumido-NEW."createdAt")))::integer WHEN NOT EXISTS(SELECT 1 FROM firma."AtendimentoAcao" WHERE "AtendimentoId"=NEW.id) THEN NEW."AtendimentoDuracao" ELSE 0 END;
  SELECT NOT COALESCE("MensagemFromMe",false) INTO NEW."AtendimentoReceptivo" FROM firma."Mensagem" WHERE "AtendimentoId"=NEW.id ORDER BY id LIMIT 1;
  SELECT COALESCE(sum(dt) FILTER(WHERE NOT enviada AND proxima AND atendente),0) soma_atendente,
    count(*) FILTER(WHERE NOT enviada AND proxima AND atendente) qtd_atendente,
    COALESCE(sum(dt) FILTER(WHERE enviada AND NOT proxima AND atual_atendente),0) soma_cliente,
    count(*) FILTER(WHERE NOT enviada)+count(*) FILTER(WHERE enviada AND NOT proxima AND atual_atendente) qtd_cliente
  INTO respostas FROM (
    SELECT COALESCE("MensagemFromMe",false) enviada,"MensagemEnviadaPeloAtendente" atual_atendente,
      lead("MensagemFromMe") OVER w proxima,lead("MensagemEnviadaPeloAtendente") OVER w atendente,
      GREATEST(0,extract(epoch FROM lead("createdAt") OVER w-"createdAt")) dt
    FROM firma."Mensagem" WHERE "AtendimentoId"=NEW.id WINDOW w AS(ORDER BY id)
  ) mensagens;
  NEW."AtendimentoTempoRespostaAtendente"=floor(respostas.soma_atendente/GREATEST(respostas.qtd_atendente,1))::integer;
  NEW."AtendimentoTempoRespostaCliente"=floor(respostas.soma_cliente/GREATEST(respostas.qtd_cliente,1))::integer;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_evento() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE canal INTEGER; cliente INTEGER; evento TEXT; dados JSONB;
BEGIN
 IF TG_TABLE_NAME='Mensagem' THEN
  IF COALESCE(NEW."MensagemFromMe",false) THEN RETURN NEW; END IF;
  canal=NEW."CanalId";evento='mensagem_recebida';dados=to_jsonb(NEW)-'MensagemRAW'-'MensagemKeyWhatsApp';
 ELSIF TG_TABLE_NAME='Canal' THEN
  IF NEW."CanalStatus" IS NOT DISTINCT FROM OLD."CanalStatus" THEN RETURN NEW; END IF;
  canal=NEW.id;evento='status_canal';dados=jsonb_build_object('CanalId',NEW.id,'CanalGUID',NEW."CanalGUID",'CanalNome',NEW."CanalNome",'CanalStatus',NEW."CanalStatus");
 ELSE
  IF NOT COALESCE(OLD."AtendimentoBOTExterno",false) OR COALESCE(NEW."AtendimentoBOTExterno",false) THEN RETURN NEW; END IF;
  canal=NEW."CanalId";evento='chatbot_externo_assumido';dados=jsonb_build_object('AtendimentoId',NEW.id,'ContatoId',NEW."ContatoId",'CanalId',canal);
 END IF;
 SELECT "ClienteId" INTO cliente FROM firma."Canal" WHERE id=canal;
 -- Registros antigos já têm entrega pelo executor anterior; os novos usam este contrato.
 INSERT INTO firma_relacionamento_webhook_entrega(webhook_id,cliente_id,evento,dados)
 SELECT w.id,cliente,evento,jsonb_build_object('Evento',evento,'Dados',dados || jsonb_build_object('ClienteId',cliente,'CanalId',canal)) FROM firma."Webhook" w
 WHERE w."ClienteId"=cliente AND w."WebhookEvento"=evento AND (w."WebhookTodosCanais" OR w."CanalId"=canal)
 AND NOT EXISTS(SELECT 1 FROM firma_relacionamento_vinculo v WHERE v.destino='Webhook' AND v.destino_id=w.id);
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_vinculo_atendente() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE atendente BIGINT; setor BIGINT; empresa BIGINT; direcao TEXT;
BEGIN
 direcao=current_setting('firma.relacionamento_direcao',true);
 PERFORM set_config('firma.relacionamento_direcao','canonico',true);
 IF TG_OP IN ('DELETE','UPDATE') THEN
  SELECT origem_id::bigint INTO atendente FROM firma_relacionamento_vinculo WHERE destino='Atendente' AND destino_id=OLD."AtendenteId";
  SELECT origem_id::bigint INTO setor FROM firma_relacionamento_vinculo WHERE destino='Setor' AND destino_id=OLD."SetorId";
  DELETE FROM chat_atendente_setor WHERE atendente_id=atendente AND setor_id=setor;
 END IF;
 IF TG_OP IN ('INSERT','UPDATE') THEN
  SELECT origem_id::bigint INTO atendente FROM firma_relacionamento_vinculo WHERE destino='Atendente' AND destino_id=NEW."AtendenteId";
  SELECT origem_id::bigint INTO setor FROM firma_relacionamento_vinculo WHERE destino='Setor' AND destino_id=NEW."SetorId";
  SELECT empresa_id INTO empresa FROM chat_atendente WHERE id=atendente;
  IF atendente IS NOT NULL AND setor IS NOT NULL THEN INSERT INTO chat_atendente_setor(atendente_id,setor_id,empresa_id) VALUES(atendente,setor,empresa) ON CONFLICT DO NOTHING; END IF;
 END IF;
 PERFORM set_config('firma.relacionamento_direcao',COALESCE(direcao,''),true);
 RETURN COALESCE(NEW,OLD);
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_bot_encerrado() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.estado<>'ATIVA' THEN
  DELETE FROM firma."ChatbotMetadata" WHERE id=(SELECT destino_id FROM firma_relacionamento_vinculo WHERE origem='chat_bot_sessao' AND origem_id=NEW.id::text);
  DELETE FROM firma_relacionamento_vinculo WHERE origem='chat_bot_sessao' AND origem_id=NEW.id::text;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_ia_agendar() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT COALESCE(NEW."MensagemFromMe",false) AND NEW."AtendimentoId" IS NOT NULL AND NEW."MensagemTipo" IN ('Texto','TEXTO') THEN
  INSERT INTO firma_relacionamento_ia_execucao(mensagem_id,agente_id)
  SELECT NEW.id,ia.id FROM firma."InteligenciaArtificial" ia JOIN firma."Atendimento" a ON a.id=NEW."AtendimentoId"
  WHERE ia."ClienteId"=a."ClienteId" AND ia."InteligenciaArtificialStatus" AND ia."InteligenciaArtificialAtivador"='todas-mensagens'
  AND (ia."CanalId" IS NULL OR ia."CanalId"=NEW."CanalId") AND (ia."SetorId" IS NULL OR ia."SetorId"=a."SetorId") ON CONFLICT DO NOTHING;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_ultima_mensagem() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW."AtendimentoId" IS NOT NULL THEN
  UPDATE firma."Atendimento" SET "MensagemId"=NEW.id,"updatedAt"=now(),
   "AtendimentoDataUltimaMensagemCliente"=CASE WHEN NOT COALESCE(NEW."MensagemFromMe",false) THEN COALESCE(NEW."MensagemDataHoraWhatsApp",now()) ELSE "AtendimentoDataUltimaMensagemCliente" END,
   "AtendimentoQtdMensagensNaoLidas"=COALESCE("AtendimentoQtdMensagensNaoLidas",0)+CASE WHEN COALESCE(NEW."MensagemFromMe",false) THEN 0 ELSE 1 END
  WHERE id=NEW."AtendimentoId";
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_campanha_publica(campanha UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE destino INTEGER; resumo RECORD;
BEGIN
 SELECT destino_id INTO destino FROM firma_relacionamento_vinculo WHERE origem='chat_campanha' AND origem_id=campanha::text;
 IF destino IS NULL THEN RETURN; END IF;
 SELECT count(*)::integer total,
  count(*) FILTER(WHERE e.estado IN ('PENDENTE','ENVIANDO'))::integer pendentes,
  count(*) FILTER(WHERE e.estado='ENVIADO')::integer enviados,
  count(*) FILTER(WHERE e.estado='FALHOU')::integer falhas,
  count(*) FILTER(WHERE e.estado='CANCELADO')::integer cancelados,
  COALESCE(jsonb_agg(jsonb_build_object('Destinatario',c.telefone,'Mensagem',COALESCE(cp.texto,m.corpo,''),'Status',CASE e.estado WHEN 'ENVIADO' THEN 'Enviada' WHEN 'FALHOU' THEN 'Falha' WHEN 'CANCELADO' THEN 'Cancelada' ELSE 'Pendente' END,'EstadoEnvio',e.estado,'Erro',e.erro,'IdentificadorExterno',e.identificador_externo,'Variaveis',jsonb_build_object('nome',c.nome,'telefone',c.telefone)) ORDER BY e.id),'[]'::jsonb) mensagens
 INTO resumo FROM chat_campanha_envio e JOIN chat_contato c ON c.id=e.contato_id JOIN chat_campanha cp ON cp.id=e.campanha_id LEFT JOIN chat_modelo_mensagem m ON m.id=cp.modelo_id WHERE e.campanha_id=campanha;
 UPDATE firma."DisparoMassa" SET "DisparoMassaQuantidadeMensagens"=resumo.total,"DisparoMassaQuantidadePendente"=resumo.pendentes,"DisparoMassaQuantidadeEnviado"=resumo.enviados,"DisparoMassaQuantidadeFalha"=resumo.falhas,"DisparoMassaQuantidadeCancelado"=resumo.cancelados,"DisparoMassaMensagens"=resumo.mensagens,"DisparoMassaProcessandoMensagens"=false WHERE id=destino;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_campanhas_alteradas() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE c UUID;
BEGIN
 FOR c IN SELECT DISTINCT campanha_id FROM novos LOOP PERFORM firma_relacionamento_campanha_publica(c); END LOOP;
 RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_campanha_alterada() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN PERFORM firma_relacionamento_campanha_publica(NEW.id); RETURN NEW; END $$;

CREATE OR REPLACE FUNCTION firma_relacionamento_excluir_canal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.tabela='Canal' THEN
  UPDATE chat_canal SET excluido_em=NEW.excluido_em,ativo=false,conexao='DESCONECTADO'
  WHERE id IN (SELECT origem_id::uuid FROM firma_relacionamento_vinculo WHERE origem='chat_canal' AND destino='Canal' AND destino_id=NEW.registro_id AND cliente_id=NEW.cliente_id);
 END IF;
 RETURN NEW;
END $$;

UPDATE chat_canal SET referencia_segredo_meta=replace(referencia_segredo_meta,'AUTENTICA:','FIRMA:') WHERE referencia_segredo_meta LIKE 'AUTENTICA:%';
UPDATE chat_atendente SET identidade=replace(identidade,'autentica:','firma:') WHERE identidade LIKE 'autentica:%';
UPDATE firma."ChatbotDefinicao" SET "ChatbotDefinicaoProvedor"='firma' WHERE "ChatbotDefinicaoProvedor"='zapio';
UPDATE firma."ChatbotMetadata" SET "ChatbotMetadataProvedorBOT"='firma' WHERE "ChatbotMetadataProvedorBOT"='zapio';
