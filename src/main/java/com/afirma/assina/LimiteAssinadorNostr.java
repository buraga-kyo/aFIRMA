package com.afirma.assina;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LimiteAssinadorNostr {
  private final JdbcTemplate banco;

  public LimiteAssinadorNostr(JdbcTemplate banco) {
    this.banco = banco;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reservar(long empresa, String referencia) {
    long hora = Instant.now().getEpochSecond() / 3600;
    var reservas =
        banco.queryForList(
            """
            INSERT INTO firma_nostr_limite(empresa_id,referencia,hora,tentativas) VALUES(?,?,?,1)
            ON CONFLICT(empresa_id,referencia,hora) DO UPDATE SET tentativas=firma_nostr_limite.tentativas+1
            WHERE firma_nostr_limite.tentativas<100 RETURNING tentativas
            """,
            empresa,
            referencia,
            hora);
    if (reservas.isEmpty())
      throw new IllegalStateException("Limite de pedidos ao assinador Nostr atingido nesta hora");
    banco.update("DELETE FROM firma_nostr_limite WHERE hora<?", hora - 48);
  }
}
