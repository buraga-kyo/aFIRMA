ALTER TABLE signatario_envelope
    ADD COLUMN resumo_documento_aceito VARCHAR(64),
    ADD COLUMN resumo_envio VARCHAR(64),
    ADD COLUMN texto_aceite VARCHAR(1000);
