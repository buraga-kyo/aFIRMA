-- Evolução separada da V28, que já foi aplicada em instalações existentes.
ALTER TABLE firma_autentica_envio ADD COLUMN IF NOT EXISTS requisicao TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS firma_autentica_envio_requisicao
  ON firma_autentica_envio(cliente_id, requisicao);
