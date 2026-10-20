ALTER TABLE assinatura_blockchain_pendente
ADD COLUMN nome_empresa VARCHAR(120) NOT NULL DEFAULT 'aFIRMA';

ALTER TABLE assinatura_blockchain_pendente
ALTER COLUMN nome_empresa DROP DEFAULT;
