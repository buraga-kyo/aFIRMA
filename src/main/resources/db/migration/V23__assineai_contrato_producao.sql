-- Estrutura Sequelize original. Tabelas existentes, IDs e sequências são preservados.
CREATE TABLE IF NOT EXISTS "Empresas" ("EmpresaId"  SERIAL , "EmpresaUsuario" VARCHAR(255), "EmpresaSenha" VARCHAR(255), "EmpresaNome" VARCHAR(255), "EmpresaCNPJ" VARCHAR(255), "EmpresaEmail" VARCHAR(255), "EmpresaTemaTelaAssinatura" VARCHAR(255), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("EmpresaId"));
CREATE TABLE IF NOT EXISTS "Assinaturas" ("AssinaturaId"  SERIAL , "AssinaturaNome" VARCHAR(255), "AssinaturaResponsavel" VARCHAR(255), "AssinaturaStatus" VARCHAR(255), "AssinaturaQuantidadeDocumentoAssinado" VARCHAR(255), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY ("AssinaturaId"));
CREATE TABLE IF NOT EXISTS "AssinaturaDocumentos" ("DocumentoId"  SERIAL , "DocumentoTitulo" VARCHAR(255), "DocumentoBuffer" BYTEA, "DocumentoAssinadoBuffer" BYTEA, "DocumentoColecaoDeDivArrastavel" JSON, "DocumentoToken" TEXT, "DocumentoCriptografiaChavePublica" TEXT, "DocumentoCriptografiaAssinatura" TEXT, "DocumentoHashDoPDFOriginal" TEXT, "DocumentoLinkAutenticacao" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AssinaturaId" INTEGER REFERENCES "Assinaturas" ("AssinaturaId") ON DELETE SET NULL ON UPDATE CASCADE, PRIMARY KEY ("DocumentoId"));
CREATE TABLE IF NOT EXISTS "AssinaturaSignatarios" ("SignatarioId"  SERIAL , "SignatarioFormaDeAutenticacao" VARCHAR(255)[], "SignatarioFormaDeNotificacao" VARCHAR(255)[], "SignatarioAssinou" BOOLEAN, "SignatarioNome" VARCHAR(255), "SignatarioEmail" VARCHAR(255), "SignatarioRG" VARCHAR(255), "SignatarioCPF" VARCHAR(255), "SignatarioWhatsApp" VARCHAR(255), "SignatarioTokenEmail" VARCHAR(255), "SignatarioTokenWhatsApp" VARCHAR(255), "SignatarioTokenLinkAssinatura" TEXT, "SignatarioLinkAssinatura" TEXT, "SignatarioQualificacao" VARCHAR(255), "SignatarioSituacaoAssinatura" VARCHAR(255), "SignatarioDataAssinatura" VARCHAR(255), "SignatarioQuantidadeDeAcessosNoLinkDeAssinatura" VARCHAR(255), "SignatarioMensagemSobreVisualizacaoDoLinkDeAssinatura" VARCHAR(255), "SignatarioIp" TEXT, "SignatarioDispositivo" TEXT, "SignatarioGeolocalizacao" TEXT, "SignatarioAutenticadoVia" VARCHAR(255), "SignatarioSelfieBase64" TEXT, "SignatarioAssinaturaEscritaBase64" TEXT, "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "AssinaturaId" INTEGER REFERENCES "Assinaturas" ("AssinaturaId") ON DELETE SET NULL ON UPDATE CASCADE, PRIMARY KEY ("SignatarioId"));
CREATE TABLE IF NOT EXISTS "SignatarioHistoricos" ("SignatarioHistoricoId"  SERIAL , "SignatarioHistoricoStatus" VARCHAR(255), "SignatarioHistoricoIp" TEXT, "SignatarioHistoricoDispositivo" TEXT, "SignatarioHistoricoAcao" VARCHAR(255), "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL, "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL, "SignatarioId" INTEGER REFERENCES "AssinaturaSignatarios" ("SignatarioId") ON DELETE SET NULL ON UPDATE CASCADE, PRIMARY KEY ("SignatarioHistoricoId"));
CREATE TABLE firma_assineai_identidade (
    identidade TEXT PRIMARY KEY,
    empresa_id INTEGER NOT NULL REFERENCES "Empresas"("EmpresaId")
);
CREATE TABLE firma_assineai_controle (
    assinatura_id INTEGER PRIMARY KEY REFERENCES "Assinaturas"("AssinaturaId"),
    criador TEXT NOT NULL,
    empresa_id INTEGER NOT NULL REFERENCES "Empresas"("EmpresaId"),
    excluido_em TIMESTAMPTZ,
    certificado_em TIMESTAMPTZ
);
CREATE INDEX firma_assineai_criador ON firma_assineai_controle(criador);
CREATE TABLE firma_assineai_codigo (
    signatario_id INTEGER NOT NULL REFERENCES "AssinaturaSignatarios"("SignatarioId"),
    canal VARCHAR(20) NOT NULL CHECK(canal IN ('email','whatsapp')),
    resumo VARCHAR(64) NOT NULL,
    enviado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    verificado BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(signatario_id,canal)
);
CREATE TABLE firma_assineai_marca (
    signatario_id INTEGER PRIMARY KEY REFERENCES "AssinaturaSignatarios"("SignatarioId"),
    rubrica BYTEA
);
