package com.afirma.assina;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** A rotina pede as fotos à ponte e guarda a data da próxima consulta de cada contato. */
@Service
public class FotosContatoFirma {
  private static final org.slf4j.Logger registroLog =
      org.slf4j.LoggerFactory.getLogger(FotosContatoFirma.class);
  private final BancoAssineai banco;
  private final ChatTransporte transporte;
  private final TransactionTemplate transacoes;
  private final boolean ativo;

  public FotosContatoFirma(
      BancoAssineai banco,
      ChatTransporte transporte,
      TransactionTemplate transacoes,
      @Value("${APP_FIRMA_ROTINAS:true}") boolean ativo) {
    this.banco = banco;
    this.transporte = transporte;
    this.transacoes = transacoes;
    this.ativo = ativo;
  }

  @Scheduled(fixedDelayString = "${APP_FIRMA_FOTOS_INTERVALO_MS:60000}", initialDelay = 60000)
  public void atualizar() {
    // Cada contato tem sua própria data de consulta. A ponte devolve a foto e a rotina agenda a
    // próxima verificação.

    if (!ativo || !transporte.configurado()) return;
    transacoes.executeWithoutResult(transacoesAtual ->
        banco.executar(
            """
            INSERT INTO firma_contato_foto(contato_id)
            SELECT co.id FROM firma."Contato" co
            WHERE EXISTS(SELECT 1 FROM firma."Canal" ca WHERE ca."ClienteId"=co."ClienteId"
              AND ca."CanalProvedorAPI"='Baileys' AND COALESCE(ca."CanalStatusUso",true))
            ON CONFLICT DO NOTHING
            """,
            Map.of()));
    for (int indice = 0; indice < 100; indice++) {
      var contato =
          transacoes.execute(transacoesAtual -> {
            var linha =
                banco.um(
                    """
                    SELECT co.id,co."ContatoNumero",ca."CanalGUID" FROM firma_contato_foto f
                    JOIN firma."Contato" co ON co.id=f.contato_id
                    JOIN LATERAL(SELECT "CanalGUID" FROM firma."Canal" c
                      WHERE c."ClienteId"=co."ClienteId" AND c."CanalProvedorAPI"='Baileys'
                      AND COALESCE(c."CanalStatusUso",true) AND upper(c."CanalStatus") IN ('CONECTADO','CONNECTED','ONLINE')
                      AND NOT EXISTS(SELECT 1 FROM firma_relacionamento_exclusao e WHERE e.tabela='Canal' AND e.registro_id=c.id)
                      ORDER BY c.id LIMIT 1) ca ON true
                    WHERE f.proxima_verificacao<=now()
                    ORDER BY f.proxima_verificacao,co.id LIMIT 1 FOR UPDATE OF f SKIP LOCKED
                    """,
                    Map.of());
            if (linha != null)
              banco.executar(
                  "UPDATE firma_contato_foto SET proxima_verificacao=now()+interval '1 hour' WHERE contato_id=:id",
                  Map.of("id", linha.get("id")));
            return linha;
          });
      if (contato == null) return;
      try {
        String foto =
            transporte.fotoContato(
                UUID.fromString(contato.get("CanalGUID").toString()),
                contato.get("ContatoNumero").toString());
        if (foto != null) ServicoWebhooks.verificarDestino(foto);
        transacoes.executeWithoutResult(transacoesAtual -> {
          var parametros = new java.util.HashMap<String, Object>();
          parametros.put("id", contato.get("id"));
          parametros.put("foto", foto);
          banco.executar(
              "UPDATE firma.\"Contato\" SET \"ContatoAvatar\"=:foto,\"ContatoAvatarChave\"=NULL,\"updatedAt\"=now() WHERE id=:id",
              parametros);
          banco.executar(
              "UPDATE firma_contato_foto SET verificada_em=now(),proxima_verificacao=now()+interval '7 days' WHERE contato_id=:id",
              parametros);
        });
      } catch (RuntimeException erro) {
        registroLog.warn("Não foi possível renovar a foto do contato {}", contato.get("id"));
      }
    }
  }
}
