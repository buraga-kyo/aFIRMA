package com.afirma.assina;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoInteligenciaConversacional {
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final NamedParameterJdbcTemplate banco;
	private final ObjectMapper json;
	private final Environment ambiente;

	public ServicoInteligenciaConversacional(NamedParameterJdbcTemplate banco, ObjectMapper json,
			Environment ambiente) {
		this.banco = banco;
		this.json = json;
		this.ambiente = ambiente;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	public String responder(long empresa, long agente, UUID atendimento) {
		Map<String, Object> configuracao;
		try {
			configuracao = banco.queryForMap("""
				SELECT provedor,modelo,instrucoes,referencia_segredo,temperatura,limite_resposta
				FROM chat_agente_ia WHERE id=:agente AND empresa_id=:empresa AND ativa
				""", dados("agente", agente, "empresa", empresa));
		} catch (org.springframework.dao.EmptyResultDataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agente indisponível");
		}
		String segredo = ambiente.getProperty((String) configuracao.get("referencia_segredo"));
		if (segredo == null || segredo.length() < 20)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Credencial do agente indisponível");
		List<Map<String, Object>> historico = banco.queryForList("""
			SELECT sentido,texto FROM chat_mensagem WHERE atendimento_id=:atendimento
			AND empresa_id=:empresa AND texto IS NOT NULL AND NOT excluida
			ORDER BY id DESC LIMIT 20
			""", dados("atendimento", atendimento, "empresa", empresa));
		if (historico.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversa vazia");
		List<Map<String, String>> mensagens = new ArrayList<>();
		mensagens.add(Map.of("role", "system", "content", (String) configuracao.get("instrucoes")));
		for (int indice = historico.size() - 1; indice >= 0; indice--) {
			Map<String, Object> mensagem = historico.get(indice);
			mensagens.add(Map.of("role", "ENTRADA".equals(mensagem.get("sentido")) ? "user" : "assistant",
					"content", (String) mensagem.get("texto")));
		}
		String provedor = (String) configuracao.get("provedor");
		String destino = switch (provedor) {
			case "OPENAI" -> "https://api.openai.com/v1/chat/completions";
			case "DEEPSEEK" -> "https://api.deepseek.com/chat/completions";
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provedor inválido");
		};
		try {
			String corpo = json.writeValueAsString(Map.of("model", configuracao.get("modelo"),
					"messages", mensagens, "temperature", configuracao.get("temperatura"),
					"max_tokens", configuracao.get("limite_resposta")));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create(destino))
					.timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + segredo)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provedor de IA recusou a solicitação");
			String texto = json.readTree(resposta.body()).path("choices").path(0)
					.path("message").path("content").asText();
			if (texto.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta de IA vazia");
			return texto.length() > 4096 ? texto.substring(0, 4096) : texto;
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provedor de IA indisponível", erro);
		}
	}
}
