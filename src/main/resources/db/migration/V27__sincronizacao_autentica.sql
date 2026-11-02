CREATE FUNCTION firma_autentica_sincronizar() RETURNS TRIGGER LANGUAGE plpgsql AS $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN IF current_setting('firma.autentica_direcao',true)='canonico' THEN RETURN NEW; END IF; SELECT to_jsonb(NEW) AS dados INTO r;
IF TG_TABLE_NAME='empresa' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Cliente" ("ClienteNome","ClienteStatus","ClienteRazaoSocial","ClienteCpfCnpj","ClienteTelefone","ClienteEmail","createdAt","updatedAt") VALUES (left(r.dados->>'nome',255),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'razao_social',100),left(r.dados->>'documento',20),left(r.dados->>'telefone',50),left(r.dados->>'email',50),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := novo;
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Cliente',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Cliente" SET "ClienteNome"=left(r.dados->>'nome',255),"ClienteStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteRazaoSocial"=left(r.dados->>'razao_social',100),"ClienteCpfCnpj"=left(r.dados->>'documento',20),"ClienteTelefone"=left(r.dados->>'telefone',50),"ClienteEmail"=left(r.dados->>'email',50),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_plano' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Plano" ("PlanoNome","PlanoStatus","PlanoValor","PlanoRenovacaoDias","PlanoQtdDisparos","PlanoQtdCanais","PlanoQtdAtendentes","PlanoPermiteIntegracao","PlanoPermiteWebhook","createdAt","updatedAt") VALUES (left(r.dados->>'nome',128),NULLIF(r.dados->>'ativo','')::BOOLEAN,(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2),NULLIF(r.dados->>'renovacao_dias','')::INTEGER,NULLIF(r.dados->>'limite_envios_dia','')::INTEGER,NULLIF(r.dados->>'limite_canais','')::INTEGER,NULLIF(r.dados->>'limite_atendentes','')::INTEGER,NULLIF(r.dados->>'permite_integracao','')::BOOLEAN,NULLIF(r.dados->>'permite_webhook','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Plano',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Plano" SET "PlanoNome"=left(r.dados->>'nome',128),"PlanoStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"PlanoValor"=(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2),"PlanoRenovacaoDias"=NULLIF(r.dados->>'renovacao_dias','')::INTEGER,"PlanoQtdDisparos"=NULLIF(r.dados->>'limite_envios_dia','')::INTEGER,"PlanoQtdCanais"=NULLIF(r.dados->>'limite_canais','')::INTEGER,"PlanoQtdAtendentes"=NULLIF(r.dados->>'limite_atendentes','')::INTEGER,"PlanoPermiteIntegracao"=NULLIF(r.dados->>'permite_integracao','')::BOOLEAN,"PlanoPermiteWebhook"=NULLIF(r.dados->>'permite_webhook','')::BOOLEAN,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_canal' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Canal" ("CanalNome","CanalGUID","CanalNumeroCelular","CanalStatus","CanalStatusUso","CanalProvedorAPI","CanalMsgEncerramentoAtendimento","CanalMetaNumeroIdentificador","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'id','')::UUID,left(r.dados->>'telefone',40),left(r.dados->>'conexao',40),NULLIF(r.dados->>'ativo','')::BOOLEAN,CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END,r.dados->>'mensagem_encerramento',left(r.dados->>'identificador_meta',100),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Canal',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Canal" SET "CanalNome"=left(r.dados->>'nome',60),"CanalGUID"=NULLIF(r.dados->>'id','')::UUID,"CanalNumeroCelular"=left(r.dados->>'telefone',40),"CanalStatus"=left(r.dados->>'conexao',40),"CanalStatusUso"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"CanalProvedorAPI"=CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END,"CanalMsgEncerramentoAtendimento"=r.dados->>'mensagem_encerramento',"CanalMetaNumeroIdentificador"=left(r.dados->>'identificador_meta',100),"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_setor' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Setor" ("SetorNome","SetorStatus","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Setor',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Setor" SET "SetorNome"=left(r.dados->>'nome',70),"SetorStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendente' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Atendente" ("AtendenteNome","AtendenteStatus","AtendenteStatusChat","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,left(r.dados->>'disponibilidade',20),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Atendente',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Atendente" SET "AtendenteNome"=left(r.dados->>'nome',70),"AtendenteStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"AtendenteStatusChat"=left(r.dados->>'disponibilidade',20),"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_contato' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Contato" ("ContatoNome","ContatoNumero","ContatoEmail","ContatoInformacaoAdicional","ContatoAvatarChave","ContatoJidWhatsApp","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),left(r.dados->>'telefone',40),left(r.dados->>'email',100),r.dados->'informacoes',r.dados->>'avatar_chave',r.dados->>'identificador_whatsapp',firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Contato',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Contato" SET "ContatoNome"=left(r.dados->>'nome',70),"ContatoNumero"=left(r.dados->>'telefone',40),"ContatoEmail"=left(r.dados->>'email',100),"ContatoInformacaoAdicional"=r.dados->'informacoes',"ContatoAvatarChave"=r.dados->>'avatar_chave',"ContatoJidWhatsApp"=r.dados->>'identificador_whatsapp',"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendimento' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Atendimento" ("AtendimentoProtocolo","AtendimentoStatus","AtendimentoAvaliacao","AtendimentoQtdMensagensNaoLidas","AtendimentoBOTExterno","AtendimentoDataFinalizado","AtendimentoReceptivo","AtendimentoDuracao","AtendimentoTempoPendente","AtendimentoContextoIA","AtendimentoDataUltimaMensagemCliente","ClienteId","CanalId","ContatoId","SetorId","AtendenteId","createdAt","updatedAt") VALUES (left(r.dados->>'protocolo',16),CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END,NULLIF(r.dados->>'avaliacao','')::INTEGER,NULLIF(r.dados->>'mensagens_nao_lidas','')::INTEGER,NULLIF(r.dados->>'bot_externo','')::BOOLEAN,NULLIF(r.dados->>'finalizado_em','')::TIMESTAMP WITH TIME ZONE,NULLIF(r.dados->>'receptivo','')::BOOLEAN,NULLIF(r.dados->>'duracao_segundos','')::INTEGER,NULLIF(r.dados->>'espera_segundos','')::INTEGER,r.dados->>'contexto_ia',NULLIF(r.dados->>'ultima_mensagem_cliente_em','')::TIMESTAMP WITH TIME ZONE,firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_contato',r.dados->>'contato_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Atendimento',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Atendimento" SET "AtendimentoProtocolo"=left(r.dados->>'protocolo',16),"AtendimentoStatus"=CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END,"AtendimentoAvaliacao"=NULLIF(r.dados->>'avaliacao','')::INTEGER,"AtendimentoQtdMensagensNaoLidas"=NULLIF(r.dados->>'mensagens_nao_lidas','')::INTEGER,"AtendimentoBOTExterno"=NULLIF(r.dados->>'bot_externo','')::BOOLEAN,"AtendimentoDataFinalizado"=NULLIF(r.dados->>'finalizado_em','')::TIMESTAMP WITH TIME ZONE,"AtendimentoReceptivo"=NULLIF(r.dados->>'receptivo','')::BOOLEAN,"AtendimentoDuracao"=NULLIF(r.dados->>'duracao_segundos','')::INTEGER,"AtendimentoTempoPendente"=NULLIF(r.dados->>'espera_segundos','')::INTEGER,"AtendimentoContextoIA"=r.dados->>'contexto_ia',"AtendimentoDataUltimaMensagemCliente"=NULLIF(r.dados->>'ultima_mensagem_cliente_em','')::TIMESTAMP WITH TIME ZONE,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"ContatoId"=firma_autentica_id('chat_contato',r.dados->>'contato_id'),"SetorId"=firma_autentica_id('chat_setor',r.dados->>'setor_id'),"AtendenteId"=firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_arquivo' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Arquivo" ("ArquivoNome","ArquivoMimeType","ArquivoSize","ArquivoChave","ArquivoExtensao","ArquivoThumbnailChave","ArquivoLatitude","ArquivoLongitude","createdAt","updatedAt") VALUES (r.dados->>'nome',left(r.dados->>'tipo_mime',500),NULLIF(r.dados->>'tamanho','')::INTEGER,r.dados->>'chave_armazenamento',left(r.dados->>'extensao',10),r.dados->>'miniatura_chave',left(r.dados->>'latitude',100),left(r.dados->>'longitude',100),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Arquivo',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Arquivo" SET "ArquivoNome"=r.dados->>'nome',"ArquivoMimeType"=left(r.dados->>'tipo_mime',500),"ArquivoSize"=NULLIF(r.dados->>'tamanho','')::INTEGER,"ArquivoChave"=r.dados->>'chave_armazenamento',"ArquivoExtensao"=left(r.dados->>'extensao',10),"ArquivoThumbnailChave"=r.dados->>'miniatura_chave',"ArquivoLatitude"=left(r.dados->>'latitude',100),"ArquivoLongitude"=left(r.dados->>'longitude',100),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_mensagem' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Mensagem" ("MensagemTexto","MensagemTipo","MensagemIdWhatsApp","MensagemACK","MensagemEditada","MensagemDeletada","MensagemReacao","MensagemInterativa","AtendimentoId","CanalId","ArquivoId","MensagemDataHoraWhatsApp","MensagemFromMe","MensagemEnviadaPeloAtendente","MensagemEnviadaPeloBot","MensagemRAW","createdAt","updatedAt") VALUES (r.dados->>'texto',left(r.dados->>'tipo',60),left(r.dados->>'identificador_externo',100),NULLIF(r.dados->>'confirmacao','')::INTEGER,NULLIF(r.dados->>'editada','')::BOOLEAN,NULLIF(r.dados->>'excluida','')::BOOLEAN,r.dados->'reacoes',r.dados->'conteudo_interativo',firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_arquivo',r.dados->>'arquivo_id'),COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz),(r.dados->>'sentido'='SAIDA'),(r.dados->>'origem'='ATENDENTE'),(r.dados->>'origem'='BOT'),(r.dados->'dados_externos')::text,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Mensagem',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Mensagem" SET "MensagemTexto"=r.dados->>'texto',"MensagemTipo"=left(r.dados->>'tipo',60),"MensagemIdWhatsApp"=left(r.dados->>'identificador_externo',100),"MensagemACK"=NULLIF(r.dados->>'confirmacao','')::INTEGER,"MensagemEditada"=NULLIF(r.dados->>'editada','')::BOOLEAN,"MensagemDeletada"=NULLIF(r.dados->>'excluida','')::BOOLEAN,"MensagemReacao"=r.dados->'reacoes',"MensagemInterativa"=r.dados->'conteudo_interativo',"AtendimentoId"=firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"ArquivoId"=firma_autentica_id('chat_arquivo',r.dados->>'arquivo_id'),"MensagemDataHoraWhatsApp"=COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz),"MensagemFromMe"=(r.dados->>'sentido'='SAIDA'),"MensagemEnviadaPeloAtendente"=(r.dados->>'origem'='ATENDENTE'),"MensagemEnviadaPeloBot"=(r.dados->>'origem'='BOT'),"MensagemRAW"=(r.dados->'dados_externos')::text,"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendente_setor' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."AtendenteSetor" ("AtendenteId","SetorId","createdAt","updatedAt") VALUES (firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'AtendenteSetor',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."AtendenteSetor" SET "AtendenteId"=firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),"SetorId"=firma_autentica_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_acao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."AtendimentoAcao" ("AtendimentoAcaoTipo","AtendimentoAcaoTexto","AtendimentoId","createdAt","updatedAt") VALUES (left(r.dados->>'tipo',40),left(r.dados->>'descricao',200),firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'AtendimentoAcao',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."AtendimentoAcao" SET "AtendimentoAcaoTipo"=left(r.dados->>'tipo',40),"AtendimentoAcaoTexto"=left(r.dados->>'descricao',200),"AtendimentoId"=firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_etiqueta' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Tag" ("TagNome","TagStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Tag',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Tag" SET "TagNome"=left(r.dados->>'nome',60),"TagStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_atendimento_etiqueta' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."AtendimentoTag" ("AtendimentoId","TagId","createdAt","updatedAt") VALUES (firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),firma_autentica_id('chat_etiqueta',r.dados->>'etiqueta_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'AtendimentoTag',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."AtendimentoTag" SET "AtendimentoId"=firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),"TagId"=firma_autentica_id('chat_etiqueta',r.dados->>'etiqueta_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_mensagem_rapida' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."MensagemRapida" ("MensagemRapidaTexto","ClienteId","SetorId","createdAt","updatedAt") VALUES (r.dados->>'texto',firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'MensagemRapida',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."MensagemRapida" SET "MensagemRapidaTexto"=r.dados->>'texto',"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"SetorId"=firma_autentica_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_modelo_mensagem' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."MensagemTemplate" ("MensagemTemplateNome","MensagemTemplateCategoria","MensagemTemplateCorpo","MensagemTemplateStatusMeta","MensagemTemplateStatusUso","MensagemTemplateCabecalhoConteudo","MensagemTemplateRodape","MensagemTemplateBotoes","MensagemTemplateMetaId","MensagemTemplateCabecalhoTipo","MensagemTemplateCabecalhoVariaveis","MensagemTemplateCorpoVariaveis","MensagemTemplateMotivoRejeicaoMeta","MensagemTemplateMetodoEnvio","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END,left(r.dados->>'corpo',1024),left(r.dados->>'estado',60),true,r.dados->'cabecalho',left(r.dados->>'rodape',60),r.dados->'botoes',left(r.dados->>'identificador_meta',50),left(r.dados->>'cabecalho_tipo',40),r.dados->'cabecalho_variaveis',r.dados->'corpo_variaveis',r.dados->>'motivo_rejeicao',left(r.dados->>'metodo_envio',40),firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'MensagemTemplate',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."MensagemTemplate" SET "MensagemTemplateNome"=left(r.dados->>'nome',100),"MensagemTemplateCategoria"=CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END,"MensagemTemplateCorpo"=left(r.dados->>'corpo',1024),"MensagemTemplateStatusMeta"=left(r.dados->>'estado',60),"MensagemTemplateStatusUso"=true,"MensagemTemplateCabecalhoConteudo"=r.dados->'cabecalho',"MensagemTemplateRodape"=left(r.dados->>'rodape',60),"MensagemTemplateBotoes"=r.dados->'botoes',"MensagemTemplateMetaId"=left(r.dados->>'identificador_meta',50),"MensagemTemplateCabecalhoTipo"=left(r.dados->>'cabecalho_tipo',40),"MensagemTemplateCabecalhoVariaveis"=r.dados->'cabecalho_variaveis',"MensagemTemplateCorpoVariaveis"=r.dados->'corpo_variaveis',"MensagemTemplateMotivoRejeicaoMeta"=r.dados->>'motivo_rejeicao',"MensagemTemplateMetodoEnvio"=left(r.dados->>'metodo_envio',40),"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"SetorId"=firma_autentica_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_lista_contatos' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."ListaContato" ("ListaContatoNome","ListaContatoStatus","ListaContatoFonteContatos","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'fonte',50),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'ListaContato',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."ListaContato" SET "ListaContatoNome"=left(r.dados->>'nome',100),"ListaContatoStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ListaContatoFonteContatos"=left(r.dados->>'fonte',50),"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_integracao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Integracao" ("IntegracaoNome","IntegracaoDados","IntegracaoStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),r.dados->'configuracao',NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Integracao',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Integracao" SET "IntegracaoNome"=left(r.dados->>'nome',100),"IntegracaoDados"=r.dados->'configuracao',"IntegracaoStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_agente_ia' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."InteligenciaArtificial" ("InteligenciaArtificialNome","InteligenciaArtificialStatus","InteligenciaArtificialProvedor","InteligenciaArtificialModelo","InteligenciaArtificialTextoPrompt","InteligenciaArtificialAtivador","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'provedor',50),left(r.dados->>'modelo',50),r.dados->>'instrucoes',left(r.dados->>'ativador',50),firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'InteligenciaArtificial',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."InteligenciaArtificial" SET "InteligenciaArtificialNome"=left(r.dados->>'nome',100),"InteligenciaArtificialStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"InteligenciaArtificialProvedor"=left(r.dados->>'provedor',50),"InteligenciaArtificialModelo"=left(r.dados->>'modelo',50),"InteligenciaArtificialTextoPrompt"=r.dados->>'instrucoes',"InteligenciaArtificialAtivador"=left(r.dados->>'ativador',50),"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"SetorId"=firma_autentica_id('chat_setor',r.dados->>'setor_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_bot_definicao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."ChatbotDefinicao" ("ChatbotDefinicaoNome","ChatbotDefinicaoStatus","ChatbotDefinicaoFuncionamento","ChatbotDefinicaoFlow","ChatbotDefinicaoProvedor","ChatbotDefinicaoN8nURL","CanalId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativo','')::BOOLEAN,NULLIF(r.dados->>'funcionamento','')::JSON,NULLIF(r.dados->>'fluxo','')::JSON,left(r.dados->>'provedor',50),r.dados->>'endereco_externo',firma_autentica_id('chat_canal',r.dados->>'canal_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'ChatbotDefinicao',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."ChatbotDefinicao" SET "ChatbotDefinicaoNome"=left(r.dados->>'nome',100),"ChatbotDefinicaoStatus"=NULLIF(r.dados->>'ativo','')::BOOLEAN,"ChatbotDefinicaoFuncionamento"=NULLIF(r.dados->>'funcionamento','')::JSON,"ChatbotDefinicaoFlow"=NULLIF(r.dados->>'fluxo','')::JSON,"ChatbotDefinicaoProvedor"=left(r.dados->>'provedor',50),"ChatbotDefinicaoN8nURL"=r.dados->>'endereco_externo',"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_webhook' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."Webhook" ("WebhookURL","WebhookTodosCanais","WebhookEvento","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'destino',2000),NULLIF(r.dados->>'todos_canais','')::BOOLEAN,(r.dados->'eventos')::text,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'Webhook',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."Webhook" SET "WebhookURL"=left(r.dados->>'destino',2000),"WebhookTodosCanais"=NULLIF(r.dados->>'todos_canais','')::BOOLEAN,"WebhookEvento"=(r.dados->'eventos')::text,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_webhook_entrega' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."WebhookLog" ("WebhookLogEvento","WebhookLogDados","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'evento',100),r.dados->>'dados',firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'WebhookLog',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."WebhookLog" SET "WebhookLogEvento"=left(r.dados->>'evento',100),"WebhookLogDados"=r.dados->>'dados',"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_chave_integracao' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."ChaveAPI" ("ChaveAPIDescricao","ChaveAPIStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'ChaveAPI',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."ChaveAPI" SET "ChaveAPIDescricao"=left(r.dados->>'nome',100),"ChaveAPIStatus"=NULLIF(r.dados->>'ativa','')::BOOLEAN,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
IF TG_TABLE_NAME='chat_campanha' THEN
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica."DisparoMassa" ("DisparoMassaDataHoraInicio","DisparoMassaStatus","DisparoMassaIntervaloEntreDisparos","DisparoMassaConteudo","DisparoMassaMaxMensagemDia","ClienteId","CanalId","MensagemTemplateId","ListaContatoId","createdAt","updatedAt") VALUES (NULLIF(r.dados->>'agendada_em','')::TIMESTAMP WITH TIME ZONE,left(r.dados->>'estado',50),NULLIF(r.dados->>'intervalo_segundos','')::INTEGER,r.dados->>'texto',NULLIF(r.dados->>'maximo_diario','')::INTEGER,firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_modelo_mensagem',r.dados->>'modelo_id'),firma_autentica_id('chat_lista_contatos',r.dados->>'lista_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
   cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'DisparoMassa',novo,cliente,r.dados);
  ELSE
   UPDATE autentica."DisparoMassa" SET "DisparoMassaDataHoraInicio"=NULLIF(r.dados->>'agendada_em','')::TIMESTAMP WITH TIME ZONE,"DisparoMassaStatus"=left(r.dados->>'estado',50),"DisparoMassaIntervaloEntreDisparos"=NULLIF(r.dados->>'intervalo_segundos','')::INTEGER,"DisparoMassaConteudo"=r.dados->>'texto',"DisparoMassaMaxMensagemDia"=NULLIF(r.dados->>'maximo_diario','')::INTEGER,"ClienteId"=firma_autentica_id('empresa',r.dados->>'empresa_id'),"CanalId"=firma_autentica_id('chat_canal',r.dados->>'canal_id'),"MensagemTemplateId"=firma_autentica_id('chat_modelo_mensagem',r.dados->>'modelo_id'),"ListaContatoId"=firma_autentica_id('chat_lista_contatos',r.dados->>'lista_id'),"updatedAt"=COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now()) WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;
RETURN NEW; END $$;
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.empresa FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_plano FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_canal FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_setor FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_atendente FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_contato FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_atendimento FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_arquivo FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_mensagem FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_atendente_setor FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_acao FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_etiqueta FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_atendimento_etiqueta FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_mensagem_rapida FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_modelo_mensagem FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_lista_contatos FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_integracao FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_agente_ia FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_bot_definicao FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_webhook FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_webhook_entrega FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_chave_integracao FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.chat_campanha FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();
