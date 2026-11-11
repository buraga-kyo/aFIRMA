ALTER TABLE "Empresas"
    ADD COLUMN "EmpresaAssinaturaAutomatica" BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN "EmpresaAutorizacaoCertificado" TEXT,
    ADD COLUMN "EmpresaAutorizacaoCertificadoEm" TIMESTAMPTZ;
ALTER TABLE envelope_assinatura
    ADD COLUMN configuracao_registro_assinatura TEXT,
    ADD COLUMN empresa_assinou_em TIMESTAMPTZ,
    ADD COLUMN empresa_titular_certificado TEXT,
    ADD COLUMN empresa_serie_certificado VARCHAR(255);
