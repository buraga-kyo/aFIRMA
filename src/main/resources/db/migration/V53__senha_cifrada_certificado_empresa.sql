ALTER TABLE certificado_empresa
    ADD COLUMN senha_protegida BYTEA,
    ADD COLUMN formato INTEGER NOT NULL DEFAULT 1 CHECK (formato IN (1,2)),
    ADD CONSTRAINT certificado_empresa_senha_formato CHECK (formato=1 OR senha_protegida IS NOT NULL);
