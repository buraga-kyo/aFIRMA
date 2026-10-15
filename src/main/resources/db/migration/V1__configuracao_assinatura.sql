CREATE TABLE configuracao_assinatura (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    tamanho_maximo_pdf_bytes BIGINT NOT NULL
        CHECK (tamanho_maximo_pdf_bytes BETWEEN 1 AND 20971520)
);

INSERT INTO configuracao_assinatura (id, tamanho_maximo_pdf_bytes)
VALUES (1, 10485760);
