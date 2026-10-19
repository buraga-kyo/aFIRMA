package com.afirma.assina;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ChatTransporte {
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final ObjectMapper json;
	private final URI base;
	private final String chave;

	public ChatTransporte(ObjectMapper json,
			@Value("${APP_CHAT_BRIDGE_URL:http://127.0.0.1:8091}") String url,
			@Value("${APP_CHAT_BRIDGE_KEY:}") String chave) {
		this.json = json;
		this.base = URI.create(url);
		this.chave = chave;
	}

	private JsonNode chamar(String metodo, String caminho, Object corpo) {
		if (chave.length() < 32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Ponte WhatsApp sem chave configurada");
		try {
			HttpRequest.Builder pedido = HttpRequest.newBuilder(base.resolve(caminho))
					.timeout(Duration.ofSeconds(15)).header("X-Chat-Bridge-Key", chave);
			if (corpo == null) pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
			else pedido.header("Content-Type", "application/json")
					.method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
			HttpResponse<String> resposta = cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"Ponte WhatsApp recusou a operação");
			return resposta.body().isBlank() ? json.createObjectNode() : json.readTree(resposta.body());
		} catch (ResponseStatusException erro) {
			throw erro;
		} catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Ponte WhatsApp indisponível", erro);
		}
	}

	public void conectar(UUID canal) { chamar("POST", "/sessoes/" + canal, null); }
	public boolean configurado() { return chave.length() >= 32; }
	public void desconectar(UUID canal) { chamar("DELETE", "/sessoes/" + canal, null); }
	public String qr(UUID canal) {
		String valor = chamar("GET", "/sessoes/" + canal + "/qr", null).path("qr").asText();
		if (valor.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR ainda indisponível");
		return valor;
	}
	public String estado(UUID canal) {
		return chamar("GET", "/sessoes/" + canal + "/estado", null).path("estado").asText();
	}
	public String enviar(UUID canal, String telefone, String texto) {
		String identificador = chamar("POST", "/sessoes/" + canal + "/mensagens",
				Map.of("telefone", telefone, "texto", texto)).path("identificador").asText();
		if (identificador.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"WhatsApp não confirmou mensagem");
		return identificador;
	}
}
