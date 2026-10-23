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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoFiltroInteligente {
	public record Filtro(String campo, String operador, String valor) {}
	public record Resposta(List<Filtro> filtros) {}
	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;
	private final ObjectMapper json;
	private final Environment ambiente;
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public ServicoFiltroInteligente(NamedParameterJdbcTemplate banco, EmpresaAtual empresas,
			ObjectMapper json, Environment ambiente) {
		this.banco = banco;
		this.empresas = empresas;
		this.json = json;
		this.ambiente = ambiente;
	}

	public Resposta interpretar(Authentication usuario, String pergunta, List<String> colunas) {
		if (pergunta == null || pergunta.isBlank() || pergunta.length() > 500
				|| colunas == null || colunas.isEmpty() || colunas.size() > 12
				|| colunas.stream().anyMatch(coluna -> coluna == null
					|| !coluna.matches("[a-z][a-z0-9_]{0,79}")))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro inválido");
		List<Map<String, Object>> agentes = banco.queryForList("""
			SELECT provedor,modelo,referencia_segredo FROM chat_agente_ia
			WHERE empresa_id=:empresa AND ativa ORDER BY id DESC LIMIT 1
			""", new MapSqlParameterSource("empresa", empresas.identificar(usuario)));
		if (agentes.isEmpty())
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ative um agente de IA na gestão para usar este filtro");
		Map<String, Object> agente = agentes.getFirst();
		String segredo = ambiente.getProperty((String) agente.get("referencia_segredo"));
		if (segredo == null || segredo.length() < 20)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Credencial do agente indisponível");
		String destino = switch ((String) agente.get("provedor")) {
			case "OPENAI" -> "https://api.openai.com/v1/chat/completions";
			case "DEEPSEEK" -> "https://api.deepseek.com/chat/completions";
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provedor de IA inválido");
		};
		String instrucao = "Interprete o pedido de filtro de uma tabela. Responda somente JSON no formato "
			+ "{\"filtros\":[{\"campo\":\"nome\",\"operador\":\"contem\",\"valor\":\"Ana\"}]}. "
			+ "Use no máximo três filtros. Campos permitidos: " + String.join(", ", colunas)
			+ ". Operadores permitidos: contem, igual, diferente. "
			+ "Para campos booleanos use valor Ativo ou Inativo. "
			+ "Para pedidos que não correspondem aos campos, devolva {\"filtros\":[]}. "
			+ "Não gere SQL nem explicações.";
		try {
			String corpo = json.writeValueAsString(Map.of(
				"model", agente.get("modelo"), "temperature", 0,
				"max_tokens", 300, "messages", List.of(
					Map.of("role", "system", "content", instrucao),
					Map.of("role", "user", "content", pergunta))));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create(destino))
				.timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + segredo)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agente de IA recusou o filtro");
			String conteudo = json.readTree(resposta.body()).path("choices").path(0)
				.path("message").path("content").asText("").trim();
			JsonNode interpretacao = json.readTree(conteudo);
			if (!interpretacao.path("filtros").isArray() || interpretacao.path("filtros").size() > 3)
				throw new IllegalArgumentException("Resposta do agente inválida");
			List<Filtro> filtros = new ArrayList<>();
			for (JsonNode item : interpretacao.path("filtros")) {
				String campo = item.path("campo").asText("");
				String operador = item.path("operador").asText("");
				String valor = item.path("valor").asText("");
				if (!colunas.contains(campo) || !List.of("contem", "igual", "diferente").contains(operador)
						|| valor.isBlank() || valor.length() > 120)
					throw new IllegalArgumentException("Filtro interpretado inválido");
				filtros.add(new Filtro(campo, operador, valor));
			}
			return new Resposta(filtros);
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível interpretar o filtro", erro);
		}
	}
}
