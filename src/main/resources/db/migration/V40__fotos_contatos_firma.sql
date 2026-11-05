CREATE TABLE firma_contato_foto (
 contato_id integer PRIMARY KEY REFERENCES firma."Contato"(id) ON DELETE CASCADE,
 verificada_em timestamptz,
 proxima_verificacao timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX firma_contato_foto_pendente ON firma_contato_foto(proxima_verificacao);
