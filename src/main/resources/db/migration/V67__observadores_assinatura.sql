CREATE TABLE observador_assinatura (
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id) ON DELETE CASCADE,
    canal VARCHAR(8) NOT NULL CHECK (canal IN ('email', 'whatsapp')),
    contato VARCHAR(254) NOT NULL,
    PRIMARY KEY (envelope_id, canal, contato)
);

CREATE TABLE notificacao_observador_assinatura (
    envelope_id UUID NOT NULL,
    sequencia BIGINT NOT NULL,
    canal VARCHAR(8) NOT NULL,
    contato VARCHAR(254) NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,
    tentar_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    reserva UUID,
    encerrada_em TIMESTAMPTZ,
    enviada_em TIMESTAMPTZ,
    PRIMARY KEY (envelope_id, canal, contato, sequencia),
    FOREIGN KEY (envelope_id, canal, contato)
        REFERENCES observador_assinatura(envelope_id, canal, contato) ON DELETE CASCADE,
    FOREIGN KEY (envelope_id, sequencia) REFERENCES evento_assinatura(envelope_id, sequencia)
);

CREATE INDEX notificacao_observador_pendente
    ON notificacao_observador_assinatura(tentar_em) WHERE encerrada_em IS NULL;
