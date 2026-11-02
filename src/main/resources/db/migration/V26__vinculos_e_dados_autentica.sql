-- Mantém as tabelas anteriores e registra a correspondência de cada ID e de seus dados originais.
CREATE TABLE firma_autentica_vinculo (
 origem VARCHAR(100) NOT NULL, origem_id TEXT NOT NULL,
 destino VARCHAR(100) NOT NULL, destino_id INTEGER NOT NULL,
 cliente_id INTEGER REFERENCES autentica."Cliente"(id), dados_origem JSONB NOT NULL,
 PRIMARY KEY(origem,origem_id), UNIQUE(destino,destino_id)
);
CREATE TABLE firma_autentica_empresa (
 empresa_id INTEGER PRIMARY KEY REFERENCES "Empresas"("EmpresaId"),
 cliente_id INTEGER NOT NULL REFERENCES autentica."Cliente"(id),
 repositorio_id INTEGER REFERENCES autentica_identidade."Repositorio"(id)
);
CREATE FUNCTION firma_autentica_id(tabela TEXT, identificador TEXT) RETURNS INTEGER LANGUAGE SQL STABLE AS $$
 SELECT destino_id FROM firma_autentica_vinculo WHERE origem=tabela AND origem_id=identificador
$$;


DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.empresa t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Cliente" ("ClienteNome","ClienteStatus","ClienteRazaoSocial","ClienteCpfCnpj","ClienteTelefone","ClienteEmail","createdAt","updatedAt") VALUES (left(r.dados->>'nome',255),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'razao_social',100),left(r.dados->>'documento',20),left(r.dados->>'telefone',50),left(r.dados->>'email',50),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := novo;
  INSERT INTO firma_autentica_vinculo VALUES ('empresa',chave,'Cliente',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_plano t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Plano" ("PlanoNome","PlanoStatus","PlanoValor","PlanoRenovacaoDias","PlanoQtdDisparos","PlanoQtdCanais","PlanoQtdAtendentes","PlanoPermiteIntegracao","PlanoPermiteWebhook","createdAt","updatedAt") VALUES (left(r.dados->>'nome',128),NULLIF(r.dados->>'ativo','')::BOOLEAN,(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2),NULLIF(r.dados->>'renovacao_dias','')::INTEGER,NULLIF(r.dados->>'limite_envios_dia','')::INTEGER,NULLIF(r.dados->>'limite_canais','')::INTEGER,NULLIF(r.dados->>'limite_atendentes','')::INTEGER,NULLIF(r.dados->>'permite_integracao','')::BOOLEAN,NULLIF(r.dados->>'permite_webhook','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_plano',chave,'Plano',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_canal t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Canal" ("CanalNome","CanalGUID","CanalNumeroCelular","CanalStatus","CanalStatusUso","CanalProvedorAPI","CanalMsgEncerramentoAtendimento","CanalMetaNumeroIdentificador","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'id','')::UUID,left(r.dados->>'telefone',40),left(r.dados->>'conexao',40),NULLIF(r.dados->>'ativo','')::BOOLEAN,CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END,r.dados->>'mensagem_encerramento',left(r.dados->>'identificador_meta',100),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_canal',chave,'Canal',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_setor t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Setor" ("SetorNome","SetorStatus","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_setor',chave,'Setor',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_atendente t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Atendente" ("AtendenteNome","AtendenteStatus","AtendenteStatusChat","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),NULLIF(r.dados->>'ativo','')::BOOLEAN,left(r.dados->>'disponibilidade',20),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_atendente',chave,'Atendente',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_contato t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Contato" ("ContatoNome","ContatoNumero","ContatoEmail","ContatoInformacaoAdicional","ContatoAvatarChave","ContatoJidWhatsApp","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',70),left(r.dados->>'telefone',40),left(r.dados->>'email',100),r.dados->'informacoes',r.dados->>'avatar_chave',r.dados->>'identificador_whatsapp',firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_contato',chave,'Contato',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_atendimento t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Atendimento" ("AtendimentoProtocolo","AtendimentoStatus","AtendimentoAvaliacao","AtendimentoQtdMensagensNaoLidas","AtendimentoBOTExterno","AtendimentoDataFinalizado","AtendimentoReceptivo","AtendimentoDuracao","AtendimentoTempoPendente","AtendimentoContextoIA","AtendimentoDataUltimaMensagemCliente","ClienteId","CanalId","ContatoId","SetorId","AtendenteId","createdAt","updatedAt") VALUES (left(r.dados->>'protocolo',16),CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END,NULLIF(r.dados->>'avaliacao','')::INTEGER,NULLIF(r.dados->>'mensagens_nao_lidas','')::INTEGER,NULLIF(r.dados->>'bot_externo','')::BOOLEAN,NULLIF(r.dados->>'finalizado_em','')::TIMESTAMP WITH TIME ZONE,NULLIF(r.dados->>'receptivo','')::BOOLEAN,NULLIF(r.dados->>'duracao_segundos','')::INTEGER,NULLIF(r.dados->>'espera_segundos','')::INTEGER,r.dados->>'contexto_ia',NULLIF(r.dados->>'ultima_mensagem_cliente_em','')::TIMESTAMP WITH TIME ZONE,firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_contato',r.dados->>'contato_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_atendimento',chave,'Atendimento',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_arquivo t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Arquivo" ("ArquivoNome","ArquivoMimeType","ArquivoSize","ArquivoChave","ArquivoExtensao","ArquivoThumbnailChave","ArquivoLatitude","ArquivoLongitude","createdAt","updatedAt") VALUES (r.dados->>'nome',left(r.dados->>'tipo_mime',500),NULLIF(r.dados->>'tamanho','')::INTEGER,r.dados->>'chave_armazenamento',left(r.dados->>'extensao',10),r.dados->>'miniatura_chave',left(r.dados->>'latitude',100),left(r.dados->>'longitude',100),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_arquivo',chave,'Arquivo',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_mensagem t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Mensagem" ("MensagemTexto","MensagemTipo","MensagemIdWhatsApp","MensagemACK","MensagemEditada","MensagemDeletada","MensagemReacao","MensagemInterativa","AtendimentoId","CanalId","ArquivoId","MensagemDataHoraWhatsApp","MensagemFromMe","MensagemEnviadaPeloAtendente","MensagemEnviadaPeloBot","MensagemRAW","createdAt","updatedAt") VALUES (r.dados->>'texto',left(r.dados->>'tipo',60),left(r.dados->>'identificador_externo',100),NULLIF(r.dados->>'confirmacao','')::INTEGER,NULLIF(r.dados->>'editada','')::BOOLEAN,NULLIF(r.dados->>'excluida','')::BOOLEAN,r.dados->'reacoes',r.dados->'conteudo_interativo',firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_arquivo',r.dados->>'arquivo_id'),COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz),(r.dados->>'sentido'='SAIDA'),(r.dados->>'origem'='ATENDENTE'),(r.dados->>'origem'='BOT'),(r.dados->'dados_externos')::text,COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_mensagem',chave,'Mensagem',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_atendente_setor t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."AtendenteSetor" ("AtendenteId","SetorId","createdAt","updatedAt") VALUES (firma_autentica_id('chat_atendente',r.dados->>'atendente_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_atendente_setor',chave,'AtendenteSetor',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_acao t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."AtendimentoAcao" ("AtendimentoAcaoTipo","AtendimentoAcaoTexto","AtendimentoId","createdAt","updatedAt") VALUES (left(r.dados->>'tipo',40),left(r.dados->>'descricao',200),firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_acao',chave,'AtendimentoAcao',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_etiqueta t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Tag" ("TagNome","TagStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',60),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_etiqueta',chave,'Tag',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_atendimento_etiqueta t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."AtendimentoTag" ("AtendimentoId","TagId","createdAt","updatedAt") VALUES (firma_autentica_id('chat_atendimento',r.dados->>'atendimento_id'),firma_autentica_id('chat_etiqueta',r.dados->>'etiqueta_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_atendimento_etiqueta',chave,'AtendimentoTag',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_mensagem_rapida t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."MensagemRapida" ("MensagemRapidaTexto","ClienteId","SetorId","createdAt","updatedAt") VALUES (r.dados->>'texto',firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_mensagem_rapida',chave,'MensagemRapida',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_modelo_mensagem t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."MensagemTemplate" ("MensagemTemplateNome","MensagemTemplateCategoria","MensagemTemplateCorpo","MensagemTemplateStatusMeta","MensagemTemplateStatusUso","MensagemTemplateCabecalhoConteudo","MensagemTemplateRodape","MensagemTemplateBotoes","MensagemTemplateMetaId","MensagemTemplateCabecalhoTipo","MensagemTemplateCabecalhoVariaveis","MensagemTemplateCorpoVariaveis","MensagemTemplateMotivoRejeicaoMeta","MensagemTemplateMetodoEnvio","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END,left(r.dados->>'corpo',1024),left(r.dados->>'estado',60),true,r.dados->'cabecalho',left(r.dados->>'rodape',60),r.dados->'botoes',left(r.dados->>'identificador_meta',50),left(r.dados->>'cabecalho_tipo',40),r.dados->'cabecalho_variaveis',r.dados->'corpo_variaveis',r.dados->>'motivo_rejeicao',left(r.dados->>'metodo_envio',40),firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_modelo_mensagem',chave,'MensagemTemplate',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_lista_contatos t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."ListaContato" ("ListaContatoNome","ListaContatoStatus","ListaContatoFonteContatos","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'fonte',50),firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_lista_contatos',chave,'ListaContato',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_integracao t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Integracao" ("IntegracaoNome","IntegracaoDados","IntegracaoStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),r.dados->'configuracao',NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_integracao',chave,'Integracao',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_agente_ia t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."InteligenciaArtificial" ("InteligenciaArtificialNome","InteligenciaArtificialStatus","InteligenciaArtificialProvedor","InteligenciaArtificialModelo","InteligenciaArtificialTextoPrompt","InteligenciaArtificialAtivador","ClienteId","CanalId","SetorId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,left(r.dados->>'provedor',50),left(r.dados->>'modelo',50),r.dados->>'instrucoes',left(r.dados->>'ativador',50),firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_setor',r.dados->>'setor_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_agente_ia',chave,'InteligenciaArtificial',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_bot_definicao t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."ChatbotDefinicao" ("ChatbotDefinicaoNome","ChatbotDefinicaoStatus","ChatbotDefinicaoFuncionamento","ChatbotDefinicaoFlow","ChatbotDefinicaoProvedor","ChatbotDefinicaoN8nURL","CanalId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativo','')::BOOLEAN,NULLIF(r.dados->>'funcionamento','')::JSON,NULLIF(r.dados->>'fluxo','')::JSON,left(r.dados->>'provedor',50),r.dados->>'endereco_externo',firma_autentica_id('chat_canal',r.dados->>'canal_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_bot_definicao',chave,'ChatbotDefinicao',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_webhook t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."Webhook" ("WebhookURL","WebhookTodosCanais","WebhookEvento","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'destino',2000),NULLIF(r.dados->>'todos_canais','')::BOOLEAN,(r.dados->'eventos')::text,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_webhook',chave,'Webhook',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_webhook_entrega t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."WebhookLog" ("WebhookLogEvento","WebhookLogDados","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'evento',100),r.dados->>'dados',firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_webhook_entrega',chave,'WebhookLog',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_chave_integracao t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."ChaveAPI" ("ChaveAPIDescricao","ChaveAPIStatus","ClienteId","createdAt","updatedAt") VALUES (left(r.dados->>'nome',100),NULLIF(r.dados->>'ativa','')::BOOLEAN,firma_autentica_id('empresa',r.dados->>'empresa_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_chave_integracao',chave,'ChaveAPI',novo,cliente,r.dados);
 END LOOP;
END $$;

DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.chat_campanha t LOOP
  chave := COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'));
  INSERT INTO autentica."DisparoMassa" ("DisparoMassaDataHoraInicio","DisparoMassaStatus","DisparoMassaIntervaloEntreDisparos","DisparoMassaConteudo","DisparoMassaMaxMensagemDia","ClienteId","CanalId","MensagemTemplateId","ListaContatoId","createdAt","updatedAt") VALUES (NULLIF(r.dados->>'agendada_em','')::TIMESTAMP WITH TIME ZONE,left(r.dados->>'estado',50),NULLIF(r.dados->>'intervalo_segundos','')::INTEGER,r.dados->>'texto',NULLIF(r.dados->>'maximo_diario','')::INTEGER,firma_autentica_id('empresa',r.dados->>'empresa_id'),firma_autentica_id('chat_canal',r.dados->>'canal_id'),firma_autentica_id('chat_modelo_mensagem',r.dados->>'modelo_id'),firma_autentica_id('chat_lista_contatos',r.dados->>'lista_id'),COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now()),COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())) RETURNING id INTO novo;
  cliente := firma_autentica_id('empresa',r.dados->>'empresa_id');
  INSERT INTO firma_autentica_vinculo VALUES ('chat_campanha',chave,'DisparoMassa',novo,cliente,r.dados);
 END LOOP;
END $$;

UPDATE autentica."Mensagem" m SET "MensagemRespostaId"=firma_autentica_id('chat_mensagem',v.dados_origem->>'mensagem_referenciada_id') FROM firma_autentica_vinculo v WHERE v.destino='Mensagem' AND v.destino_id=m.id;
UPDATE autentica."Setor" s SET "CanalId"=(SELECT min(c.id) FROM autentica."Canal" c JOIN firma_autentica_vinculo vc ON vc.destino='Canal' AND vc.destino_id=c.id WHERE vc.dados_origem->>'setor_padrao_id'=v.origem_id) FROM firma_autentica_vinculo v WHERE v.destino='Setor' AND v.destino_id=s.id;
DO $$ DECLARE e RECORD; cid INTEGER; rid INTEGER; BEGIN
 FOR e IN SELECT * FROM "Empresas" LOOP
  SELECT id INTO cid FROM autentica."Cliente" WHERE (NULLIF("ClienteEmail",'') IS NOT NULL AND lower("ClienteEmail")=lower(e."EmpresaEmail")) OR (NULLIF("ClienteCpfCnpj",'') IS NOT NULL AND "ClienteCpfCnpj"=e."EmpresaCNPJ") ORDER BY id LIMIT 1;
  IF cid IS NULL AND (SELECT count(*) FROM empresa)=1 AND (SELECT count(*) FROM "Empresas")=1 THEN SELECT min(id) INTO cid FROM autentica."Cliente"; END IF;
  IF cid IS NULL THEN INSERT INTO autentica."Cliente"("ClienteNome","ClienteEmail","ClienteCpfCnpj","ClienteStatus","createdAt","updatedAt") VALUES(COALESCE(e."EmpresaNome",e."EmpresaUsuario"),e."EmpresaEmail",e."EmpresaCNPJ",true,now(),now()) RETURNING id INTO cid; END IF;
  INSERT INTO autentica_identidade."Repositorio"("RepositorioNome","createdAt","updatedAt") VALUES('aFIRMA · '||cid,now(),now()) RETURNING id INTO rid;
  INSERT INTO firma_autentica_empresa VALUES(e."EmpresaId",cid,rid);
 END LOOP;
END $$;


UPDATE autentica."Cliente" c SET "PlanoId"=firma_autentica_id('chat_plano',a.plano_id::text) FROM chat_assinatura_plano a JOIN firma_autentica_vinculo v ON v.origem='empresa' AND v.origem_id=a.empresa_id::text WHERE c.id=v.destino_id AND a.estado='ATIVA';
UPDATE autentica."ListaContato" l SET "ListaContatoContatos"=COALESCE((SELECT jsonb_agg(jsonb_build_object('ContatoId',firma_autentica_id('chat_contato',c.id::text),'Nome',c.nome,'Numero',c.telefone,'Email',c.email)) FROM chat_lista_destinatario d JOIN chat_contato c ON c.id=d.contato_id WHERE d.lista_id=v.origem_id::bigint),'[]'::jsonb),"ListaContatoQuantidadeContatos"=(SELECT count(*) FROM chat_lista_destinatario d WHERE d.lista_id=v.origem_id::bigint) FROM firma_autentica_vinculo v WHERE v.destino='ListaContato' AND v.destino_id=l.id;