CREATE TABLE firma_nostr_limite (
 empresa_id BIGINT NOT NULL REFERENCES "Empresas"("EmpresaId"),
 referencia VARCHAR(80) NOT NULL,
 hora BIGINT NOT NULL,
 tentativas INTEGER NOT NULL CHECK(tentativas BETWEEN 1 AND 100),
 PRIMARY KEY(empresa_id,referencia,hora)
);
