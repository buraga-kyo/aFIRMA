ALTER TABLE envelope_assinatura
    ADD COLUMN tipo_documento VARCHAR(40),
    ADD COLUMN declaracao_formalidades TEXT,
    ADD COLUMN reter_evidencias_ate TIMESTAMPTZ,
    ADD COLUMN cancelado_em TIMESTAMPTZ,
    ADD COLUMN motivo_cancelamento VARCHAR(500);
ALTER TABLE signatario_envelope
    ADD COLUMN papel VARCHAR(30),
    ADD COLUMN representado VARCHAR(254),
    ADD COLUMN fundamento_representacao VARCHAR(1000),
    ADD COLUMN autenticado_em TIMESTAMPTZ,
    ADD COLUMN autenticacao_id UUID,
    ADD COLUMN recusado_em TIMESTAMPTZ,
    ADD COLUMN motivo_recusa VARCHAR(500);

CREATE TABLE autenticacao_convite (
    signatario_id UUID PRIMARY KEY REFERENCES signatario_envelope(id),
    resumo_codigo VARCHAR(64),
    codigo_expira_em TIMESTAMPTZ,
    ultimo_envio_em TIMESTAMPTZ,
    janela_envios_em TIMESTAMPTZ NOT NULL,
    quantidade_envios INTEGER NOT NULL DEFAULT 0,
    tentativas_incorretas INTEGER NOT NULL DEFAULT 0,
    bloqueado_ate TIMESTAMPTZ
);
CREATE TABLE sessao_convite (
    id UUID PRIMARY KEY,
    signatario_id UUID NOT NULL REFERENCES signatario_envelope(id),
    resumo_segredo VARCHAR(64) NOT NULL UNIQUE,
    criada_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    revogada_em TIMESTAMPTZ,
    endereco_ip VARCHAR(64) NOT NULL,
    agente_usuario VARCHAR(512) NOT NULL
);
CREATE INDEX sessao_convite_signatario ON sessao_convite(signatario_id);

CREATE TABLE evento_assinatura (
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id),
    sequencia BIGINT NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL,
    tipo VARCHAR(60) NOT NULL,
    participante_id UUID,
    conteudo TEXT NOT NULL,
    resumo_anterior VARCHAR(64) NOT NULL,
    resumo_evento VARCHAR(64) NOT NULL,
    selo_servidor VARCHAR(64) NOT NULL,
    chave_selo VARCHAR(64) NOT NULL,
    PRIMARY KEY (envelope_id, sequencia)
);
CREATE TABLE arquivo_evidencia_assinatura (
    id UUID PRIMARY KEY,
    ordem BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id),
    participante_id UUID,
    tipo VARCHAR(40) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    mime VARCHAR(100) NOT NULL,
    resumo_sha256 VARCHAR(64) NOT NULL,
    tamanho BIGINT NOT NULL CHECK (tamanho > 0),
    dados BYTEA,
    chave_minio VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL,
    CHECK ((dados IS NULL) <> (chave_minio IS NULL))
);
CREATE INDEX arquivo_evidencia_envelope ON arquivo_evidencia_assinatura(envelope_id, criado_em);

-- A aplicação acrescenta evidências. Uma correção precisa de outro evento, sem apagar o registro anterior.
CREATE FUNCTION impedir_alteracao_evidencia_assinatura() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Evidências de assinatura não podem ser alteradas ou removidas';
END;
$$;
CREATE TRIGGER proteger_evento_assinatura BEFORE UPDATE OR DELETE ON evento_assinatura
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_evidencia_assinatura();
CREATE TRIGGER proteger_arquivo_evidencia BEFORE UPDATE OR DELETE ON arquivo_evidencia_assinatura
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_evidencia_assinatura();
CREATE TRIGGER proteger_evento_assinatura_truncate BEFORE TRUNCATE ON evento_assinatura
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_evidencia_assinatura();
CREATE TRIGGER proteger_arquivo_evidencia_truncate BEFORE TRUNCATE ON arquivo_evidencia_assinatura
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_evidencia_assinatura();
