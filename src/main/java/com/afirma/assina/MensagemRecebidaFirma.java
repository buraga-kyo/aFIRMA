package com.afirma.assina;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Lê o conteúdo recebido do canal oficial antes de entregar a mensagem ao atendimento e ao bot. */
final class MensagemRecebidaFirma {
  record Conteudo(
      String texto, String tipo, String resposta, String midia, String nome, String mime) {}

  static Conteudo interpretar(JsonNode mensagem) {
    String tipo = mensagem.path("type").asText();
    String texto =
        switch (tipo) {
          case "text" -> mensagem.path("text").path("body").asText();
          case "button" -> mensagem.path("button").path("text").asText();
          case "interactive" -> interativa(mensagem.path("interactive"));
          case "contacts" -> contatos(mensagem.path("contacts"));
          case "location" -> localizacao(mensagem.path("location"));
          case "image", "video", "audio", "document", "sticker" ->
              mensagem.path(tipo).path("caption").asText();
          default -> "[" + tipo + "]";
        };
    var midia = mensagem.path(tipo);
    if (texto.isBlank()) texto = "[" + tipo + "]";
    return new Conteudo(
        texto,
        tipo.toUpperCase(java.util.Locale.ROOT),
        mensagem.path("context").path("id").asText(),
        midia.path("id").asText(),
        midia.path("filename").asText("anexo"),
        midia.path("mime_type").asText("application/octet-stream"));
  }

  private static String interativa(JsonNode dados) {
    var resposta = dados.path(dados.path("type").asText());
    String titulo = resposta.path("title").asText();
    String descricao = resposta.path("description").asText();
    return descricao.isBlank() ? titulo : titulo + "\n" + descricao;
  }

  private static String contatos(JsonNode contatos) {
    List<String> linhas = new ArrayList<>();
    for (var contato : contatos) {
      String nome = contato.path("name").path("formatted_name").asText();
      if (!nome.isBlank()) linhas.add(nome);
      for (var telefone : contato.path("phones")) {
        String numero = telefone.path("phone").asText();
        if (!numero.isBlank()) linhas.add(numero);
      }
      for (var email : contato.path("emails")) {
        String endereco = email.path("email").asText();
        if (!endereco.isBlank()) linhas.add(endereco);
      }
    }
    return String.join("\n", linhas);
  }

  private static String localizacao(JsonNode local) {
    if (!local.path("latitude").isNumber() || !local.path("longitude").isNumber())
      throw new IllegalArgumentException("Localização inválida");
    double latitude = local.path("latitude").asDouble(),
        longitude = local.path("longitude").asDouble();
    if (!Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || Math.abs(latitude) > 90
        || Math.abs(longitude) > 180) throw new IllegalArgumentException("Localização inválida");
    String nome = local.path("name").asText(), endereco = local.path("address").asText();
    return (nome.isBlank() ? "" : nome + "\n")
        + (endereco.isBlank() ? "" : endereco + "\n")
        + "https://maps.google.com/?q="
        + latitude
        + ","
        + longitude;
  }
}
