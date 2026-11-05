package com.afirma.assina;

import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Entrega os eventos da empresa aos endereços cadastrados e guarda o resultado de cada tentativa. */
@Service
public class WebhooksFirma {
  private final BancoAssineai banco;
  private final TransactionTemplate transacoes;
  private final ObjectMapper json;
  private final ServicoFirma dados;
  private final FirmaPersistencia persistencia;
  private final boolean ativo;
  private final HttpClient clienteHttp =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public WebhooksFirma(
      BancoAssineai banco,
      TransactionTemplate transacoes,
      ObjectMapper json,
      ServicoFirma dados,
      FirmaPersistencia persistencia,
      @Value("${APP_FIRMA_ROTINAS:true}") boolean ativo) {
    this.banco = banco;
    this.transacoes = transacoes;
    this.json = json;
    this.dados = dados;
    this.persistencia = persistencia;
    this.ativo = ativo;
  }

  @Scheduled(fixedDelay = 3000)
  public void executar() {
    if (!ativo) return;
    for (int indice = 0; indice < 20; indice++) {
      var item =
          transacoes.execute(transacao -> {
            var registroAtual =
                banco.um(
                    "SELECT e.*,w.\"WebhookURL\" FROM firma_relacionamento_webhook_entrega e JOIN firma.\"Webhook\" w ON w.id=e.webhook_id WHERE e.estado IN ('PENDENTE','ENVIANDO') AND e.proxima_tentativa<=now() ORDER BY e.id LIMIT 1 FOR UPDATE OF e SKIP LOCKED",
                    Map.of());
            if (registroAtual != null)
              banco.executar(
                  "UPDATE firma_relacionamento_webhook_entrega SET estado='ENVIANDO',tentativas=tentativas+1,proxima_tentativa=now()+interval '2 minutes' WHERE id=:id",
                  Map.of("id", registroAtual.get("id")));
            return registroAtual;
          });
      if (item == null) break;
      entregar(item);
    }
  }

  private void entregar(Map<String, Object> item) {
    int tentativa = ((Number) item.get("tentativas")).intValue() + 1;
    Integer codigo = null;
    try {
      String destino = item.get("WebhookURL").toString();
      ServicoWebhooks.verificarDestino(destino);
      var pedido =
          HttpRequest.newBuilder(URI.create(destino))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .header("X-Firma-Entrega", item.get("id").toString())
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(item.get("dados"))))
              .build();
      var resposta = clienteHttp.send(pedido, HttpResponse.BodyHandlers.discarding());
      codigo = resposta.statusCode();
      if (codigo < 200 || codigo >= 300) throw new IllegalStateException();
      final int itemAtual = codigo;
      transacoes.executeWithoutResult(transacao -> {
        banco.executar(
            "UPDATE firma_relacionamento_webhook_entrega SET estado='ENTREGUE',codigo=:c,erro=NULL,respondida_em=now() WHERE id=:id",
            Map.of("id", item.get("id"), "c", itemAtual));
      });
    } catch (Exception erro) {
      if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
      var parametros = new HashMap<String, Object>();
      parametros.put("id", item.get("id"));
      parametros.put("estado", tentativa >= 5 ? "FALHOU" : "PENDENTE");
      parametros.put("codigo", codigo);
      parametros.put("minutos", Math.min(60, 1 << tentativa));
      banco.executar(
          "UPDATE firma_relacionamento_webhook_entrega SET estado=:estado,codigo=:codigo,erro='Destino indisponível ou resposta recusada',respondida_em=now(),proxima_tentativa=now()+(:minutos*interval '1 minute') WHERE id=:id",
          parametros);
    }
  }

  public Object entregas(long webhook, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "webhooks", "read");
    dados.registro("Webhook", webhook, contexto);
    return banco.listar(
        "SELECT id,evento,estado,tentativas,codigo,erro,criada_em,respondida_em FROM firma_relacionamento_webhook_entrega WHERE webhook_id=:id AND cliente_id=:c ORDER BY id DESC LIMIT 100",
        Map.of("id", webhook, "c", contexto.cliente()));
  }

  @Scheduled(cron = "0 0 1 * * *", zone = "America/Sao_Paulo")
  public void limpar() {
    if (!ativo) return;
    banco.executar(
        "DELETE FROM firma_relacionamento_token_revogado WHERE expira_em<now()", Map.of());
    banco.executar(
        "DELETE FROM firma_relacionamento_webhook_entrega WHERE criada_em<now()-interval '30 days' AND estado IN ('ENTREGUE','FALHOU')",
        Map.of());
    banco.executar(
        "DELETE FROM firma.\"APILog\" WHERE \"createdAt\"<now()-interval '30 days'", Map.of());
    banco.executar(
        "DELETE FROM firma.\"WebhookLog\" WHERE \"createdAt\"<now()-interval '30 days'", Map.of());
    banco.executar(
        "UPDATE firma_identidade.\"Sessao\" SET \"SessaoStatus\"='F' WHERE \"SessaoStatus\"='A' AND \"SessaoUltimoAcesso\"<now()-interval '30 days'",
        Map.of());
  }
}
