ALTER TABLE campo_assinatura DROP CONSTRAINT campo_assinatura_tipo_check;
ALTER TABLE campo_assinatura ADD CONSTRAINT campo_assinatura_tipo_check CHECK(tipo IN ('ASSINATURA','RUBRICA','SELFIE','TEXTO','DATA','SELECAO','NUMERO'));
ALTER TABLE campo_assinatura ADD COLUMN nome VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE campo_assinatura ADD COLUMN obrigatorio BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE campo_assinatura ADD COLUMN valor VARCHAR(2000);
