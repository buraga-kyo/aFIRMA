package com.afirma.assina;

import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/** Liga o cadastro dos templates à publicação, às imagens de exemplo e ao resultado devolvido pela Meta. */
@Service
public class TemplatesMetaFirma {
  private final BancoAssineai banco;
  private final FirmaPersistencia persistencia;
  private final ServicoCanalOficial oficial;
  private final ObjectMapper json;
  private final String versao;
  private final HttpClient clienteHttp =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TemplatesMetaFirma(
      BancoAssineai banco,
      FirmaPersistencia persistencia,
      ServicoCanalOficial oficial,
      ObjectMapper json,
      @Value("${APP_META_VERSAO:v23.0}") String versao) {
    this.banco = banco;
    this.persistencia = persistencia;
    this.oficial = oficial;
    this.json = json;
    this.versao = versao;
  }

  static List<Object> exemplos(Object valores) {
    var registro = new ArrayList<Object>();
    if (valores instanceof List<?> listaAtual)
      for (var valor : listaAtual)
        if (valor instanceof Map<?, ?> registroAtual)
          registro.add(registroAtual.getOrDefault("Valor", null));
    return registro;
  }

  static List<Map<String, Object>> componentes(Map<String, Object> registro) {
    var lista = new ArrayList<Map<String, Object>>();
    String tipo = Objects.toString(registro.get("MensagemTemplateCabecalhoTipo"), "");
    var conteudo =
        registro.get("MensagemTemplateCabecalhoConteudo") instanceof Map<?, ?> registroAtual
            ? registroAtual
            : Map.of();
    if (tipo.equals("Texto")) {
      var dados =
          new HashMap<String, Object>(
              Map.of(
                  "type",
                  "HEADER",
                  "format",
                  "TEXT",
                  "text",
                  Objects.toString(conteudo.get("Texto"), "")));
      var exemplo = exemplos(registro.get("MensagemTemplateCabecalhoVariaveis"));
      if (!exemplo.isEmpty()) dados.put("example", Map.of("header_text", exemplo));
      lista.add(dados);
    } else if (Set.of("Imagem", "Vídeo", "Documento").contains(tipo)) {
      String identificadorMidia = Objects.toString(conteudo.get("MediaHandle"), "");
      RegrasFirma.exigir(
          !identificadorMidia.isBlank(), "Envie o exemplo de mídia do cabeçalho antes de publicar");
      lista.add(
          Map.of(
              "type",
              "HEADER",
              "format",
              switch (tipo) {
                case "Imagem" -> "IMAGE";
                case "Vídeo" -> "VIDEO";
                default -> "DOCUMENT";
              },
              "example",
              Map.of("header_handle", List.of(identificadorMidia))));
    }
    var dadosAtuais =
        new HashMap<String, Object>(
            Map.of(
                "type",
                "BODY",
                "text",
                Objects.toString(registro.get("MensagemTemplateCorpo"), "")));
    var exemplos = exemplos(registro.get("MensagemTemplateCorpoVariaveis"));
    if (!exemplos.isEmpty()) dadosAtuais.put("example", Map.of("body_text", List.of(exemplos)));
    lista.add(dadosAtuais);
    if (registro.get("MensagemTemplateRodape") instanceof String campo && !campo.isBlank())
      lista.add(Map.of("type", "FOOTER", "text", campo));
    if (registro.get("MensagemTemplateBotoes") instanceof List<?> botoes && !botoes.isEmpty()) {
      RegrasFirma.exigir(botoes.size() <= 10, "Muitos botões");
      var botoesAtuais = new ArrayList<Map<String, Object>>();
      for (var botao : botoes) {
        RegrasFirma.exigir(botao instanceof Map<?, ?>, "Botão inválido");
        var registroAtual = (Map<?, ?>) botao;
        String texto = Objects.toString(registroAtual.get("Texto"), "");
        RegrasFirma.exigir(!texto.isBlank(), "Informe o texto do botão");
        switch (Objects.toString(registroAtual.get("Tipo"), "")) {
          case "quick-reply" -> botoesAtuais.add(Map.of("type", "QUICK_REPLY", "text", texto));
          case "call-phone" ->
              botoesAtuais.add(
                  Map.of(
                      "type",
                      "PHONE_NUMBER",
                      "text",
                      texto,
                      "phone_number",
                      Objects.toString(registroAtual.get("CodigoPais"), "")
                          + Objects.toString(registroAtual.get("Numero"), "")));
          case "visit-website" -> {
            String endereco = Objects.toString(registroAtual.get("URL"), "");
            RegrasFirma.url(endereco.replace("{{1}}", "exemplo"));
            botoesAtuais.add(Map.of("type", "URL", "text", texto, "url", endereco));
          }
          default ->
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de botão inválido");
        }
      }
      lista.add(Map.of("type", "BUTTONS", "buttons", botoesAtuais));
    }
    return lista;
  }

  private JsonNode chamar(long cliente, String metodo, String caminho, Object corpo) {
    String token = oficial.tokenFirma(cliente);
    try {
      var requisicao =
          HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + versao + "/" + caminho))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .method(
                  metodo,
                  corpo == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
      var respostaAtual =
          clienteHttp.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
      if (respostaAtual.statusCode() < 200 || respostaAtual.statusCode() >= 300)
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "A Meta recusou a operação do template (HTTP " + respostaAtual.statusCode() + ")");
      return json.readTree(respostaAtual.body());
    } catch (ResponseStatusException erro) {
      throw erro;
    } catch (Exception erro) {
      if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta indisponível");
    }
  }

  public Object operar(long id, String acao, long cliente) {
    var registro =
        banco.um(
            "SELECT * FROM firma.\"MensagemTemplate\" WHERE id=:id AND \"ClienteId\"=:c",
            Map.of("id", id, "c", cliente));
    if (registro == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var canal =
        banco.um(
            "SELECT * FROM firma.\"Canal\" WHERE id=:id AND \"ClienteId\"=:c AND \"CanalProvedorAPI\"='Meta'",
            Map.of("id", OperacaoFirma.numero(registro, "CanalId"), "c", cliente));
    if (canal == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal oficial não encontrado");
    String conta = Objects.toString(canal.get("CanalMetaWAID"), "");
    RegrasFirma.exigir(
        conta.matches("[0-9]{5,30}"), "Informe o identificador da conta Meta no canal");
    String remoto = Objects.toString(registro.get("MensagemTemplateMetaId"), "");
    JsonNode resposta;
    if (acao.equals("publicar")) {
      var corpo =
          new HashMap<String, Object>(
              Map.of(
                  "category",
                  registro.get("MensagemTemplateCategoria"),
                  "components",
                  componentes(registro)));
      String caminho = remoto;
      if (remoto.isBlank()) {
        corpo.put("name", registro.get("MensagemTemplateNome"));
        corpo.put("language", "pt_BR");
        corpo.put("allow_category_change", false);
        caminho = conta + "/message_templates";
      } else
        RegrasFirma.exigir(remoto.matches("[0-9]{5,30}"), "Identificador de template inválido");
      resposta = chamar(cliente, "POST", caminho, corpo);
      String codigo = resposta.path("id").asText(remoto);
      RegrasFirma.exigir(
          codigo.matches("[0-9]{5,30}"), "A Meta não confirmou o identificador do template");
      persistencia.gravar(
          "MensagemTemplate",
          id,
          Map.of(
              "MensagemTemplateMetaId",
              codigo,
              "MensagemTemplateStatusMeta",
              resposta.path("status").asText("PENDING")));
    } else if (acao.equals("sincronizar")) {
      if (remoto.isBlank()) {
        var consulta =
            chamar(
                cliente,
                "GET",
                conta
                    + "/message_templates?fields=id,status,rejected_reason&name="
                    + registro.get("MensagemTemplateNome"),
                null);
        RegrasFirma.exigir(consulta.path("data").size() > 0, "Template não encontrado na Meta");
        resposta = consulta.path("data").path(0);
      } else {
        RegrasFirma.exigir(remoto.matches("[0-9]{5,30}"), "Identificador de template inválido");
        resposta = chamar(cliente, "GET", remoto + "?fields=id,status,rejected_reason", null);
      }
      var dadosDoRegistro = new HashMap<String, Object>();
      dadosDoRegistro.put("MensagemTemplateMetaId", resposta.path("id").asText(remoto));
      dadosDoRegistro.put("MensagemTemplateStatusMeta", resposta.path("status").asText("PENDING"));
      dadosDoRegistro.put(
          "MensagemTemplateMotivoRejeicaoMeta", resposta.path("rejected_reason").asText(null));
      persistencia.gravar("MensagemTemplate", id, dadosDoRegistro);
    } else if (acao.equals("excluir-meta")) {
      resposta =
          chamar(
              cliente,
              "DELETE",
              conta + "/message_templates?name=" + registro.get("MensagemTemplateNome"),
              null);
      persistencia.gravar("MensagemTemplate", id, Map.of("MensagemTemplateStatusUso", false));
    } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação inválida");
    return Map.of("sucesso", true, "status", resposta.path("status").asText("PENDING"));
  }

  public String media(long cliente, MultipartFile arquivo) {
    RegrasFirma.exigir(
        arquivo != null && !arquivo.isEmpty() && arquivo.getSize() <= 15 * 1024 * 1024,
        "Mídia inválida");
    var empresa =
        banco.um(
            "SELECT \"ClienteMetaIdAplicativo\" FROM firma.\"Cliente\" WHERE id=:id",
            Map.of("id", cliente));
    String aplicativo = Objects.toString(empresa.get("ClienteMetaIdAplicativo"), "");
    RegrasFirma.exigir(
        aplicativo.matches("[0-9]{5,30}"), "Informe o aplicativo Meta no cadastro da empresa");
    String tipo = Objects.toString(arquivo.getContentType(), "application/octet-stream"),
        nome = Objects.toString(arquivo.getOriginalFilename(), "exemplo");
    String consulta =
        "?file_name="
            + java.net.URLEncoder.encode(nome, java.nio.charset.StandardCharsets.UTF_8)
            + "&file_length="
            + arquivo.getSize()
            + "&file_type="
            + java.net.URLEncoder.encode(tipo, java.nio.charset.StandardCharsets.UTF_8);
    String sessao =
        chamar(cliente, "POST", aplicativo + "/uploads" + consulta, null).path("id").asText();
    RegrasFirma.exigir(
        sessao.matches("[A-Za-z0-9:_=+%?&.\\/-]{1,2000}"), "Sessão de upload inválida");
    try {
      var requisicao =
          HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + versao + "/" + sessao))
              .timeout(Duration.ofSeconds(60))
              .header("Authorization", "OAuth " + oficial.tokenFirma(cliente))
              .header("file_offset", "0")
              .header("Content-Type", tipo)
              .POST(HttpRequest.BodyPublishers.ofByteArray(arquivo.getBytes()))
              .build();
      var respostaAtual = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
      if (respostaAtual.statusCode() < 200 || respostaAtual.statusCode() >= 300)
        throw new IllegalStateException();
      String cabecalho = json.readTree(respostaAtual.body()).path("h").asText();
      RegrasFirma.exigir(!cabecalho.isBlank(), "A Meta não confirmou a mídia");
      return cabecalho;
    } catch (Exception erro) {
      if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload de exemplo indisponível");
    }
  }
}
