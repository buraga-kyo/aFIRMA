package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/** Os fluxos do n8n usam a chave da empresa para continuar o bot ou passar a conversa ao atendente. */
@RestController
@RequestMapping("/n8n")
public class N8nFirma {
  private final ApiFirmaV1 api;
  private final ServicoFirma dados;
  private final ServicoBots bots;
  private final FirmaOperacoes operacoes;

  public N8nFirma(ApiFirmaV1 api, ServicoFirma dados, ServicoBots bots, FirmaOperacoes operacoes) {
    this.api = api;
    this.dados = dados;
    this.bots = bots;
    this.operacoes = operacoes;
  }

  private UUID sessao(Map<String, Object> parametros, ServicoFirma.Contexto contextoAtual) {
    if (parametros.get("ChatbotMetadataId") instanceof Number valor) {
      dados.registro("ChatbotMetadata", valor.longValue(), contextoAtual);
      return operacoes.uuid("ChatbotMetadata", valor.longValue());
    }
    String id =
        Objects.toString(
            parametros.getOrDefault("ChatbotMetadataId", parametros.get("sessao")), "");
    return UUID.fromString(id);
  }

  @PostMapping("/mensagem/enviar")
  public Object mensagem(
      @RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    var item = api.acesso(requisicao);
    if (parametros.get("ChatbotMetadataId") == null) return api.mensagem(parametros, requisicao);
    var registroAtual = parametros.get("Mensagem") instanceof Map<?, ?> mapa ? mapa : Map.of();
    bots.enviarMensagemExterna(
        operacoes.executor(item),
        sessao(parametros, item),
        Objects.toString(
            registroAtual.get("MensagemTexto"),
            Objects.toString(
                registroAtual.get("Texto"), Objects.toString(parametros.get("texto"), ""))));
    return Map.of("Sucesso", true);
  }

  @PostMapping("/chatbot/contexto")
  public void contexto(@RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    var item = api.acesso(requisicao);
    bots.definirContinuacao(
        operacoes.executor(item),
        sessao(parametros, item),
        Objects.toString(parametros.get("URLWebhook"), ""));
  }

  @PostMapping("/chatbot/finalizar")
  public void finalizar(
      @RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    var item = api.acesso(requisicao);
    bots.finalizarExterno(operacoes.executor(item), sessao(parametros, item));
  }

  @PostMapping("/atendimento/insert")
  public Object atendimento(
      @RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    var item = api.acesso(requisicao);
    Long setor = parametros.get("SetorId") instanceof Number valor ? valor.longValue() : null;
    if (setor != null) {
      dados.registro("Setor", setor, item);
      setor = operacoes.legado("Setor", setor);
    }
    return Map.of(
        "AtendimentoId",
        bots.gerarAtendimentoExterno(operacoes.executor(item), sessao(parametros, item), setor));
  }
}
