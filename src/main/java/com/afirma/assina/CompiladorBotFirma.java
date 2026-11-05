package com.afirma.assina;

import java.util.*;

/** Transforma o fluxo desenhado em etapas e variáveis que o MotorFluxoBot consegue executar. */
public final class CompiladorBotFirma {
  private CompiladorBotFirma() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> compilar(Map<String, Object> fluxo) {
    var tarefas = new ArrayList<Map<String, Object>>();
    var metadados = new LinkedHashMap<String, Object>();
    metadados.put("BotIniciado", false);
    metadados.put("TarefaAtual", "");
    metadados.put("BotFinalizado", false);
    metadados.put("Variaveis", Map.of());
    for (var objeto : (List<?>) fluxo.getOrDefault("nodes", List.of())) {
      var no = (Map<String, Object>) objeto;
      String id = no.get("id").toString(), tipo = Objects.toString(no.get("type"), "");
      if (!Set.of("tarefa", "script", "integracao").contains(tipo)) continue;
      var dados =
          no.get("data") instanceof Map<?, ?> registro
              ? (Map<String, Object>) registro
              : Map.<String, Object>of();
      var dadosAtuais = new LinkedHashMap<String, Object>();
      dadosAtuais.put("nome", id);
      var perguntas = new ArrayList<Object>();
      var respostas = new ArrayList<Object>();
      var acoes = new ArrayList<Object>();
      dadosAtuais.put("pergunta", perguntas);
      dadosAtuais.put("respostas", respostas);
      dadosAtuais.put("acoes", acoes);
      metadados.put(id + "_Executado", false);
      metadados.put(id + "_Resposta", "");
      if (tipo.equals("tarefa")) {
        if (dados.get("pergunta") instanceof String parametros && !parametros.isBlank())
          perguntas.add(Map.of("tipo", "texto", "conteudo", parametros));
        else dadosAtuais.put("tarefaAutomatica", true);
        if (dados.get("resposta") instanceof String registroAtual && !registroAtual.isBlank())
          respostas.add(Map.of("tipo", "texto", "conteudo", registroAtual));
      } else {
        dadosAtuais.put("tarefaAutomatica", true);
        metadados.put(id + "_Executado", false);
        metadados.put(id + "_Resposta", "");
        var scripts = new ArrayList<Object>();
        dadosAtuais.put("scripts", scripts);
        String subtipo = Objects.toString(dados.get("tipo"), "");
        if (subtipo.equals("gerar_atendimento"))
          scripts.add(
              Map.of(
                  "nome",
                  "GerarAtendimento",
                  "caminho",
                  "/ScriptsChatbot/GerarAtendimento",
                  "parametros",
                  Map.of("ContatoId", "", "CanalId", "", "ClienteId", "", "SetorId", "")));
        if (subtipo.equals("transferir_setor") && dados.get("setor") instanceof Map<?, ?> setor)
          dadosAtuais.put("constante", Map.of("nome", "SetorId", "valor", setor.get("id")));
        if (subtipo.equals("rest")) {
          var rest = dados.get("restData") instanceof Map<?, ?> registro ? registro : Map.of();
          var script = new LinkedHashMap<String, Object>();
          script.put("nome", "ExecutarAPI");
          script.put("caminho", "/ScriptsChatbot/ExecutarAPI");
          script.put("API_Externa", true);
          script.put("Url_API", rest.get("url"));
          script.put("Metodo_API", rest.get("metodo"));
          var cabecalhos = new LinkedHashMap<String, Object>();
          if (Boolean.TRUE.equals(rest.get("enviarHeaders"))
              && rest.get("headers") instanceof List<?> hs)
            for (Object cabecalho : hs)
              if (cabecalho instanceof Map<?, ?> registro)
                cabecalhos.put(Objects.toString(registro.get("chave")), registro.get("valor"));
          script.put("Headers_API", cabecalhos);
          var parametrosConsulta = new ArrayList<String>();
          if (Boolean.TRUE.equals(rest.get("enviarQueryParameters"))
              && rest.get("queryParameters") instanceof List<?> ps)
            for (Object parametros : ps)
              if (parametros instanceof Map<?, ?> registro)
                parametrosConsulta.add(
                    java.net.URLEncoder.encode(
                            Objects.toString(registro.get("nome")),
                            java.nio.charset.StandardCharsets.UTF_8)
                        + "="
                        + java.net.URLEncoder.encode(
                            Objects.toString(registro.get("valor")),
                            java.nio.charset.StandardCharsets.UTF_8));
          script.put("QueryParams_API", String.join("&", parametrosConsulta));
          script.put("Body_API", Objects.toString(rest.get("body"), ""));
          scripts.add(script);
        }
        if (subtipo.equals("hControl")) {
          scripts.add(
              Map.of(
                  "nome",
                  "hControl",
                  "caminho",
                  "/ScriptsChatbot/BuscarBoletos_hControl",
                  "CPFCNPJ",
                  "",
                  "clientId",
                  "",
                  "secretId",
                  ""));
          var cabecalho =
              dados.get("hControlData") instanceof Map<?, ?> registro ? registro : Map.of();
          dadosAtuais.put(
              "constante",
              List.of(
                  Map.of(
                      "nome", "clientId", "valor", Objects.toString(cabecalho.get("clientId"), "")),
                  Map.of(
                      "nome",
                      "secretId",
                      "valor",
                      Objects.toString(cabecalho.get("secretId"), ""))));
        }
        if (dados.get("destino_transferir") instanceof String destino
            && !destino.isBlank()
            && !destino.equals("end"))
          acoes.add(Map.of("nome", "transferir_tarefa", "valor", destino, "condicao", ""));
      }
      if (!tipo.equals("script")) {
        if (dados.get("acoes") instanceof List<?> acoesDaEtapa)
          for (Object acaoDoFluxo : acoesDaEtapa)
            if (acaoDoFluxo instanceof Map<?, ?> registro && !"end".equals(registro.get("valor")))
              acoes.add(registro);
        if (dados.get("variavel") instanceof String valor && !valor.isBlank())
          dadosAtuais.put("variavel", "&" + valor.toUpperCase(Locale.ROOT));
      }
      tarefas.add(dadosAtuais);
    }
    for (Object objeto : (List<?>) fluxo.getOrDefault("edges", List.of())) {
      var ligacao = (Map<?, ?>) objeto;
      for (var tabela : tarefas) {
        if ("start".equals(ligacao.get("source"))
            && Objects.equals(tabela.get("nome"), ligacao.get("target")))
          tabela.put("tarefaInicial", true);
        if ("end".equals(ligacao.get("target"))
            && Objects.equals(tabela.get("nome"), ligacao.get("source")))
          ((List<Object>) tabela.get("acoes"))
              .add(Map.of("nome", "finalizar_chatbot", "valor", "", "condicao", ""));
      }
    }
    return Map.of(
        "Definicao",
        Map.of(
            "tarefa",
            tarefas,
            "falha",
            Map.of(
                "respostas",
                List.of(
                    Map.of(
                        "tipo",
                        "texto",
                        "conteudo",
                        "Ops, não entendi o que você falou, verifique a pergunta e tente *responder novamente*.")))),
        "Metadata",
        metadados);
  }
}
