CREATE OR REPLACE FUNCTION chat_registrar_evento_mensagem() RETURNS TRIGGER AS $$
DECLARE nome_evento TEXT;
BEGIN
    nome_evento := CASE WHEN NEW.sentido = 'ENTRADA' THEN 'mensagem.recebida' ELSE 'mensagem.enviada' END;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT webhook.id,NEW.empresa_id,nome_evento,
        jsonb_build_object('mensagemId',NEW.id,'atendimentoId',NEW.atendimento_id,
            'canalId',NEW.canal_id,'sentido',NEW.sentido,'estado',NEW.estado,
            'texto',NEW.texto,'criadaEm',NEW.criado_em)
    FROM chat_webhook webhook WHERE webhook.empresa_id=NEW.empresa_id AND webhook.ativo
        AND (cardinality(webhook.eventos)=0 OR webhook.eventos @> ARRAY[nome_evento])
        AND (webhook.todos_canais OR EXISTS (SELECT 1 FROM chat_webhook_canal vinculo
            WHERE vinculo.webhook_id=webhook.id AND vinculo.canal_id=NEW.canal_id));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION chat_registrar_evento_atendimento() RETURNS TRIGGER AS $$
DECLARE nome_evento TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN nome_evento := 'atendimento.criado';
    ELSIF NEW.estado = OLD.estado THEN RETURN NEW;
    ELSE nome_evento := CASE WHEN NEW.estado='FINALIZADO' THEN 'atendimento.finalizado'
        WHEN NEW.estado='PENDENTE' THEN 'atendimento.pendente' ELSE 'atendimento.assumido' END;
    END IF;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT webhook.id,NEW.empresa_id,nome_evento,
        jsonb_build_object('atendimentoId',NEW.id,'canalId',NEW.canal_id,
            'contatoId',NEW.contato_id,'estado',NEW.estado,'setorId',NEW.setor_id,
            'atendenteId',NEW.atendente_id,'atualizadoEm',NEW.atualizado_em)
    FROM chat_webhook webhook WHERE webhook.empresa_id=NEW.empresa_id AND webhook.ativo
        AND (cardinality(webhook.eventos)=0 OR webhook.eventos @> ARRAY[nome_evento])
        AND (webhook.todos_canais OR EXISTS (SELECT 1 FROM chat_webhook_canal vinculo
            WHERE vinculo.webhook_id=webhook.id AND vinculo.canal_id=NEW.canal_id));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION chat_registrar_evento_canal() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.conexao = OLD.conexao THEN RETURN NEW; END IF;
    INSERT INTO chat_webhook_entrega(webhook_id,empresa_id,evento,dados)
    SELECT webhook.id,NEW.empresa_id,'canal.estado',
        jsonb_build_object('canalId',NEW.id,'estado',NEW.conexao,'atualizadoEm',NEW.atualizado_em)
    FROM chat_webhook webhook WHERE webhook.empresa_id=NEW.empresa_id AND webhook.ativo
        AND (cardinality(webhook.eventos)=0 OR webhook.eventos @> ARRAY['canal.estado'])
        AND (webhook.todos_canais OR EXISTS (SELECT 1 FROM chat_webhook_canal vinculo
            WHERE vinculo.webhook_id=webhook.id AND vinculo.canal_id=NEW.id));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
