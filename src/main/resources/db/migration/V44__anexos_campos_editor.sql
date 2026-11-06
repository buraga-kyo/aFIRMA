ALTER TABLE campo_assinatura DROP CONSTRAINT campo_assinatura_tipo_check;
ALTER TABLE campo_assinatura ADD CONSTRAINT campo_assinatura_tipo_check CHECK(tipo IN ('ASSINATURA','RUBRICA','SELFIE','TEXTO','DATA','SELECAO','NUMERO','ANEXO'));
ALTER TABLE campo_assinatura ADD COLUMN anexo BYTEA;
ALTER TABLE campo_assinatura ADD COLUMN anexo_nome VARCHAR(120);
ALTER TABLE campo_assinatura ADD COLUMN anexo_tipo VARCHAR(80);
ALTER TABLE campo_assinatura ADD COLUMN anexo_sha256 CHAR(64);
