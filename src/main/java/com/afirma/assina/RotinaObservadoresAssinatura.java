package com.afirma.assina;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RotinaObservadoresAssinatura {
  private static final Logger REGISTRO = LoggerFactory.getLogger(RotinaObservadoresAssinatura.class);
  private static final int LIMITE_TENTATIVAS = 5;
  private final JdbcTemplate banco;
  private final ServicoEntregaAssinatura entrega;

  public RotinaObservadoresAssinatura(JdbcTemplate banco, ServicoEntregaAssinatura entrega) {
    this.banco = banco;
    this.entrega = entrega;
  }

  @Scheduled(fixedDelayString = "${APP_ASSINATURAS_OBSERVADORES_INTERVALO_MS:1000}")
  public void entregar() {
    if (!entrega.disponivel()) return;
    banco.update("""
        UPDATE notificacao_observador_assinatura SET encerrada_em=now(), reserva=NULL
        WHERE encerrada_em IS NULL AND tentativas >= ? AND tentar_em <= now()
        """, LIMITE_TENTATIVAS);

    UUID reserva = UUID.randomUUID();
    // A reserva evita envio simultâneo e deixa a fila retomar após uma interrupção.
    // Cada contato recebe o histórico na ordem, inclusive quando uma entrega precisa ser repetida.
    var notificacoes = banco.query("""
        WITH proxima AS (
          SELECT fila.envelope_id, fila.sequencia, fila.canal, fila.contato
          FROM notificacao_observador_assinatura fila
          WHERE fila.encerrada_em IS NULL AND fila.tentar_em <= now() AND fila.tentativas < ?
            AND (fila.canal='email' OR ?)
            AND NOT EXISTS (
              SELECT 1 FROM notificacao_observador_assinatura anterior
              WHERE anterior.envelope_id=fila.envelope_id AND anterior.canal=fila.canal
                AND anterior.contato=fila.contato AND anterior.sequencia < fila.sequencia
                AND anterior.encerrada_em IS NULL
            )
          ORDER BY fila.tentar_em, fila.envelope_id, fila.sequencia
          LIMIT 1 FOR UPDATE SKIP LOCKED
        ), reservada AS (
          UPDATE notificacao_observador_assinatura fila
          SET reserva=?, tentativas=fila.tentativas+1, tentar_em=now()+interval '5 minutes'
          FROM proxima
          WHERE (fila.envelope_id, fila.sequencia, fila.canal, fila.contato) =
                (proxima.envelope_id, proxima.sequencia, proxima.canal, proxima.contato)
          RETURNING fila.*
        )
        SELECT fila.envelope_id, fila.sequencia, fila.canal, fila.contato, fila.tentativas,
               evento.tipo, COALESCE(NULLIF(envelope.nome, ''), envelope.nome_documento), participante.nome
        FROM reservada fila
        JOIN evento_assinatura evento ON (evento.envelope_id, evento.sequencia) = (fila.envelope_id, fila.sequencia)
        JOIN envelope_assinatura envelope ON envelope.id=fila.envelope_id
        LEFT JOIN signatario_envelope participante ON participante.id=evento.participante_id
        """, (linha, indice) -> new Notificacao(linha.getObject(1, UUID.class), linha.getLong(2),
            linha.getString(3), linha.getString(4), linha.getInt(5), linha.getString(6),
            linha.getString(7), linha.getString(8)), LIMITE_TENTATIVAS, entrega.whatsappDisponivel(), reserva);

    for (Notificacao notificacao : notificacoes) entregar(notificacao, reserva);
  }

  private void entregar(Notificacao notificacao, UUID reserva) {
    boolean enviada = false;
    try {
      String mensagem = ObservadorAssinatura.mensagem(notificacao.evento(), notificacao.participante());
      entrega.enviar(notificacao.canal(), notificacao.contato(), "observador",
          "Assinatura: " + notificacao.titulo() + "\n" + mensagem);
      enviada = true;
    } catch (RuntimeException falha) {
      REGISTRO.warn("Falha na atualização do envelope {}, evento {}, canal {}, tentativa {}: {}",
          notificacao.envelope(), notificacao.sequencia(), notificacao.canal(), notificacao.tentativas(),
          falha.getClass().getSimpleName());
    }
    banco.update("""
        UPDATE notificacao_observador_assinatura
        SET reserva=NULL, encerrada_em=CASE WHEN ? THEN now() END,
            enviada_em=CASE WHEN ? THEN now() END, tentar_em=now()+(? * interval '1 minute')
        WHERE (envelope_id, sequencia, canal, contato, reserva) = (?,?,?,?,?)
        """, enviada || notificacao.tentativas() >= LIMITE_TENTATIVAS, enviada, notificacao.tentativas(),
        notificacao.envelope(), notificacao.sequencia(), notificacao.canal(), notificacao.contato(), reserva);
  }

  private record Notificacao(UUID envelope, long sequencia, String canal, String contato, int tentativas,
      String evento, String titulo, String participante) {}
}
