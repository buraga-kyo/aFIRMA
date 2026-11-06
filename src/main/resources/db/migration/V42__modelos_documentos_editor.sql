CREATE TABLE firma_modelo_documento (
  id UUID PRIMARY KEY,
  proprietario VARCHAR(255) NOT NULL,
  nome VARCHAR(120) NOT NULL,
  versao INTEGER NOT NULL CHECK (versao > 0),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX firma_modelo_documento_proprietario ON firma_modelo_documento(proprietario, atualizado_em DESC);
CREATE TABLE firma_modelo_documento_versao (
  modelo_id UUID NOT NULL REFERENCES firma_modelo_documento(id),
  versao INTEGER NOT NULL,
  nome VARCHAR(120) NOT NULL,
  pdf BYTEA NOT NULL,
  campos JSONB NOT NULL,
  sha256 CHAR(64) NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(modelo_id, versao)
);
