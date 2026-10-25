ALTER TABLE envelope_assinatura ADD COLUMN excluido_em TIMESTAMPTZ;
CREATE INDEX envelope_assinatura_criador_ativo ON envelope_assinatura (criador, criado_em DESC) WHERE excluido_em IS NULL;
