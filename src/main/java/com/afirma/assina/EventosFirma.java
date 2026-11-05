package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A ponte e o canal oficial entregam aqui as confirmações, reações e alterações das mensagens. */
@Service
public class EventosFirma {
  private final BancoAssineai banco;
  private final FirmaPersistencia persistencia;
  private final ObjectMapper json;

  public EventosFirma(BancoAssineai banco, FirmaPersistencia persistencia, ObjectMapper json) {
    this.banco = banco;
    this.persistencia = persistencia;
    this.json = json;
  }

  @Transactional
  public void atualizar(ChatControle.Evento evento) {
    RegrasFirma.exigir(
        evento.identificador() != null
            && !evento.identificador().isBlank()
            && evento.identificador().length() <= 100,
        "Mensagem inválida");
    var mensagem =
        banco.um(
            "SELECT m.* FROM firma.\"Mensagem\" m JOIN firma.\"Canal\" c ON c.id=m.\"CanalId\" WHERE c.\"CanalGUID\"=:c AND m.\"MensagemIdWhatsApp\"=:id FOR UPDATE OF m",
            Map.of("c", evento.canalId(), "id", evento.identificador()));
    if (mensagem == null) return;
    var valores = new HashMap<String, Object>();
    if (evento.ack() != null) {
      RegrasFirma.exigir(evento.ack() >= -1 && evento.ack() <= 4, "Confirmação inválida");
      int antiga = mensagem.get("MensagemACK") instanceof Number valor ? valor.intValue() : -1;
      valores.put("MensagemACK", Math.max(antiga, evento.ack()));
    }
    if (Boolean.TRUE.equals(evento.deletada())) valores.put("MensagemDeletada", true);
    if (Boolean.TRUE.equals(evento.editada()) && evento.texto() != null) {
      RegrasFirma.exigir(evento.texto().length() <= 10000, "Texto editado muito grande");
      valores.put("MensagemEditada", true);
      valores.put("MensagemTexto", evento.texto());
    }
    if (evento.reacao() != null && evento.reacao().isObject()) {
      var reacoes = new ArrayList<Object>();
      String numero = evento.reacao().path("Numero").asText(),
          reacao = evento.reacao().path("Reacao").asText();
      RegrasFirma.exigir(numero.length() <= 30 && reacao.length() <= 100, "Reação inválida");
      if (mensagem.get("MensagemReacao") instanceof List<?> lista)
        for (var registro : lista)
          if (!(registro instanceof Map<?, ?> mapa) || !Objects.equals(mapa.get("Numero"), numero))
            reacoes.add(registro);
      if (!reacao.isBlank()) reacoes.add(Map.of("Numero", numero, "Reacao", reacao));
      valores.put("MensagemReacao", reacoes);
    }
    if (!valores.isEmpty())
      persistencia.gravar("Mensagem", ((Number) mensagem.get("id")).longValue(), valores);
    var parametros = new HashMap<String, Object>();
    parametros.put("canal", evento.canalId());
    parametros.put("id", evento.identificador());
    parametros.put("ack", valores.get("MensagemACK"));
    parametros.put("deletada", Boolean.TRUE.equals(evento.deletada()));
    parametros.put("editada", Boolean.TRUE.equals(evento.editada()));
    parametros.put("texto", evento.texto());
    parametros.put("reacoes", json.writeValueAsString(valores.get("MensagemReacao")));
    banco.um("SELECT set_config('firma.relacionamento_direcao','canonico',true)", Map.of());
    banco.executar(
        "UPDATE chat_mensagem SET confirmacao=COALESCE(:ack,confirmacao),excluida=excluida OR :deletada,editada=editada OR :editada,texto=CASE WHEN :editada THEN :texto ELSE texto END,reacoes=COALESCE(NULLIF(CAST(:reacoes AS jsonb),'null'::jsonb),reacoes) WHERE canal_id=:canal AND identificador_externo=:id",
        parametros);
  }

  @Transactional
  public void metadados(ChatControle.Evento evento) {
    var canal =
        banco.um(
            "SELECT id,\"ClienteId\",\"CanalNumeroCelular\" FROM firma.\"Canal\" WHERE \"CanalGUID\"=:c",
            Map.of("c", evento.canalId()));
    if (canal == null) return;
    var mensagem =
        banco.um(
            "SELECT * FROM firma.\"Mensagem\" WHERE \"CanalId\"=:c AND \"MensagemIdWhatsApp\"=:id",
            Map.of("c", canal.get("id"), "id", evento.identificador()));
    var valores = new HashMap<String, Object>();
    if (mensagem == null && Boolean.TRUE.equals(evento.fromMe())) {
      var atendimento =
          banco.um(
              "SELECT a.id FROM firma.\"Atendimento\" a JOIN firma.\"Contato\" co ON co.id=a.\"ContatoId\" WHERE a.\"CanalId\"=:c AND co.\"ContatoNumero\"=:n AND a.\"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY a.id DESC LIMIT 1",
              Map.of("c", canal.get("id"), "n", evento.telefone()));
      valores.put("CanalId", canal.get("id"));
      valores.put("AtendimentoId", atendimento == null ? null : atendimento.get("id"));
      valores.put("MensagemIdWhatsApp", evento.identificador());
      valores.put("MensagemTexto", evento.texto());
      valores.put("MensagemTipo", "Texto");
      valores.put("MensagemFromMe", true);
      valores.put("MensagemEnviadaExternamente", true);
      valores.put("MensagemDataHoraWhatsApp", java.time.OffsetDateTime.now());
    }
    if (mensagem != null
        && evento.raw() != null
        && evento.raw().path("location").isObject()
        && mensagem.get("ArquivoId") instanceof Number arquivo) {
      var local = evento.raw().path("location");
      persistencia.gravar(
          "Arquivo",
          arquivo.longValue(),
          Map.of(
              "ArquivoLatitude",
              local.path("latitude").asDouble(),
              "ArquivoLongitude",
              local.path("longitude").asDouble()));
    }
    if (evento.raw() != null && evento.raw().path("timestamp").asText().matches("[0-9]{1,11}")) {
      long segundos = Long.parseLong(evento.raw().path("timestamp").asText());
      valores.put(
          "MensagemDataHoraWhatsApp",
          java.time.OffsetDateTime.ofInstant(
              java.time.Instant.ofEpochSecond(segundos), java.time.ZoneOffset.UTC));
    }
    if (evento.raw() != null) {
      String dadosBrutos = json.writeValueAsString(evento.raw());
      RegrasFirma.exigir(dadosBrutos.length() <= 200000, "Metadados muito grandes");
      valores.put("MensagemRAW", dadosBrutos);
      if (evento.raw().has("key"))
        valores.put("MensagemKeyWhatsApp", json.writeValueAsString(evento.raw().path("key")));
    }
    if (evento.respostaId() != null) {
      var resposta =
          banco.um(
              "SELECT id FROM firma.\"Mensagem\" WHERE \"CanalId\"=:c AND \"MensagemIdWhatsApp\"=:id",
              Map.of("c", canal.get("id"), "id", evento.respostaId()));
      if (resposta != null) valores.put("MensagemRespostaId", resposta.get("id"));
    }
    if ((mensagem != null || Boolean.TRUE.equals(evento.fromMe())) && !valores.isEmpty())
      persistencia.gravar(
          "Mensagem", mensagem == null ? null : ((Number) mensagem.get("id")).longValue(), valores);
  }
}
