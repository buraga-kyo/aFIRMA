ALTER TABLE chat_mensagem ALTER COLUMN atendimento_id DROP NOT NULL;
ALTER TABLE chat_mensagem ADD COLUMN contato_id BIGINT;
ALTER TABLE chat_mensagem ADD COLUMN campanha_envio_id BIGINT UNIQUE REFERENCES chat_campanha_envio(id);
ALTER TABLE chat_mensagem ADD CONSTRAINT chat_mensagem_contato_empresa_fk
    FOREIGN KEY (contato_id, empresa_id) REFERENCES chat_contato(id, empresa_id);
ALTER TABLE chat_mensagem ADD CONSTRAINT chat_mensagem_destino_check
    CHECK (atendimento_id IS NOT NULL OR campanha_envio_id IS NOT NULL);
ALTER TABLE chat_atendimento ADD COLUMN primeira_resposta_em TIMESTAMPTZ;
ALTER TABLE chat_atendimento ALTER COLUMN protocolo SET DEFAULT
    ('F' || to_char(NOW(),'YYMMDD') || lpad(nextval('chat_protocolo_sequencia')::text,8,'0'));
UPDATE chat_atendimento SET protocolo=DEFAULT WHERE protocolo IS NULL;
ALTER TABLE chat_atendimento ALTER COLUMN protocolo SET NOT NULL;
CREATE INDEX chat_atendimento_contato_historico ON chat_atendimento(empresa_id, contato_id, iniciado_em DESC);
CREATE INDEX chat_mensagem_contato_historico ON chat_mensagem(empresa_id, contato_id, criado_em DESC);

CREATE OR REPLACE FUNCTION chat_calcular_tempos_atendimento() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.estado='FINALIZADO' AND OLD.estado<>'FINALIZADO' THEN
        NEW.finalizado_em := COALESCE(NEW.finalizado_em,NOW());
        NEW.duracao_segundos := GREATEST(0,EXTRACT(EPOCH FROM (NEW.finalizado_em-NEW.iniciado_em))::integer);
        NEW.espera_segundos := GREATEST(0,EXTRACT(EPOCH FROM
            (COALESCE(NEW.primeira_resposta_em,NEW.finalizado_em)-NEW.iniciado_em))::integer);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_atendimento_tempos BEFORE UPDATE OF estado ON chat_atendimento
    FOR EACH ROW EXECUTE FUNCTION chat_calcular_tempos_atendimento();
