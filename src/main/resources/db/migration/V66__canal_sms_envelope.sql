ALTER TABLE desafio_assinatura DROP CONSTRAINT desafio_canal_valido;
ALTER TABLE desafio_assinatura ADD CONSTRAINT desafio_canal_valido CHECK (canal IN ('email', 'whatsapp', 'sms'));
