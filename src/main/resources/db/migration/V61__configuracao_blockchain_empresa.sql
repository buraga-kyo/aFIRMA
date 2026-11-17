CREATE TABLE firma_blockchain_conexao (
    empresa_id bigint PRIMARY KEY REFERENCES "Empresas"("EmpresaId"),
    chave_id varchar(64) NOT NULL,
    conteudo bytea NOT NULL,
    atualizado_em timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE firma_blockchain_config_auditoria (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES "Empresas"("EmpresaId"),
    responsavel text NOT NULL,
    acao varchar(40) NOT NULL,
    criado_em timestamptz NOT NULL DEFAULT now()
);
