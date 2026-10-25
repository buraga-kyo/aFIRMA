package com.afirma.assina;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoPlanoBuscaAssineAi {
	public record Etapa(String op, String column, String type, String value, String from, String to, String dir) { }
	public record Plano(List<Etapa> steps, String summary) { }
	private final Environment ambiente;
	private final ObjectMapper json;
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public ServicoPlanoBuscaAssineAi(Environment ambiente, ObjectMapper json) {
		this.ambiente = ambiente;
		this.json = json;
	}

	public Plano interpretar(String consulta) {
		if (consulta == null || consulta.isBlank() || consulta.length() > 500)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta inválida");
		String chave = ambiente.getProperty("ASSINEAI_GPT_API_KEY");
		if (chave == null || chave.isBlank())
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Configure ASSINEAI_GPT_API_KEY para habilitar o plano de IA");
		String modelo = ambiente.getProperty("ASSINEAI_GPT_MODEL", "gpt-4o-mini");
		String instrucao = "Converta a intenção em operações da tabela de documentos de assinatura. "
				+ "Responda APENAS JSON: {\"steps\":[{\"op\":\"column-filter\",\"column\":\"status\",\"type\":\"equals\",\"value\":\"Pendente\"}],\"summary\":\"...\"}. "
				+ "Colunas: id,titulo,data,responsavel,status,quantidadeAssinaturas,signatarios. "
				+ "Operações: column-filter com type equals ou contains e value; column-filter com type range, from e to; "
				+ "sort com column e dir asc ou desc. Máximo 5 etapas. "
				+ "Não invente campos nem operações; se não puder representar, retorne steps vazio. Não execute nada.";
		try {
			String corpo = json.writeValueAsString(Map.of("model", modelo, "temperature", 0,
					"max_tokens", 500, "messages", List.of(
						Map.of("role", "system", "content", instrucao),
						Map.of("role", "user", "content", consulta))));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
					.timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + chave)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GPT recusou a consulta");
			JsonNode resultado = json.readTree(json.readTree(resposta.body()).path("choices").path(0)
					.path("message").path("content").asText(""));
			if (!resultado.path("steps").isArray() || resultado.path("steps").size() > 5)
				throw new IllegalArgumentException("Plano inválido");
			List<Etapa> etapas = new ArrayList<>();
			List<String> campos = List.of("id", "titulo", "data", "responsavel", "status",
					"quantidadeAssinaturas", "signatarios");
			for (JsonNode item : resultado.path("steps")) {
				Etapa etapa = new Etapa(item.path("op").asText(""), item.path("column").asText(""),
						item.path("type").asText(""), item.path("value").asText(""),
						item.path("from").asText(""), item.path("to").asText(""),
						item.path("dir").asText(""));
				if (!campos.contains(etapa.column()) || !validar(etapa))
					throw new IllegalArgumentException("Operação fora da interface");
				etapas.add(etapa);
			}
			return new Plano(etapas, resultado.path("summary").asText("").substring(0,
						Math.min(200, resultado.path("summary").asText("").length())));
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível gerar o plano", erro);
		}
	}

	private boolean validar(Etapa e) {
		if (e.op().equals("sort")) return List.of("asc", "desc").contains(e.dir());
		if (!e.op().equals("column-filter")) return false;
		if (List.of("equals", "contains").contains(e.type())) return !e.value().isBlank() && e.value().length() <= 120;
		return e.type().equals("range") && (e.column().equals("data") || e.column().equals("quantidadeAssinaturas"))
				&& (!e.from().isBlank() || !e.to().isBlank());
	}
}
