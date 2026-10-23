ALTER TABLE chat_plano ADD COLUMN renovacao_dias INTEGER NOT NULL DEFAULT 30 CHECK (renovacao_dias > 0);
ALTER TABLE chat_plano ADD COLUMN limite_envios_dia INTEGER CHECK (limite_envios_dia IS NULL OR limite_envios_dia >= 0);
ALTER TABLE chat_plano ADD COLUMN limite_canais INTEGER CHECK (limite_canais IS NULL OR limite_canais >= 0);
ALTER TABLE chat_plano ADD COLUMN limite_atendentes INTEGER CHECK (limite_atendentes IS NULL OR limite_atendentes >= 0);
ALTER TABLE chat_plano ADD COLUMN permite_integracao BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE chat_plano ADD COLUMN permite_webhook BOOLEAN NOT NULL DEFAULT TRUE;

CREATE OR REPLACE FUNCTION chat_registrar_evento_mensagem() RETURNS TRIGGER AS $$
DECLARE nome_evento TEXT;
BEGIN
    nome_evento := CASE WHEN NEW.sentido = 'ENTRADA' THEN 'mensagem.recebida' ELSE 'mensagem.enviada' END;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT id,NEW.empresa_id,nome_evento,
        jsonb_build_object('mensagemId',NEW.id,'atendimentoId',NEW.atendimento_id,
            'canalId',NEW.canal_id,'sentido',NEW.sentido,'estado',NEW.estado,
            'texto',NEW.texto,'criadaEm',NEW.criado_em)
    FROM chat_webhook WHERE empresa_id=NEW.empresa_id AND ativo
        AND (cardinality(eventos)=0 OR eventos @> ARRAY[nome_evento]);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_mensagem_evento AFTER INSERT ON chat_mensagem
    FOR EACH ROW EXECUTE FUNCTION chat_registrar_evento_mensagem();

CREATE OR REPLACE FUNCTION chat_registrar_evento_atendimento() RETURNS TRIGGER AS $$
DECLARE nome_evento TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN nome_evento := 'atendimento.criado';
    ELSIF NEW.estado = OLD.estado THEN RETURN NEW;
    ELSE nome_evento := CASE WHEN NEW.estado='FINALIZADO' THEN 'atendimento.finalizado'
        WHEN NEW.estado='PENDENTE' THEN 'atendimento.pendente' ELSE 'atendimento.assumido' END;
    END IF;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT id,NEW.empresa_id,nome_evento,
        jsonb_build_object('atendimentoId',NEW.id,'canalId',NEW.canal_id,
            'contatoId',NEW.contato_id,'estado',NEW.estado,'setorId',NEW.setor_id,
            'atendenteId',NEW.atendente_id,'atualizadoEm',NEW.atualizado_em)
    FROM chat_webhook WHERE empresa_id=NEW.empresa_id AND ativo
        AND (cardinality(eventos)=0 OR eventos @> ARRAY[nome_evento]);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_atendimento_evento AFTER INSERT OR UPDATE OF estado ON chat_atendimento
    FOR EACH ROW EXECUTE FUNCTION chat_registrar_evento_atendimento();

CREATE OR REPLACE FUNCTION chat_registrar_evento_canal() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.conexao = OLD.conexao THEN RETURN NEW; END IF;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT id,NEW.empresa_id,'canal.estado',
        jsonb_build_object('canalId',NEW.id,'estado',NEW.conexao,'atualizadoEm',NEW.atualizado_em)
    FROM chat_webhook WHERE empresa_id=NEW.empresa_id AND ativo
        AND (cardinality(eventos)=0 OR eventos @> ARRAY['canal.estado']);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_canal_evento AFTER UPDATE OF conexao ON chat_canal
    FOR EACH ROW EXECUTE FUNCTION chat_registrar_evento_canal();
