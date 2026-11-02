ALTER TABLE firma_autentica_envio ADD COLUMN interativa JSONB;
ALTER TABLE firma_autentica_envio ADD COLUMN resposta_id INTEGER REFERENCES autentica."Mensagem"(id);
ALTER TABLE firma_autentica_envio ADD COLUMN encerrar BOOLEAN NOT NULL DEFAULT false;
