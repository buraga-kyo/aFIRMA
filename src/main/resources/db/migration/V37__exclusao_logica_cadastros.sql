-- Diferencia exclusão de desativação sem apagar cadastros usados no histórico.
CREATE TABLE firma_autentica_exclusao (
 cliente_id INTEGER NOT NULL REFERENCES autentica."Cliente"(id),
 tabela TEXT NOT NULL,registro_id INTEGER NOT NULL,
 usuario_id INTEGER NOT NULL,excluido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(cliente_id,tabela,registro_id)
);
