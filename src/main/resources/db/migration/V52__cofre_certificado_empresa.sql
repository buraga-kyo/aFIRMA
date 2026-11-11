CREATE TABLE certificado_empresa (
    empresa_id BIGINT PRIMARY KEY REFERENCES "Empresas"("EmpresaId") ON DELETE CASCADE,
    pfx_protegido BYTEA NOT NULL CHECK (octet_length(pfx_protegido) BETWEEN 1 AND 3145728),
    chave_protecao VARCHAR(64) NOT NULL,
    titular TEXT NOT NULL,
    numero_serie VARCHAR(255) NOT NULL,
    valido_ate TIMESTAMPTZ NOT NULL,
    cadastrado_em TIMESTAMPTZ NOT NULL,
    responsavel TEXT NOT NULL
);
CREATE TABLE evento_certificado_empresa (
    id UUID PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES "Empresas"("EmpresaId"),
    acao VARCHAR(30) NOT NULL,
    numero_serie VARCHAR(255),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    responsavel TEXT NOT NULL
);
