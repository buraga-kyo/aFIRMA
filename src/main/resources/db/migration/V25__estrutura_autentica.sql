-- Gerado das migrações e modelos do Autentica. Reproduzir: scripts/autentica/extrair-esquema.cjs

-- Não altera nem apaga as tabelas anteriores da FIRMA.

CREATE SCHEMA "autentica";

CREATE TABLE IF NOT EXISTS "autentica"."APILog" ("id"   SERIAL , "APILogDataHoraRecebimento" TIMESTAMP WITH TIME ZONE, "APILogMetodoHTTP" VARCHAR(40), "APILogStatusCode" INTEGER, "APILogURL" VARCHAR(2000), "APILogTempoDecorridoMS" FLOAT, "APILogIP" VARCHAR(60), "APILogDadosRecebidos" TEXT, "APILogRespostaEnviada" TEXT, "APILogChaveAPI" VARCHAR(50), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Arquivo" ("id"   SERIAL , "ArquivoURL" TEXT, "ArquivoChave" TEXT, "ArquivoNome" TEXT, "ArquivoMimeType" VARCHAR(500), "ArquivoExtensao" VARCHAR(10), "ArquivoSize" INTEGER, "ArquivoThumbnailURL" TEXT, "ArquivoThumbnailChave" TEXT, "ArquivoLatitude" VARCHAR(100), "ArquivoLongitude" VARCHAR(100), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Atendente" ("id"   SERIAL , "AtendenteNome" VARCHAR(70), "AtendenteStatus" BOOLEAN, "AtendenteStatusChat" VARCHAR(20), "AtendenteSobreChat" VARCHAR(256), "AtendenteAutenticaAIUsuarioId" INTEGER, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "AtendenteNotificacoes" JSONB, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."AtendenteSetor" ("id"   SERIAL , "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AtendenteId" INTEGER, "SetorId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Atendimento" ("id"   SERIAL , "AtendimentoProtocolo" VARCHAR(16), "AtendimentoStatus" VARCHAR(20), "AtendimentoAvaliacao" INTEGER, "AtendimentoQtdMensagensNaoLidas" INTEGER, "AtendimentoBOTExterno" BOOLEAN, "AtendimentoDataFinalizado" TIMESTAMP WITH TIME ZONE, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "AtendenteId" INTEGER, "CanalId" INTEGER, "ContatoId" INTEGER, "SetorId" INTEGER, "MensagemId" INTEGER, "AtendimentoContextoIA" TEXT, "AtendimentoReceptivo" BOOLEAN, "AtendimentoDuracao" INTEGER, "AtendimentoTempoPendente" INTEGER, "AtendimentoTempoRespostaCliente" INTEGER, "AtendimentoTempoRespostaAtendente" INTEGER, "AtendimentoDataUltimaMensagemCliente" TIMESTAMP WITH TIME ZONE, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."AtendimentoAcao" ("id"   SERIAL , "AtendimentoAcaoTipo" VARCHAR(40), "AtendimentoAcaoTexto" VARCHAR(200), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AtendimentoId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."AtendimentoTag" ("id"   SERIAL , "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AtendimentoId" INTEGER, "TagId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Canal" ("id"   SERIAL , "CanalNome" VARCHAR(60), "CanalGUID" UUID, "CanalNomePastaWhatsApp" VARCHAR(40), "CanalNumeroCelular" VARCHAR(40), "CanalStatus" VARCHAR(40), "CanalStatusUso" BOOLEAN, "CanalProvedorAPI" VARCHAR(40), "CanalValorCredsWhatsApp" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "CanalMetaNumeroIdentificador" VARCHAR(100), "CanalMetaWAID" VARCHAR(60), "CanalMsgEncerramentoAtendimento" TEXT, "CanalVisualizaMenuBOTChat" BOOLEAN, "CanalFinalizaAtendimentoPorTempoInatividade" BOOLEAN, "CanalAtendimentoTempoInatividade" INTEGER, "CanalMsgFinalizarTempoInatividade" TEXT, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."ChatbotDefinicao" ("id"   SERIAL , "ChatbotDefinicaoNome" VARCHAR(100), "ChatbotDefinicaoJSON" TEXT, "ChatbotDefinicaoMetadata" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "CanalId" INTEGER, "ChatbotDefinicaoStatus" BOOLEAN, "ChatbotDefinicaoFuncionamento" JSON, "ChatbotDefinicaoFlow" JSON, "ChatbotDefinicaoProvedor" VARCHAR(50), "ChatbotDefinicaoN8nURL" TEXT, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."ChatbotMetadata" ("id"   SERIAL , "ChatbotMetadataIdentificador" VARCHAR(100), "ChatbotMetadataNomeBOT" VARCHAR(100), "ChatbotMetadataCanalGUID" UUID, "ChatbotMetadataDados" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "CanalId" INTEGER, "ChatbotMensagens" JSON, "ChatbotMetadataDefinicao" JSON, "ChatbotMetadataDadosN8n" JSONB, "ChatbotMetadataProvedorBOT" VARCHAR(50), PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."ChaveAPI" ("id"   SERIAL , "ChaveAPIDescricao" VARCHAR(100), "ChaveAPIStatus" BOOLEAN, "ChaveAPIValor" UUID, "ChaveAPIResponsavelCriacao" VARCHAR(100), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Cliente" ("id"   SERIAL , "ClienteNome" VARCHAR(255), "ClienteStatus" BOOLEAN, "ClienteProvedorAPI" VARCHAR(40), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteMetaToken" VARCHAR(500), "ClienteMetaIdAplicativo" VARCHAR(100), "ClienteRazaoSocial" VARCHAR(100), "ClienteCpfCnpj" VARCHAR(20), "ClienteCep" VARCHAR(8), "ClienteLogradouro" VARCHAR(100), "ClienteNumero" VARCHAR(30), "ClienteComplemento" VARCHAR(30), "ClienteBairro" VARCHAR(50), "ClienteCidade" VARCHAR(80), "ClienteEstado" VARCHAR(50), "ClienteTelefone" VARCHAR(50), "ClienteEmail" VARCHAR(50), "PlanoId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Contato" ("id"   SERIAL , "ContatoNome" VARCHAR(70), "ContatoNumero" VARCHAR(40), "ContatoEmail" VARCHAR(100), "ContatoInformacaoAdicional" JSONB, "ContatoAvatar" TEXT, "ContatoAvatarChave" TEXT, "ContatoJidWhatsApp" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Mensagem" ("id"   SERIAL , "MensagemTexto" TEXT, "MensagemRemetente" VARCHAR(100), "MensagemDestinatario" VARCHAR(100), "MensagemACK" INTEGER, "MensagemFromMe" BOOLEAN, "MensagemTipo" VARCHAR(60), "MensagemEnviadaPeloBot" BOOLEAN, "MensagemEnviadaViaAPI" BOOLEAN, "MensagemEnviadaPeloAtendente" BOOLEAN, "MensagemRAW" TEXT, "MensagemIdWhatsApp" VARCHAR(100), "MensagemDataHoraWhatsApp" TIMESTAMP WITH TIME ZONE, "MensagemTimestampWhatsApp" INTEGER, "MensagemDeletada" BOOLEAN, "MensagemKeyWhatsApp" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "CanalId" INTEGER, "MensagemRespostaId" INTEGER, "AtendimentoId" INTEGER, "AtendenteId" INTEGER, "ArquivoId" INTEGER, "MensagemReacao" JSONB, "MensagemEditada" BOOLEAN, "MensagemInterativa" JSONB, "MensagemEnviadaForaZapio" BOOLEAN, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."MensagemRapida" ("id"   SERIAL , "MensagemRapidaGUID" UUID, "MensagemRapidaTexto" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "SetorId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Setor" ("id"   SERIAL , "SetorStatus" BOOLEAN, "SetorNome" VARCHAR(70), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "CanalId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Tag" ("id"   SERIAL , "TagStatus" BOOLEAN, "TagNome" VARCHAR(60), "TagGUID" UUID, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "SetorId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Webhook" ("id"   SERIAL , "WebhookURL" VARCHAR(2000), "WebhookResponsavelCriacao" VARCHAR(100), "WebhookEvento" VARCHAR(100), "WebhookTodosCanais" BOOLEAN, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "CanalId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."WebhookLog" ("id"   SERIAL , "WebhookLogDataHoraEnvio" TIMESTAMP WITH TIME ZONE, "WebhookLogDataHoraRecebimento" TIMESTAMP WITH TIME ZONE, "WebhookLogStatusCode" INTEGER, "WebhookLogEvento" VARCHAR(100), "WebhookLogDados" TEXT, "WebhookLogURLNotificada" VARCHAR(2000), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."MensagemTemplate" ("id"   SERIAL , "MensagemTemplateNome" VARCHAR(100), "MensagemTemplateCategoria" VARCHAR(40), "MensagemTemplateMetaId" VARCHAR(50), "MensagemTemplateCabecalhoTipo" VARCHAR(40), "MensagemTemplateCabecalhoConteudo" JSONB, "MensagemTemplateCabecalhoVariaveis" JSONB, "MensagemTemplateCorpo" VARCHAR(1024), "MensagemTemplateCorpoVariaveis" JSONB, "MensagemTemplateRodape" VARCHAR(60), "MensagemTemplateBotoes" JSONB, "MensagemTemplateStatusMeta" VARCHAR(60), "MensagemTemplateStatusUso" BOOLEAN, "MensagemTemplateMotivoRejeicaoMeta" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "SetorId" INTEGER, "MensagemTemplateMetodoEnvio" VARCHAR(40), "CanalId" INTEGER, "MensagemTemplateMedia" TEXT, "MensagemTemplateMediaChave" TEXT, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."InteligenciaArtificial" ("id"   SERIAL , "InteligenciaArtificialNome" VARCHAR(100), "InteligenciaArtificialStatus" BOOLEAN, "InteligenciaArtificialProvedor" VARCHAR(50), "InteligenciaArtificialModelo" VARCHAR(50), "InteligenciaArtificialProvedorAPIKey" TEXT, "InteligenciaArtificialTextoPrompt" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "CanalId" INTEGER, "InteligenciaArtificialAtivador" VARCHAR(50), "ClienteId" INTEGER, "SetorId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."ListaContato" ("id"   SERIAL , "ListaContatoNome" VARCHAR(100), "ListaContatoStatus" BOOLEAN, "ListaContatoContatos" JSONB, "ListaContatoQuantidadeContatos" INTEGER, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "ListaContatoFonteContatos" VARCHAR(50), PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."DisparoMassa" ("id"   SERIAL , "DisparoMassaDataHoraInicio" TIMESTAMP WITH TIME ZONE, "DisparoMassaStatus" VARCHAR(50), "DisparoMassaMensagensPorDisparos" INTEGER, "DisparoMassaIntervaloEntreDisparos" INTEGER, "DisparoMassaMaxMensagemDia" INTEGER, "DisparoMassaUltimoHorario" VARCHAR(5), "DisparoMassaHoraReinicio" VARCHAR(5), "DisparoMassaDiasEnvio" VARCHAR(50), "DisparoMassaConteudo" TEXT, "DisparoMassaQuantidadeMensagens" INTEGER, "DisparoMassaQuantidadePendente" INTEGER, "DisparoMassaQuantidadeEnviado" INTEGER, "DisparoMassaQuantidadeFalha" INTEGER, "DisparoMassaQuantidadeCancelado" INTEGER, "DisparoMassaMensagens" JSONB, "DisparoMassaTemplateVariaveis" JSONB, "DisparoMassaAnexo" TEXT, "DisparoMassaAnexoChave" TEXT, "DisparoMassaAnexoTipo" VARCHAR(40), "DisparoMassaAnexoNome" VARCHAR(100), "DisparoMassaColunaDestinatario" VARCHAR(50), "DisparoMassaProcessandoMensagens" BOOLEAN, "DisparoMassaDesejaGerarAtendimento" BOOLEAN, "DisparoMassaDiaReinicio" TIMESTAMP WITH TIME ZONE, "DisparoMassaQtdMensagensEnviadasDiaAtual" JSONB, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "CanalId" INTEGER, "MensagemTemplateId" INTEGER, "ListaContatoId" INTEGER, "SetorId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Integracao" ("id"   SERIAL , "IntegracaoNome" VARCHAR(100), "IntegracaoDados" JSONB, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "ClienteId" INTEGER, "IntegracaoStatus" BOOLEAN, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."Plano" ("id"   SERIAL , "PlanoNome" VARCHAR(128), "PlanoStatus" BOOLEAN, "PlanoValor" DECIMAL(12,2), "PlanoRenovacaoDias" INTEGER, "PlanoQtdDisparos" INTEGER, "PlanoQtdCanais" INTEGER, "PlanoQtdAtendentes" INTEGER, "PlanoPermiteIntegracao" BOOLEAN, "PlanoPermiteWebhook" BOOLEAN, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica"."FCMToken" ("id"   SERIAL , "Token" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AtendenteId" INTEGER, PRIMARY KEY ("id"));

CREATE SCHEMA "autentica_identidade";

CREATE TABLE IF NOT EXISTS "autentica_identidade"."Usuario" ("id"   SERIAL , "UsuarioPrimeiroNome" VARCHAR(50), "UsuarioUltimoNome" VARCHAR(50), "UsuarioEmail" VARCHAR(200), "UsuarioSenha" VARCHAR(300), "UsuarioRandomBytes" VARCHAR(100), "UsuarioTipoAutenticacao" VARCHAR(50), "UsuarioAvatar" TEXT, "UsuarioAvatarChaveS3" TEXT, "UsuarioClienteId" INTEGER, "UsuarioBloqueado" BOOLEAN, "UsuarioQtdTentativasLoginFalha" INTEGER, "UsuarioUltimaTentativaLoginFalha" TIMESTAMP WITH TIME ZONE, "UsuarioRecSenhaToken" VARCHAR(6), "UsuarioRecSenhaTokenTempoExpirar" TIMESTAMP WITH TIME ZONE, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "RepositorioId" INTEGER, "PerfilId" INTEGER, "UsuarioNomeCompleto" VARCHAR(100), PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."Sessao" ("id"   SERIAL , "SessaoJWT" TEXT, "SessaoStatus" VARCHAR(1), "SessaoTipoAutenticacao" VARCHAR(50), "SessaoCriacao" TIMESTAMP WITH TIME ZONE, "SessaoUltimoAcesso" TIMESTAMP WITH TIME ZONE, "SessaoHabilidadesCasl" TEXT, "SessaoIP" VARCHAR(60), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "RepositorioId" INTEGER, "UsuarioId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."Repositorio" ("id"   SERIAL , "RepositorioNome" VARCHAR(100), "RepositorioToken" VARCHAR(100), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."Permissao" ("id"   SERIAL , "PermissaoNome" VARCHAR(100), "PermissaoAcao" VARCHAR(50), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "RepositorioId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."Perfil" ("id"   SERIAL , "PerfilNome" VARCHAR(100), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "RepositorioId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."PerfilPermissao" ("id"   SERIAL , "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "RepositorioId" INTEGER, "PerfilId" INTEGER, "PermissaoId" INTEGER, PRIMARY KEY ("id"));

CREATE TABLE IF NOT EXISTS "autentica_identidade"."UsuarioPermissao" ("id"   SERIAL , "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "UsuarioId" INTEGER, "PermissaoId" INTEGER, "RepositorioId" INTEGER, PRIMARY KEY ("id"));

ALTER TABLE "autentica"."Atendente" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."AtendenteSetor" ADD FOREIGN KEY ("AtendenteId") REFERENCES "autentica"."Atendente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."AtendenteSetor" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("AtendenteId") REFERENCES "autentica"."Atendente" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("ContatoId") REFERENCES "autentica"."Contato" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Atendimento" ADD FOREIGN KEY ("MensagemId") REFERENCES "autentica"."Mensagem" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."AtendimentoAcao" ADD FOREIGN KEY ("AtendimentoId") REFERENCES "autentica"."Atendimento" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."AtendimentoTag" ADD FOREIGN KEY ("AtendimentoId") REFERENCES "autentica"."Atendimento" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."AtendimentoTag" ADD FOREIGN KEY ("TagId") REFERENCES "autentica"."Tag" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Canal" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."ChatbotDefinicao" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."ChatbotMetadata" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."ChaveAPI" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Cliente" ADD FOREIGN KEY ("PlanoId") REFERENCES "autentica"."Plano" ("id") ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "autentica"."Contato" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Mensagem" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Mensagem" ADD FOREIGN KEY ("MensagemRespostaId") REFERENCES "autentica"."Mensagem" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Mensagem" ADD FOREIGN KEY ("AtendimentoId") REFERENCES "autentica"."Atendimento" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Mensagem" ADD FOREIGN KEY ("AtendenteId") REFERENCES "autentica"."Atendente" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Mensagem" ADD FOREIGN KEY ("ArquivoId") REFERENCES "autentica"."Arquivo" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."MensagemRapida" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."MensagemRapida" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Setor" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Tag" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Tag" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Webhook" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."Webhook" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."WebhookLog" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."MensagemTemplate" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."MensagemTemplate" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."MensagemTemplate" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."InteligenciaArtificial" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

ALTER TABLE "autentica"."InteligenciaArtificial" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."InteligenciaArtificial" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."ListaContato" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."DisparoMassa" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."DisparoMassa" ADD FOREIGN KEY ("CanalId") REFERENCES "autentica"."Canal" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."DisparoMassa" ADD FOREIGN KEY ("MensagemTemplateId") REFERENCES "autentica"."MensagemTemplate" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."DisparoMassa" ADD FOREIGN KEY ("ListaContatoId") REFERENCES "autentica"."ListaContato" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."DisparoMassa" ADD FOREIGN KEY ("SetorId") REFERENCES "autentica"."Setor" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."Integracao" ADD FOREIGN KEY ("ClienteId") REFERENCES "autentica"."Cliente" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica"."FCMToken" ADD FOREIGN KEY ("AtendenteId") REFERENCES "autentica"."Atendente" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Usuario" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Usuario" ADD FOREIGN KEY ("PerfilId") REFERENCES "autentica_identidade"."Perfil" ("id") ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Sessao" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Sessao" ADD FOREIGN KEY ("UsuarioId") REFERENCES "autentica_identidade"."Usuario" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Permissao" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."Perfil" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."PerfilPermissao" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."PerfilPermissao" ADD FOREIGN KEY ("PerfilId") REFERENCES "autentica_identidade"."Perfil" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."PerfilPermissao" ADD FOREIGN KEY ("PermissaoId") REFERENCES "autentica_identidade"."Permissao" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."UsuarioPermissao" ADD FOREIGN KEY ("UsuarioId") REFERENCES "autentica_identidade"."Usuario" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."UsuarioPermissao" ADD FOREIGN KEY ("PermissaoId") REFERENCES "autentica_identidade"."Permissao" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "autentica_identidade"."UsuarioPermissao" ADD FOREIGN KEY ("RepositorioId") REFERENCES "autentica_identidade"."Repositorio" ("id") ON DELETE CASCADE ON UPDATE CASCADE;

CREATE UNIQUE INDEX "atendimento_pendente_unique" ON "autentica"."Atendimento" ("CanalId", "ContatoId", "AtendimentoStatus") WHERE "AtendimentoStatus" = 'pendente';

CREATE INDEX "canal_createdat_idx" ON "autentica"."Atendimento" USING BTREE ("CanalId", "AtendimentoStatus", "updatedAt" DESC);

CREATE INDEX "idx_atendimentotag_atendimentoid_id" ON "autentica"."AtendimentoTag" ("AtendimentoId", "id" DESC);

CREATE UNIQUE INDEX "canal__canal_g_u_i_d" ON "autentica"."Canal" ("CanalGUID");

CREATE UNIQUE INDEX "contato__contato_numero__cliente_id" ON "autentica"."Contato" ("ContatoNumero", "ClienteId");

CREATE UNIQUE INDEX "mensagem__mensagem_id_whats_app" ON "autentica"."Mensagem" ("MensagemIdWhatsApp");

CREATE INDEX "idx_mensagem_atendimentoid" ON "autentica"."Mensagem" ("AtendimentoId" DESC NULLS LAST);

CREATE INDEX "idx_mensagem_whatsapp_created_at" ON "autentica"."Mensagem" ("MensagemIdWhatsApp" ASC NULLS LAST, "createdAt" ASC NULLS LAST);

CREATE INDEX "idx_mensagem_whatsapp_created_at_desc" ON "autentica"."Mensagem" ("MensagemIdWhatsApp" ASC NULLS LAST, "createdAt" DESC NULLS LAST);

CREATE INDEX "atendimento_createdat_idx" ON "autentica"."Mensagem" USING BTREE ("AtendimentoId" ASC NULLS LAST, "createdAt" DESC);

CREATE UNIQUE INDEX "mensagem__mensagem_id_whats_app__canal_id" ON "autentica"."Mensagem" ("MensagemIdWhatsApp", "CanalId");

CREATE UNIQUE INDEX "idx_token_unique" ON "autentica"."FCMToken" ("Token");
