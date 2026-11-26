CREATE TABLE observador_signatario_assinatura (
    envelope_id UUID NOT NULL,
    canal VARCHAR(8) NOT NULL,
    contato VARCHAR(254) NOT NULL,
    participante_id UUID NOT NULL REFERENCES signatario_envelope(id) ON DELETE CASCADE,
    PRIMARY KEY (envelope_id, canal, contato, participante_id),
    FOREIGN KEY (envelope_id, canal, contato)
        REFERENCES observador_assinatura(envelope_id, canal, contato) ON DELETE CASCADE
);

CREATE INDEX observador_por_participante
    ON observador_signatario_assinatura(participante_id);

-- Em envelopes antigos, só dá pra recuperar o vínculo quando há um único signatário.
WITH signatario_unico AS (
    SELECT envelope_id, (array_agg(id))[1] AS participante_id
    FROM signatario_envelope
    GROUP BY envelope_id
    HAVING count(*) = 1
)
INSERT INTO observador_signatario_assinatura(envelope_id, canal, contato, participante_id)
SELECT observador.envelope_id, observador.canal, observador.contato, signatario.participante_id
FROM observador_assinatura observador
JOIN signatario_unico signatario USING (envelope_id);

-- Preserva o histórico de entrega e encerra avisos pendentes sem destinatário vinculado.
UPDATE notificacao_observador_assinatura fila
SET encerrada_em=now(), reserva=NULL
FROM evento_assinatura evento
WHERE (evento.envelope_id, evento.sequencia)=(fila.envelope_id, fila.sequencia)
  AND fila.encerrada_em IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM observador_signatario_assinatura vinculo
      WHERE (vinculo.envelope_id, vinculo.canal, vinculo.contato)=(fila.envelope_id, fila.canal, fila.contato)
        AND (vinculo.participante_id=evento.participante_id OR evento.tipo IN (
            'ENVELOPE_CRIADO', 'ENVELOPE_FINALIZADO', 'ENVELOPE_CANCELADO'
        ))
  );
