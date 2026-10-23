ALTER TABLE chat_automacao_execucao ADD COLUMN indice_acao INTEGER NOT NULL DEFAULT 0;
ALTER TABLE chat_mensagem ADD COLUMN automacao_execucao_id BIGINT
    REFERENCES chat_automacao_execucao(id);
ALTER TABLE chat_mensagem ADD COLUMN automacao_acao_indice INTEGER;
CREATE UNIQUE INDEX chat_mensagem_automacao_acao_unica
    ON chat_mensagem(automacao_execucao_id,automacao_acao_indice)
    WHERE automacao_execucao_id IS NOT NULL;
