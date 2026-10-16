CREATE TABLE assinatura_blockchain_pendente (
    identificador UUID PRIMARY KEY,
    carteira VARCHAR(42) NOT NULL,
    resumo_pdf VARCHAR(66) NOT NULL,
    pdf_preparado BYTEA NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX assinatura_blockchain_pendente_expira_em
    ON assinatura_blockchain_pendente (expira_em);
