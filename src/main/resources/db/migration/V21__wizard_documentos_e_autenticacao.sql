ALTER TABLE documento_assinatura ADD COLUMN pagina_inicio INTEGER NOT NULL DEFAULT 1;
ALTER TABLE documento_assinatura ADD COLUMN pagina_fim INTEGER NOT NULL DEFAULT 1;
CREATE INDEX documento_assinatura_envelope ON documento_assinatura(envelope_id);
CREATE TABLE desafio_assinatura (
    id VARCHAR(64) PRIMARY KEY,
    resumo VARCHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    enviado_em TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    verificado BOOLEAN NOT NULL DEFAULT FALSE
);
