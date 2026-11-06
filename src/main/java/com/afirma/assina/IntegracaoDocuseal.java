package com.afirma.assina;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Credencial exclusiva do servidor. Nenhum convite é disparado ao criar o modelo. */
@Service
public class IntegracaoDocuseal {

  private final String endereco, chave;
  private final ObjectMapper json;
  private final HttpClient cliente = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

  public IntegracaoDocuseal(
    @Value("${APP_DOCUSEAL_URL:}") String endereco,
    @Value("${APP_DOCUSEAL_API_KEY:}") String chave,
    ObjectMapper json
  ) {
    this.endereco = endereco.replaceAll("/+$", "");
    this.chave = chave;
    this.json = json;
  }

  public Object criar(byte[] pdf, String nome, List<Map<String, Object>> campos) {
    if (endereco.isBlank() || chave.isBlank()) throw erro(
      HttpStatus.SERVICE_UNAVAILABLE,
      "DocuSeal não configurado. Defina APP_DOCUSEAL_URL e APP_DOCUSEAL_API_KEY no servidor."
    );
    URI base;
    try {
      base = URI.create(endereco);
    } catch (IllegalArgumentException e) {
      throw erro(HttpStatus.SERVICE_UNAVAILABLE, "Endereço do DocuSeal inválido.");
    }
    if (
      base.getHost() == null ||
      base.getUserInfo() != null ||
      base.getQuery() != null ||
      base.getFragment() != null ||
      !(
        "https".equals(base.getScheme()) ||
        ("http".equals(base.getScheme()) &&
          Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost()))
      )
    ) throw erro(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Use HTTPS no endereço do DocuSeal, ou HTTP em localhost."
    );
    var documento = Map.of(
      "name",
      nome,
      "file",
      Base64.getEncoder().encodeToString(pdf),
      "fields",
      campos
    );
    var dados = Map.of("name", nome, "shared_link", false, "documents", List.of(documento));
    try {
      var pedido = HttpRequest.newBuilder(URI.create(endereco + "/templates/pdf"))
        .timeout(Duration.ofSeconds(60))
        .header("X-Auth-Token", chave)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(dados)))
        .build();
      var resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
      if (resposta.statusCode() == 404) throw erro(
        HttpStatus.BAD_GATEWAY,
        "Esta instância do DocuSeal não oferece criação por PDF via API. O endpoint /templates/pdf exige DocuSeal Pro."
      );
      if (resposta.statusCode() == 401 || resposta.statusCode() == 403) throw erro(
        HttpStatus.BAD_GATEWAY,
        "DocuSeal recusou a credencial configurada no servidor."
      );
      if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) throw erro(
        HttpStatus.BAD_GATEWAY,
        "DocuSeal recusou a criação do modelo (HTTP " + resposta.statusCode() + ")."
      );
      var resultado = json.readValue(resposta.body(), Map.class);
      if (!(resultado.get("id") instanceof Number)) throw erro(
        HttpStatus.BAD_GATEWAY,
        "Resposta inválida do DocuSeal."
      );
      return Map.of("id", resultado.get("id"));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw erro(HttpStatus.BAD_GATEWAY, "Conexão com o DocuSeal interrompida.");
    } catch (Exception e) {
      throw erro(
        HttpStatus.BAD_GATEWAY,
        "Não foi possível conectar ao DocuSeal. Verifique o serviço antes de tentar novamente."
      );
    }
  }

  public static List<Map<String, Object>> validarCampos(Object valor, int paginas) {
    return CamposEditorDocumentos.validar(valor, paginas);
  }

  private static ResponseStatusException erro(HttpStatus status, String mensagem) {
    return new ResponseStatusException(status, mensagem);
  }
}
