package com.afirma.assina;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProcessadorPublicacoesNostr {
  private final JdbcTemplate banco;
  private final ServicoPublicacaoNostr publicacoes;
  private final ObjectMapper json;

  public ProcessadorPublicacoesNostr(
      JdbcTemplate banco, ServicoPublicacaoNostr publicacoes, ObjectMapper json) {
    this.banco = banco;
    this.publicacoes = publicacoes;
    this.json = json;
  }

  @Scheduled(
      fixedDelayString = "${APP_NOSTR_INTERVALO_MS:10000}",
      initialDelayString = "${APP_NOSTR_INICIO_MS:30000}")
  public void processar() {
    UUID reserva = UUID.randomUUID();
    var registros =
        banco.queryForList(
            "UPDATE firma_nostr_publicacao SET lease=?,lease_ate=now()+interval '3"
                + " minutes',tentativas=tentativas+1 WHERE id IN (SELECT p.id FROM"
                + " firma_nostr_publicacao p JOIN firma_nostr_configuracao c ON"
                + " c.empresa_id=p.empresa_id WHERE ((c.ativa AND c.chave_publica=p.chave_publica"
                + " AND c.referencia=p.referencia AND p.publicacao_estado<>'SUSPENSA' AND"
                + " (p.publicacao_estado<>'PUBLICADO' OR p.tipo='COMPROVANTE' AND"
                + " p.anterioridade_estado<>'CONFIRMADA')) OR (p.tipo='COMPROVANTE' AND"
                + " p.evento_canonico IS NOT NULL AND p.publicacao_estado IN"
                + " ('PUBLICADO','PARCIAL') AND p.anterioridade_estado<>'CONFIRMADA')) AND"
                + " p.proxima_tentativa<=now() AND (p.lease_ate IS NULL OR p.lease_ate<now()) ORDER"
                + " BY p.criado_em LIMIT 1 FOR UPDATE OF p SKIP LOCKED) RETURNING *",
            reserva);
    for (var registro : registros) executar(registro, reserva);
  }

  @SuppressWarnings("unchecked")
  void executar(Map<String, Object> registro, UUID reserva) {
    UUID id = (UUID) registro.get("id");
    String publicacao = registro.get("publicacao_estado").toString();
    String anterioridade = registro.get("anterioridade_estado").toString();
    try {
      var pedido =
          new LinkedHashMap<String, Object>(
              json.readValue(registro.get("pedido").toString(), Map.class));
      String canonico = Objects.toString(registro.get("evento_canonico"), "");
      if (canonico.isBlank()) {
        var assinado = publicacoes.assinar(pedido);
        canonico = Objects.toString(assinado.get("eventoCanonico"), "");
        if (canonico.length() > 8192) throw new IllegalArgumentException();
        var evento = json.readValue(canonico, Map.class);
        if (!Objects.equals(evento.get("pubkey"), registro.get("chave_publica"))
            || !Objects.toString(evento.get("id"), "").matches("[0-9a-f]{64}"))
          throw new IllegalArgumentException();
        int alteradas =
            banco.update(
                "UPDATE firma_nostr_publicacao SET"
                    + " evento_canonico=?,evento_id=?,atualizado_em=now() WHERE id=? AND lease=?",
                canonico,
                evento.get("id"),
                id,
                reserva);
        if (alteradas != 1) return;
      }
      pedido.put("eventoCanonico", canonico);
      pedido.put("chavePublica", registro.get("chave_publica"));
      if (!publicacao.equals("PUBLICADO")) {
        // Pausar a automação impede novas chamadas de publicação após a leitura da fila.
        Boolean ativa =
            banco.queryForObject(
                "SELECT ativa AND chave_publica=? AND referencia=? FROM firma_nostr_configuracao"
                    + " WHERE empresa_id=?",
                Boolean.class,
                registro.get("chave_publica"),
                registro.get("referencia"),
                registro.get("empresa_id"));
        if (Boolean.TRUE.equals(ativa)) {
          var resultado = publicacoes.publicar(pedido);
          List<Map<String, Object>> anteriores =
              json.readValue(registro.get("relays").toString(), List.class);
          List<Map<String, Object>> novos = (List<Map<String, Object>>) resultado.get("relays");
          Map<String, Map<String, Object>> unidos = new LinkedHashMap<>();
          for (var r : anteriores) unidos.put(r.get("url").toString(), r);
          for (var r : novos) {
            String url = r.get("url").toString();
            if (!unidos.containsKey(url) || !Boolean.TRUE.equals(unidos.get(url).get("aceito")))
              unidos.put(url, r);
          }
          long aceitos =
              unidos.values().stream().filter(r -> Boolean.TRUE.equals(r.get("aceito"))).count();
          publicacao = aceitos >= 2 ? "PUBLICADO" : aceitos == 1 ? "PARCIAL" : "PENDENTE";
          banco.update(
              "UPDATE firma_nostr_publicacao SET"
                  + " publicacao_estado=?,relays=?::jsonb,atualizado_em=now() WHERE id=? AND"
                  + " lease=?",
              publicacao,
              json.writeValueAsString(unidos.values()),
              id,
              reserva);
        } else if (!Set.of("PUBLICADO", "PARCIAL").contains(publicacao)) return;
      }
      if (registro.get("tipo").equals("COMPROVANTE") && !anterioridade.equals("CONFIRMADA")) {
        if (registro.get("ots") instanceof byte[] ots)
          pedido.put("ots", Base64.getEncoder().encodeToString(ots));
        var resultado = publicacoes.carimbar(pedido);
        anterioridade = Objects.toString(resultado.get("estado"), "");
        if (!Set.of("PENDENTE", "AGUARDANDO_NO_BITCOIN", "CONFIRMADA").contains(anterioridade))
          throw new IllegalArgumentException();
        byte[] ots = Base64.getDecoder().decode(resultado.get("ots").toString());
        if (ots.length > 65536) throw new IllegalArgumentException();
        String bloco = Objects.toString(resultado.get("bloco"), null);
        if (anterioridade.equals("CONFIRMADA")
            && (bloco == null
                || !bloco.matches("[0-9a-f]{64}")
                || !(resultado.get("altura") instanceof Number)
                || !(resultado.get("data") instanceof Number)))
          throw new IllegalArgumentException();
        banco.update(
            "UPDATE firma_nostr_publicacao SET"
                + " anterioridade_estado=?,ots=?,bloco=?,altura=?,data_bloco=?,atualizado_em=now()"
                + " WHERE id=? AND lease=?",
            anterioridade,
            ots,
            bloco,
            resultado.get("altura"),
            resultado.get("data"),
            id,
            reserva);
      }
      long espera =
          publicacao.equals("PUBLICADO")
              ? 600
              : Math.min(
                  3600,
                  30L * (1L << Math.min(7, ((Number) registro.get("tentativas")).intValue())));
      banco.update(
          "UPDATE firma_nostr_publicacao SET erro_codigo=null,proxima_tentativa=now()+(? * interval"
              + " '1 second'),atualizado_em=now() WHERE id=? AND lease=?",
          espera,
          id,
          reserva);
    } catch (Exception falha) {
      // Não registra payload, URL de bunker, tokens ou mensagens do assinador.
      banco.update(
          "UPDATE firma_nostr_publicacao SET"
              + " erro_codigo='SERVICO_INDISPONIVEL_OU_RESPOSTA_INVALIDA',proxima_tentativa=now()+interval"
              + " '5 minutes',atualizado_em=now() WHERE id=? AND lease=?",
          id,
          reserva);
    } finally {
      banco.update(
          "UPDATE firma_nostr_publicacao SET lease=null,lease_ate=null WHERE id=? AND lease=?",
          id,
          reserva);
    }
  }
}
