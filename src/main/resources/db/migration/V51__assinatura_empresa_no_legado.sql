ALTER TABLE firma_assineai_controle ADD COLUMN assinatura_automatica BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE "AssinaturaDocumentos"
    ADD COLUMN "DocumentoBaseEmpresa" BYTEA,
    ADD COLUMN "DocumentoEmpresaAssinouEm" TIMESTAMPTZ,
    ADD COLUMN "DocumentoEmpresaTitular" TEXT;
