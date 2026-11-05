package com.afirma.assina;

import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/** Consulta SGA, Asaas e hControl com as credenciais cadastradas pela empresa. */
@Service
public class IntegracoesFirma {
  private final BancoAssineai banco;
  private final ObjectMapper json;
  private final Environment ambiente;
  private final HttpClient clienteHttp =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public IntegracoesFirma(BancoAssineai banco, ObjectMapper json, Environment ambiente) {
    this.banco = banco;
    this.json = json;
    this.ambiente = ambiente;
  }

  private String token(long cliente, String integracao) {
    var registro =
        banco.um(
            "SELECT \"IntegracaoDados\" FROM firma.\"Integracao\" WHERE \"ClienteId\"=:c AND lower(\"IntegracaoNome\")=lower(:nome) AND \"IntegracaoStatus\" ORDER BY id LIMIT 1",
            Map.of("c", cliente, "nome", integracao));
    if (registro == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Integração não configurada ou desativada");
    Object dadosAtuais = registro.get("IntegracaoDados"), valor = null;
    if (dadosAtuais instanceof Map<?, ?> registroAtual) valor = registroAtual.get("Token");
    if (dadosAtuais instanceof List<?> listaAtual)
      for (Object valorAtual : listaAtual)
        if (valorAtual instanceof Map<?, ?> registroAtual
            && "Token".equals(registroAtual.get("campo"))) valor = registroAtual.get("valor");
    if (integracao.equalsIgnoreCase("hControl")) {
      String identificadorCliente = parametro(dadosAtuais, "clientId"),
          segredoCliente = parametro(dadosAtuais, "secretId");
      if (!identificadorCliente.isBlank() && !segredoCliente.isBlank()) {
        String recebido =
            chamar(
                    "HCONTROL_BASE_URL",
                    "/Account/Token?"
                        + query(
                            Map.of("clientId", identificadorCliente, "secretId", segredoCliente)),
                    "GET",
                    Map.of(),
                    null)
                .path("data")
                .path("token")
                .asText();
        if (!recebido.isBlank()) return recebido;
      }
    }
    String texto = Objects.toString(valor, "");
    if (texto.startsWith("APP_INTEGRACAO_"))
      texto = Objects.toString(ambiente.getProperty(texto), "");
    if (texto.isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Token da integração não configurado");
    return texto;
  }

  private String parametro(Object dados, String campo) {
    Object valor = null;
    if (dados instanceof Map<?, ?> registroAtual) valor = registroAtual.get(campo);
    if (dados instanceof List<?> lista)
      for (Object valorAtual : lista)
        if (valorAtual instanceof Map<?, ?> registroAtual
            && campo.equals(registroAtual.get("campo"))) valor = registroAtual.get("valor");
    String texto = Objects.toString(valor, "");
    return texto.startsWith("APP_INTEGRACAO_") ? ambiente.getProperty(texto, "") : texto;
  }

  private String query(Map<String, Object> filtros) {
    var partes = new ArrayList<String>();
    for (var item : filtros.entrySet())
      if (item.getValue() != null)
        partes.add(
            URLEncoder.encode(item.getKey(), StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(item.getValue().toString(), StandardCharsets.UTF_8));
    return String.join("&", partes);
  }

  JsonNode chamar(
      String base, String caminho, String metodo, Map<String, String> cabecalhos, Object corpo) {
    String enderecoAtual = Objects.toString(ambiente.getProperty(base), "");
    if (enderecoAtual.isBlank())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure " + base);
    String destino = enderecoAtual.replaceAll("/$", "") + caminho;
    URI endereco = URI.create(destino);
    RegrasFirma.exigir(
        Set.of("https", "http").contains(endereco.getScheme())
            && endereco.getHost() != null
            && endereco.getUserInfo() == null,
        "Base de integração inválida");
    try {
      var requisicao =
          HttpRequest.newBuilder(URI.create(destino))
              .timeout(Duration.ofSeconds(30))
              .header("Accept", "application/json");
      cabecalhos.forEach(requisicao::header);
      if (corpo != null) requisicao.header("Content-Type", "application/json");
      requisicao.method(
          metodo,
          corpo == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
      var resposta =
          clienteHttp.send(requisicao.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var in = resposta.body()) {
        byte[] bytes = in.readNBytes(5 * 1024 * 1024 + 1);
        if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
          if (base.equals("SGA_BASE_URL")
              && new String(bytes, StandardCharsets.UTF_8)
                  .contains("Não foram encontrados boletos dentro dos parâmetros enviados"))
            return json.createArrayNode();
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY,
              "A integração recusou a operação (HTTP " + resposta.statusCode() + ")");
        }
        if (bytes.length > 5 * 1024 * 1024)
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY, "Resposta da integração muito grande");
        return bytes.length == 0 ? json.createObjectNode() : json.readTree(bytes);
      }
    } catch (ResponseStatusException erro) {
      throw erro;
    } catch (Exception erro) {
      if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Integração indisponível");
    }
  }

  public Object consultar(long cliente, String nome, String operacao, Map<String, Object> filtros) {
    String texto = token(cliente, nome);
    var dadosDoRegistro = new HashMap<>(filtros);
    RegrasFirma.exigir(dadosDoRegistro.size() <= 40, "Muitos filtros");
    if (nome.equalsIgnoreCase("Asaas")) {
      String caminho =
          switch (operacao) {
            case "cobrancas" -> "/payments";
            case "clientes" -> "/customers";
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operação inválida");
          };
      var lista = json.createArrayNode();
      int offset = 0;
      for (int pagina = 0; pagina < 100; pagina++) {
        dadosDoRegistro.put("offset", offset);
        dadosDoRegistro.put("limit", 100);
        var registro =
            chamar(
                "ASAAS_BASE_URL",
                caminho + "?" + query(dadosDoRegistro),
                "GET",
                Map.of("access_token", texto),
                null);
        if (registro.path("data").isArray()) registro.path("data").forEach(lista::add);
        if (!registro.path("hasMore").asBoolean()) return lista;
        int tamanho = registro.path("data").size();
        RegrasFirma.exigir(tamanho > 0, "Paginação inválida na integração");
        offset += tamanho;
      }
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Refine os filtros; resultado maior que 10 mil registros");
    }
    if (nome.equalsIgnoreCase("SGA")) {
      if (operacao.equals("situacoes"))
        return chamar(
            "SGA_BASE_URL",
            "/listar/situacao-boleto/ativo",
            "GET",
            Map.of("Authorization", "Bearer " + texto),
            null);
      RegrasFirma.exigir(operacao.equals("boletos"), "Operação inválida");
      var lista = json.createArrayNode();
      for (int pagina = 0; pagina < 100; pagina++) {
        dadosDoRegistro.put("inicio_paginacao", pagina);
        dadosDoRegistro.put("quantidade_por_pagina", 1000);
        var registro =
            chamar(
                "SGA_BASE_URL",
                "/listar/boleto/periodo",
                "POST",
                Map.of("Authorization", "Bearer " + texto),
                dadosDoRegistro);
        RegrasFirma.exigir(registro.isArray(), "Resposta inválida do SGA");
        registro.forEach(lista::add);
        if (registro.size() == 0) return lista;
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Refine os filtros do SGA");
    }
    if (nome.equalsIgnoreCase("hControl")) {
      String caminho =
          switch (operacao) {
            case "boletos" ->
                dadosDoRegistro.containsKey("cpfCnpj")
                        || dadosDoRegistro.containsKey("cpfCnpjCliente")
                    ? "/Financeiro/ObterBoletosEmAbertoPorCpfCnpj"
                    : "/Financeiro/ObterBoletosEmAberto";
            case "imprimir" -> "/Financeiro/ImprimirBoleto";
            case "clientes" -> "/ObterClientes";
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operação inválida");
          };
      if (dadosDoRegistro.containsKey("cpfCnpj"))
        dadosDoRegistro.put(
            "cpfCnpjCliente",
            dadosDoRegistro.remove("cpfCnpj").toString().replaceAll("[^0-9]", ""));
      return chamar(
              "HCONTROL_BASE_URL",
              caminho + "?" + query(dadosDoRegistro),
              operacao.equals("imprimir") ? "POST" : "GET",
              Map.of("Authorization", "Bearer " + texto),
              null)
          .path("data");
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integração desconhecida");
  }

  public Object boletosBot(
      long empresa, String cpf, String identificadorCliente, String segredoCliente) {
    RegrasFirma.exigir(
        cpf.replaceAll("[^0-9]", "").matches("[0-9]{11}|[0-9]{14}"), "Informe CPF ou CNPJ válido");
    var chave =
        chamar(
                "HCONTROL_BASE_URL",
                "/Account/Token?"
                    + query(Map.of("clientId", identificadorCliente, "secretId", segredoCliente)),
                "GET",
                Map.of(),
                null)
            .path("data")
            .path("token")
            .asText();
    if (chave.isBlank())
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "hControl não confirmou a autenticação");
    return chamar(
            "HCONTROL_BASE_URL",
            "/Financeiro/ObterBoletosEmAbertoPorCpfCnpj?"
                + query(Map.of("cpfCnpjCliente", cpf.replaceAll("[^0-9]", ""))),
            "GET",
            Map.of("Authorization", "Bearer " + chave),
            null)
        .path("data");
  }
}
