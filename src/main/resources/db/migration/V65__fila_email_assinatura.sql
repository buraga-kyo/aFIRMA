CREATE TABLE firma_email_assinatura_fila (
    id bigserial PRIMARY KEY,
    chave text NOT NULL UNIQUE,
    empresa_id bigint NOT NULL REFERENCES "Empresas"("EmpresaId"),
    envelope_id uuid NOT NULL REFERENCES envelope_assinatura(id),
    signatario_id uuid REFERENCES signatario_envelope(id),
    etapa text NOT NULL,
    destinatario varchar(254) NOT NULL,
    dados jsonb NOT NULL,
    estado text NOT NULL DEFAULT 'pendente'
        CHECK (estado IN ('pendente', 'enviando', 'enviado', 'descartado', 'falhou')),
    tentativas integer NOT NULL DEFAULT 0,
    tentar_em timestamptz NOT NULL DEFAULT now(),
    reserva uuid,
    reservado_ate timestamptz,
    atualizado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX firma_email_assinatura_fila_pendentes
    ON firma_email_assinatura_fila (tentar_em, id) WHERE estado IN ('pendente', 'enviando');
CREATE INDEX evento_assinatura_email_tipo_data ON evento_assinatura (tipo, ocorrido_em)
    WHERE tipo IN ('ACEITE_REGISTRADO', 'ENVELOPE_FINALIZADO', 'CONVITE_RECUSADO', 'ENVELOPE_CANCELADO');
CREATE INDEX envelope_assinatura_email_prazo ON envelope_assinatura (expira_em)
    WHERE finalizado_em IS NULL AND cancelado_em IS NULL AND excluido_em IS NULL;
