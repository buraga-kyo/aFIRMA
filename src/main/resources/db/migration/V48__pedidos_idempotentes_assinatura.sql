CREATE TABLE pedido_assinatura (
    criador VARCHAR(255) NOT NULL,
    requisicao_id UUID NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    resumo_pedido VARCHAR(64) NOT NULL,
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id),
    chave_resposta VARCHAR(64) NOT NULL,
    resposta_cifrada BYTEA,
    criado_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (criador, requisicao_id)
);
