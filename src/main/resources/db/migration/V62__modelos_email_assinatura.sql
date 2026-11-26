CREATE TABLE firma_email_assinatura_rascunho (
    empresa_id bigint NOT NULL REFERENCES "Empresas"("EmpresaId"),
    etapa text NOT NULL,
    assunto varchar(180) NOT NULL,
    html text NOT NULL,
    PRIMARY KEY (empresa_id, etapa)
);

CREATE TABLE firma_email_assinatura_publicacao (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES "Empresas"("EmpresaId"),
    etapa text NOT NULL,
    assunto varchar(180) NOT NULL,
    html text NOT NULL,
    publicado_por text NOT NULL,
    publicado_em timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX firma_email_assinatura_historico
    ON firma_email_assinatura_publicacao (empresa_id, etapa, id DESC);
