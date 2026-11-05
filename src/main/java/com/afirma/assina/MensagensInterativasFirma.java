package com.afirma.assina;

import java.util.*;

/** Monta os botões e as listas no formato que a Meta espera receber. */
public final class MensagensInterativasFirma {
  private MensagensInterativasFirma() {}

  public static Map<String, Object> montar(Map<String, Object> registro) {
    String tipo = Objects.toString(registro.get("Tipo"), "").toLowerCase(Locale.ROOT);
    RegrasFirma.exigir(
        tipo.equals("botao") || tipo.equals("lista"), "Tipo de mensagem interativa inválido");
    String texto =
        Objects.toString(registro.getOrDefault("Texto", registro.get("MensagemTexto")), "");
    RegrasFirma.exigir(
        !texto.isBlank() && texto.length() <= 1024, "Texto da mensagem interativa inválido");
    var dados =
        new LinkedHashMap<String, Object>(
            Map.of(
                "type", tipo.equals("botao") ? "button" : "list", "body", Map.of("text", texto)));
    if (registro.get("Rodape") instanceof String campoAtual && !campoAtual.isBlank()) {
      RegrasFirma.exigir(campoAtual.length() <= 60, "Rodapé muito longo");
      dados.put("footer", Map.of("text", campoAtual));
    }
    if (tipo.equals("botao")) {
      RegrasFirma.exigir(registro.get("Botoes") instanceof List<?>, "Informe os botões");
      var botoes = (List<?>) registro.get("Botoes");
      RegrasFirma.exigir(!botoes.isEmpty() && botoes.size() <= 3, "Informe de 1 a 3 botões reply");
      var botoesAtuais = new ArrayList<Object>();
      for (var valor : botoes) {
        RegrasFirma.exigir(valor instanceof Map<?, ?>, "Botão inválido");
        var valorAtual = (Map<?, ?>) valor;
        RegrasFirma.exigir("reply".equals(valorAtual.get("Tipo")), "Tipo de botão não suportado");
        botoesAtuais.add(
            Map.of(
                "type",
                "reply",
                "reply",
                Map.of(
                    "id", campo(valorAtual, "Id", 256), "title", campo(valorAtual, "Texto", 20))));
      }
      dados.put("action", Map.of("buttons", botoesAtuais));
    } else {
      RegrasFirma.exigir(registro.get("Lista") instanceof Map<?, ?>, "Informe a lista");
      var lista = (Map<?, ?>) registro.get("Lista");
      RegrasFirma.exigir(lista.get("Itens") instanceof List<?>, "Informe os itens da lista");
      var secoes = new ArrayList<Object>();
      int total = 0;
      for (var valorDoRegistro : (List<?>) lista.get("Itens")) {
        RegrasFirma.exigir(valorDoRegistro instanceof Map<?, ?>, "Seção inválida");
        var secao = (Map<?, ?>) valorDoRegistro;
        RegrasFirma.exigir(secao.get("Itens") instanceof List<?>, "Itens inválidos");
        var linhas = new ArrayList<Object>();
        for (var valorAtual : (List<?>) secao.get("Itens")) {
          RegrasFirma.exigir(valorAtual instanceof Map<?, ?>, "Item inválido");
          var item = (Map<?, ?>) valorAtual;
          var registroAtual =
              new HashMap<String, Object>(
                  Map.of("id", campo(item, "Id", 200), "title", campo(item, "Titulo", 24)));
          if (item.get("Descricao") instanceof String descricao && !descricao.isBlank())
            registroAtual.put("description", campo(item, "Descricao", 72));
          linhas.add(registroAtual);
          total++;
        }
        secoes.add(Map.of("title", campo(secao, "Titulo", 24), "rows", linhas));
      }
      RegrasFirma.exigir(total > 0 && total <= 10, "Informe de 1 a 10 itens na lista");
      dados.put("action", Map.of("button", campo(lista, "Texto", 20), "sections", secoes));
    }
    return dados;
  }

  private static String campo(Map<?, ?> valorAtual, String nome, int maiorValor) {
    String textoAtual = Objects.toString(valorAtual.get(nome), "");
    RegrasFirma.exigir(
        !textoAtual.isBlank() && textoAtual.length() <= maiorValor, "Conteúdo inválido em " + nome);
    return textoAtual;
  }
}
