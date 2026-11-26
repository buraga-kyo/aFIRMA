CREATE TABLE firma_email_template (
    id bigserial PRIMARY KEY,
    empresa_id bigint NOT NULL REFERENCES "Empresas"("EmpresaId"),
    etapa text NOT NULL,
    titulo varchar(180) NOT NULL,
    assunto varchar(180) NOT NULL,
    html text NOT NULL,
    documento jsonb,
    padrao boolean NOT NULL DEFAULT false,
    ativo boolean NOT NULL DEFAULT false,
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    excluido_em timestamptz
);

CREATE UNIQUE INDEX firma_email_template_padrao
    ON firma_email_template (empresa_id, etapa) WHERE padrao;
CREATE UNIQUE INDEX firma_email_template_ativo
    ON firma_email_template (empresa_id, etapa) WHERE ativo;
CREATE INDEX firma_email_template_empresa ON firma_email_template (empresa_id, id);

ALTER TABLE firma_email_assinatura_publicacao
    ADD COLUMN template_id bigint REFERENCES firma_email_template (id);

WITH ultima_publicacao AS (
    SELECT DISTINCT ON (empresa_id, etapa) *
    FROM firma_email_assinatura_publicacao
    ORDER BY empresa_id, etapa, id DESC
)
INSERT INTO firma_email_template (empresa_id, etapa, titulo, assunto, html, documento, padrao, ativo, atualizado_em)
SELECT coalesce(r.empresa_id, p.empresa_id), coalesce(r.etapa, p.etapa), 'Código de verificação',
       coalesce(r.assunto, p.assunto), coalesce(r.html, p.html),
       CASE WHEN r.empresa_id IS NULL THEN p.documento ELSE r.documento END,
       true, true, coalesce(p.publicado_em, now())
FROM firma_email_assinatura_rascunho r
FULL JOIN ultima_publicacao p ON r.empresa_id = p.empresa_id AND r.etapa = p.etapa;

UPDATE firma_email_assinatura_publicacao p SET template_id = t.id
FROM firma_email_template t
WHERE p.empresa_id = t.empresa_id AND p.etapa = t.etapa AND t.padrao;

ALTER TABLE firma_email_assinatura_publicacao ALTER COLUMN template_id SET NOT NULL;
CREATE INDEX firma_email_publicacao_template ON firma_email_assinatura_publicacao (template_id, id DESC);
