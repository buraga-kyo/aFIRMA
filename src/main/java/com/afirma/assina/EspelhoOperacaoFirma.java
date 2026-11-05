package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Liga os cadastros de bots, campanhas e integrações às rotinas que executam cada operação. */
@Component
public class EspelhoOperacaoFirma {
  private final BancoAssineai banco;
  private final ObjectMapper json;

  public EspelhoOperacaoFirma(BancoAssineai banco, ObjectMapper json) {
    this.banco = banco;
    this.json = json;
  }

  private Object legado(String tabela, Object id) {
    if (id == null) return null;
    var registro =
        banco.um(
            "SELECT origem_id FROM firma_relacionamento_vinculo WHERE destino=:t AND destino_id=:id",
            Map.of("t", tabela, "id", id));
    return registro == null ? null : registro.get("origem_id");
  }

  private Long longo(String texto, Object id) {
    Object valorAtual = legado(texto, id);
    return valorAtual == null ? null : Long.valueOf(valorAtual.toString());
  }

  private UUID uuid(String texto, Object id) {
    Object valorAtual = legado(texto, id);
    return valorAtual == null ? null : UUID.fromString(valorAtual.toString());
  }

  private Object valor(Map<String, Object> registro, String textoAtual, Object padrao) {
    return registro.get(textoAtual) == null ? padrao : registro.get(textoAtual);
  }

  public void salvar(
      String tabela, Map<String, Object> registro, ServicoFirma.Contexto contexto, long empresa) {
    if (!Set.of(
            "AtendenteSetor",
            "Tag",
            "MensagemRapida",
            "ListaContato",
            "MensagemTemplate",
            "ChatbotDefinicao",
            "InteligenciaArtificial",
            "Integracao")
        .contains(tabela)) return;
    banco.um("SELECT set_config('firma.relacionamento_direcao','canonico',true)", Map.of());
    var dados = new LinkedHashMap<String, Object>();
    dados.put("empresa_id", empresa);
    var camposJson = new HashSet<String>();
    String origem;
    switch (tabela) {
      case "AtendenteSetor" -> {
        return;
      }
      case "Tag" -> {
        origem = "chat_etiqueta";
        dados.put("nome", registro.get("TagNome"));
        dados.put("ativa", valor(registro, "TagStatus", true));
      }
      case "MensagemRapida" -> {
        origem = "chat_mensagem_rapida";
        dados.put("atalho", "relacionamento-" + registro.get("id"));
        dados.put("titulo", "Resposta " + registro.get("id"));
        dados.put("texto", registro.get("MensagemRapidaTexto"));
      }
      case "ListaContato" -> {
        origem = "chat_lista_contatos";
        dados.put("nome", registro.get("ListaContatoNome"));
        dados.put("ativa", valor(registro, "ListaContatoStatus", true));
      }
      case "MensagemTemplate" -> {
        origem = "chat_modelo_mensagem";
        dados.put("nome", registro.get("MensagemTemplateNome"));
        dados.put("corpo", registro.get("MensagemTemplateCorpo"));
        dados.put("rodape", registro.get("MensagemTemplateRodape"));
        dados.put(
            "categoria",
            switch (Objects.toString(registro.get("MensagemTemplateCategoria"), "UTILITY")) {
              case "AUTHENTICATION" -> "AUTENTICACAO";
              case "MARKETING" -> "MARKETING";
              default -> "UTILIDADE";
            });
        dados.put(
            "estado",
            switch (Objects.toString(registro.get("MensagemTemplateStatusMeta"), "")) {
              case "APPROVED" -> "APROVADO";
              case "REJECTED" -> "REJEITADO";
              case "PENDING" -> "PENDENTE";
              default -> "RASCUNHO";
            });
        dados.put("identificador_meta", registro.get("MensagemTemplateMetaId"));
        dados.put("status_uso", valor(registro, "MensagemTemplateStatusUso", true));
        dados.put("metodo_envio", valor(registro, "MensagemTemplateMetodoEnvio", "OFICIAL"));
        dados.put("cabecalho_tipo", registro.get("MensagemTemplateCabecalhoTipo"));
        dados.put(
            "cabecalho_variaveis",
            valor(registro, "MensagemTemplateCabecalhoVariaveis", List.of()));
        dados.put("corpo_variaveis", valor(registro, "MensagemTemplateCorpoVariaveis", List.of()));
        camposJson.addAll(Set.of("cabecalho_variaveis", "corpo_variaveis"));
        dados.put("botoes", valor(registro, "MensagemTemplateBotoes", List.of()));
        camposJson.add("botoes");
      }
      case "ChatbotDefinicao" -> {
        origem = "chat_bot_definicao";
        dados.put("nome", registro.get("ChatbotDefinicaoNome"));
        dados.put("canal_id", uuid("Canal", registro.get("CanalId")));
        dados.put("ativa", valor(registro, "ChatbotDefinicaoStatus", false));
        dados.put(
            "provedor",
            "n8n".equalsIgnoreCase(Objects.toString(registro.get("ChatbotDefinicaoProvedor"), ""))
                ? "N8N"
                : "INTERNO");
        dados.put("endereco_externo", registro.get("ChatbotDefinicaoN8nURL"));
        dados.put(
            "funcionamento",
            valor(registro, "ChatbotDefinicaoFuncionamento", Map.of("Integral", true)));
        Object fluxo =
            valor(registro, "ChatbotDefinicaoFlow", Map.of("nodes", List.of(), "edges", List.of()));
        var arvore = json.valueToTree(fluxo);
        for (var no : arvore.path("nodes")) {
          var setor = no.path("data").path("setor");
          if (setor instanceof tools.jackson.databind.node.ObjectNode opcao
              && setor.path("id").asLong(0) > 0) {
            Long sid = longo("Setor", setor.path("id").asLong());
            if (sid != null) opcao.put("id", sid);
          }
        }
        dados.put("fluxo", arvore);
        camposJson.addAll(Set.of("funcionamento", "fluxo"));
      }
      case "InteligenciaArtificial" -> {
        origem = "chat_agente_ia";
        dados.put("nome", registro.get("InteligenciaArtificialNome"));
        dados.put(
            "provedor",
            Objects.toString(registro.get("InteligenciaArtificialProvedor"), "")
                    .toLowerCase(Locale.ROOT)
                    .contains("deepseek")
                ? "DEEPSEEK"
                : "OPENAI");
        dados.put("modelo", registro.get("InteligenciaArtificialModelo"));
        dados.put("instrucoes", registro.get("InteligenciaArtificialTextoPrompt"));
        dados.put("ativa", valor(registro, "InteligenciaArtificialStatus", true));
        dados.put("ativador", valor(registro, "InteligenciaArtificialAtivador", "manual-chat"));
        dados.put("referencia_segredo", "FIRMA:" + registro.get("id"));
      }
      case "Integracao" -> {
        origem = "chat_integracao";
        dados.put("nome", registro.get("IntegracaoNome"));
        dados.put(
            "tipo",
            Objects.toString(registro.get("IntegracaoNome"), "REST").toUpperCase(Locale.ROOT));
        dados.put("configuracao", valor(registro, "IntegracaoDados", Map.of()));
        camposJson.add("configuracao");
        dados.put("ativa", valor(registro, "IntegracaoStatus", true));
      }
      default -> {
        return;
      }
    }
    Object existente = legado(tabela, registro.get("id"));
    long id =
        existente == null
            ? ((Number)
                    banco
                        .um(
                            "SELECT nextval(pg_get_serial_sequence('" + origem + "','id')) id",
                            Map.of())
                        .get("id"))
                .longValue()
            : Long.parseLong(existente.toString());
    if (existente == null)
      banco.executar(
          "INSERT INTO firma_relacionamento_vinculo VALUES(:o,:oid,:t,:id,:c,'{}')",
          Map.of(
              "o",
              origem,
              "oid",
              Long.toString(id),
              "t",
              tabela,
              "id",
              registro.get("id"),
              "c",
              contexto.cliente()));
    if (tabela.equals("ChatbotDefinicao") && existente != null) {
      var antes = banco.um("SELECT * FROM chat_bot_definicao WHERE id=:id", Map.of("id", id));
      if (antes != null
          && (!Boolean.TRUE.equals(dados.get("ativa"))
              || !Objects.equals(antes.get("canal_id"), dados.get("canal_id"))
              || !Objects.equals(antes.get("provedor"), dados.get("provedor"))
              || !Objects.equals(
                  json.valueToTree(antes.get("fluxo")), json.valueToTree(dados.get("fluxo")))))
        banco.executar(
            "UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=now() WHERE definicao_id=:id AND estado='ATIVA'",
            Map.of("id", id));
    }
    dados.put("id", id);
    var parametros = new HashMap<String, Object>();
    var expressao = new ArrayList<String>();
    int indice = 0;
    for (var item : dados.entrySet()) {
      String chave = "p" + indice++;
      parametros.put(
          chave,
          camposJson.contains(item.getKey())
              ? json.writeValueAsString(item.getValue())
              : item.getValue());
      expressao.add(
          camposJson.contains(item.getKey()) ? "CAST(:" + chave + " AS jsonb)" : ":" + chave);
    }
    banco.executar(
        "INSERT INTO "
            + origem
            + " ("
            + String.join(",", dados.keySet())
            + ") VALUES("
            + String.join(",", expressao)
            + ") ON CONFLICT(id) DO UPDATE SET "
            + String.join(
                ",",
                dados.keySet().stream()
                    .filter(chave -> !chave.equals("id"))
                    .map(chave -> chave + "=EXCLUDED." + chave)
                    .toList()),
        parametros);
  }
}
