ALTER TABLE empresa ADD COLUMN provedor_mensagens VARCHAR(30);
ALTER TABLE empresa ADD COLUMN identificador_aplicativo_oficial VARCHAR(100);
ALTER TABLE empresa ADD COLUMN referencia_segredo_oficial VARCHAR(100);

ALTER TABLE chat_atendimento ADD COLUMN tempo_resposta_cliente_segundos INTEGER;
ALTER TABLE chat_atendimento ADD COLUMN tempo_resposta_atendente_segundos INTEGER;
ALTER TABLE chat_atendimento ADD CONSTRAINT chat_atendimento_resposta_cliente_check
    CHECK (tempo_resposta_cliente_segundos IS NULL OR tempo_resposta_cliente_segundos >= 0);
ALTER TABLE chat_atendimento ADD CONSTRAINT chat_atendimento_resposta_atendente_check
    CHECK (tempo_resposta_atendente_segundos IS NULL OR tempo_resposta_atendente_segundos >= 0);

ALTER TABLE chat_mensagem ADD COLUMN remetente VARCHAR(100);
ALTER TABLE chat_mensagem ADD COLUMN destinatario VARCHAR(100);
ALTER TABLE chat_mensagem ADD COLUMN chave_externa JSONB;
ALTER TABLE chat_mensagem ADD COLUMN conteudo_original JSONB;
ALTER TABLE chat_mensagem ADD COLUMN ocorrida_em TIMESTAMPTZ;
ALTER TABLE chat_mensagem ADD COLUMN gerada_fora_sistema BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_mensagem ADD COLUMN enviada_por_bot BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_mensagem ADD COLUMN enviada_por_api BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_mensagem ADD COLUMN enviada_por_atendente BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_modelo_mensagem ADD COLUMN status_uso BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE chat_modelo_mensagem ADD COLUMN tipo_midia VARCHAR(30);
ALTER TABLE chat_modelo_mensagem ADD COLUMN chave_midia TEXT;
ALTER TABLE chat_campanha ADD COLUMN coluna_destinatario VARCHAR(120) NOT NULL DEFAULT 'telefone';
ALTER TABLE chat_campanha ADD COLUMN anexo_tipo VARCHAR(100);
ALTER TABLE chat_campanha ADD COLUMN anexo_nome VARCHAR(255);
ALTER TABLE chat_campanha ADD COLUMN ultimo_envio_em TIMESTAMPTZ;

ALTER TABLE chat_webhook ADD COLUMN todos_canais BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE chat_webhook_canal (
    webhook_id UUID NOT NULL,
    canal_id UUID NOT NULL,
    empresa_id BIGINT NOT NULL REFERENCES empresa(id),
    PRIMARY KEY (webhook_id, canal_id),
    FOREIGN KEY (webhook_id, empresa_id) REFERENCES chat_webhook(id, empresa_id) ON DELETE CASCADE,
    FOREIGN KEY (canal_id, empresa_id) REFERENCES chat_canal(id, empresa_id)
);
ALTER TABLE chat_webhook_entrega ADD COLUMN enviada_em TIMESTAMPTZ;
ALTER TABLE chat_webhook_entrega ADD COLUMN respondida_em TIMESTAMPTZ;
ALTER TABLE chat_log_api ADD COLUMN metodo VARCHAR(10);
ALTER TABLE chat_log_api ADD COLUMN endereco TEXT;
ALTER TABLE chat_log_api ADD COLUMN codigo_resposta INTEGER;
ALTER TABLE chat_log_api ADD COLUMN duracao_milissegundos INTEGER;
ALTER TABLE chat_log_api ADD COLUMN endereco_ip VARCHAR(45);
ALTER TABLE chat_log_api ADD COLUMN dados_recebidos JSONB;
ALTER TABLE chat_log_api ADD COLUMN dados_respondidos JSONB;
ALTER TABLE chat_chave_integracao ADD COLUMN criada_por VARCHAR(200);
