CREATE TABLE envelope_assinatura (
    id UUID PRIMARY KEY,
    nome_empresa VARCHAR(120) NOT NULL,
    nome_documento VARCHAR(255) NOT NULL,
    criador VARCHAR(255) NOT NULL,
    pdf_original BYTEA NOT NULL,
    pdf_final BYTEA,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    finalizado_em TIMESTAMPTZ
);

CREATE TABLE signatario_envelope (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id),
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    resumo_convite CHAR(64) NOT NULL UNIQUE,
    assinatura BYTEA,
    rubrica BYTEA,
    selfie BYTEA,
    assinado_em TIMESTAMPTZ,
    endereco_ip VARCHAR(64)
);

CREATE INDEX signatario_envelope_envelope_id ON signatario_envelope(envelope_id);

CREATE TABLE campo_assinatura (
    id UUID PRIMARY KEY,
    signatario_id UUID NOT NULL REFERENCES signatario_envelope(id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ASSINATURA', 'RUBRICA', 'SELFIE')),
    pagina INTEGER NOT NULL CHECK (pagina > 0),
    posicao_x DOUBLE PRECISION NOT NULL CHECK (posicao_x >= 0 AND posicao_x <= 1),
    posicao_y DOUBLE PRECISION NOT NULL CHECK (posicao_y >= 0 AND posicao_y <= 1),
    largura DOUBLE PRECISION NOT NULL CHECK (largura > 0 AND largura <= 1),
    altura DOUBLE PRECISION NOT NULL CHECK (altura > 0 AND altura <= 1),
    CHECK (posicao_x + largura <= 1),
    CHECK (posicao_y + altura <= 1)
);

CREATE INDEX campo_assinatura_signatario_id ON campo_assinatura(signatario_id);
