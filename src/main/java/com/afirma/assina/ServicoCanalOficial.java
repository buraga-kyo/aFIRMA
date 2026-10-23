package com.afirma.assina;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoCanalOficial {
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final NamedParameterJdbcTemplate banco;
	private final ObjectMapper json;
	private final Environment ambiente;
	private final String versao;

	public ServicoCanalOficial(NamedParameterJdbcTemplate banco, ObjectMapper json, Environment ambiente,
			@Value("${APP_META_VERSAO:v23.0}") String versao) {
		this.banco = banco;
		this.json = json;
		this.ambiente = ambiente;
		if (!versao.matches("v[0-9]{1,2}\\.[0-9]"))
			throw new IllegalArgumentException("Versão da API oficial inválida");
		this.versao = versao;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	public String enviar(UUID canal, String telefone, String texto) {
		if (texto == null || texto.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texto obrigatório");
		return transmitir(canal, Map.of("messaging_product", "whatsapp", "to", telefone,
				"type", "text", "text", Map.of("body", texto)));
	}

	public String enviarModelo(UUID canal, String telefone, String nome, String idioma) {
		if (nome == null || !nome.matches("[a-z0-9_]{1,120}") || idioma == null
				|| !idioma.matches("[a-z]{2}_[A-Z]{2}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modelo inválido");
		return transmitir(canal, Map.of("messaging_product", "whatsapp", "to", telefone,
				"type", "template", "template", Map.of("name", nome,
					"language", Map.of("code", idioma))));
	}

	public String enviarArquivo(UUID canal, String telefone, String nome,
			String tipoMime, byte[] conteudo, String legenda) {
		if (conteudo == null || conteudo.length == 0 || conteudo.length > 15 * 1024 * 1024
				|| nome == null || nome.length() > 255 || tipoMime == null
				|| !tipoMime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
		Map<String, Object> configuracao = banco.queryForMap("""
			SELECT identificador_meta,referencia_segredo_meta FROM chat_canal
			WHERE id=:id AND provedor='META' AND ativo
			""", dados("id", canal));
		String referencia = (String) configuracao.get("referencia_segredo_meta");
		if (referencia == null || !referencia.matches("APP_META_TOKEN_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal inválido");
		String segredo = ambiente.getProperty(referencia);
		if (segredo == null || segredo.length() < 20)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal indisponível");
		String identificador = (String) configuracao.get("identificador_meta");
		if (identificador == null || !identificador.matches("[0-9]{5,30}"))
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identificador do canal inválido");
		String limite = "firma" + UUID.randomUUID().toString().replace("-", "");
		try {
			ByteArrayOutputStream corpo = new ByteArrayOutputStream();
			corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"messaging_product\"\r\n\r\nwhatsapp\r\n")
					.getBytes(StandardCharsets.UTF_8));
			corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\n"
					+ tipoMime + "\r\n").getBytes(StandardCharsets.UTF_8));
			corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
					+ nome.replace('"', '_').replace('\r', '_').replace('\n', '_') + "\"\r\nContent-Type: "
					+ tipoMime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			corpo.write(conteudo);
			corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + versao
					+ "/" + identificador + "/media"))
					.timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + segredo)
					.header("Content-Type", "multipart/form-data; boundary=" + limite)
					.POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray())).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Canal oficial recusou o arquivo");
			String arquivo = json.readTree(resposta.body()).path("id").asText();
			if (arquivo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Arquivo sem confirmação");
			String tipo = tipoMime.startsWith("image/") ? "image" : tipoMime.startsWith("video/") ? "video"
					: tipoMime.startsWith("audio/") ? "audio" : "document";
			Map<String, Object> detalhes = new java.util.HashMap<>();
			detalhes.put("id", arquivo);
			if ("document".equals(tipo)) detalhes.put("filename", nome);
			if (legenda != null && !legenda.isBlank() && !"audio".equals(tipo)) detalhes.put("caption", legenda);
			return transmitir(canal, Map.of("messaging_product", "whatsapp", "to", telefone,
					"type", tipo, tipo, detalhes));
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Envio do arquivo indisponível", erro);
		}
	}

	public Map<String, Object> publicarModelo(UUID canal, long modelo, long empresa) {
		Map<String, Object> registro = modelo(canal, modelo, empresa);
		if (registro.get("identificador_modelo") != null)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Modelo já publicado");
		String categoria = switch ((String) registro.get("categoria")) {
			case "UTILIDADE" -> "UTILITY";
			case "MARKETING" -> "MARKETING";
			case "AUTENTICACAO" -> "AUTHENTICATION";
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida");
		};
		Credenciais credenciais = credenciais(registro);
		String conta = (String) registro.get("identificador_conta_meta");
		if (conta == null || !conta.matches("[0-9]{5,30}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta oficial inválida");
		try {
			String corpo = json.writeValueAsString(Map.of(
					"name", registro.get("nome"), "language", registro.get("idioma"),
					"category", categoria,
					"components", List.of(Map.of("type", "BODY", "text", registro.get("corpo")))));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/"
					+ versao + "/" + conta + "/message_templates"))
					.timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + credenciais.token())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cadastro do modelo recusado");
			JsonNode remoto = json.readTree(resposta.body());
			String identificador = remoto.path("id").asText();
			if (!identificador.matches("[0-9]{5,30}"))
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Modelo sem identificador");
			return atualizarModelo(modelo, empresa, identificador,
					traduzirEstado(remoto.path("status").asText("PENDING")));
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cadastro do modelo indisponível", erro);
		}
	}

	public Map<String, Object> sincronizarModelo(UUID canal, long modelo, long empresa) {
		Map<String, Object> registro = modelo(canal, modelo, empresa);
		String identificador = (String) registro.get("identificador_modelo");
		if (identificador == null || !identificador.matches("[0-9]{5,30}"))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Modelo ainda não publicado");
		Credenciais credenciais = credenciais(registro);
		try {
			HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/"
					+ versao + "/" + identificador + "?fields=id,status"))
					.timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + credenciais.token())
					.GET().build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Consulta do modelo recusada");
			String estado = traduzirEstado(json.readTree(resposta.body()).path("status").asText());
			return atualizarModelo(modelo, empresa, identificador, estado);
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Consulta do modelo indisponível", erro);
		}
	}

	private Map<String, Object> modelo(UUID canal, long modelo, long empresa) {
		List<Map<String, Object>> encontrados = banco.queryForList("""
			SELECT modelo.nome,modelo.idioma,modelo.categoria,modelo.corpo,
			modelo.identificador_meta AS identificador_modelo,
			canal.identificador_conta_meta,canal.referencia_segredo_meta
			FROM chat_modelo_mensagem modelo JOIN chat_canal canal
			ON canal.id=:canal AND canal.empresa_id=modelo.empresa_id
			WHERE modelo.id=:modelo AND modelo.empresa_id=:empresa
			AND canal.provedor='META' AND canal.ativo
			""", dados("canal", canal, "modelo", modelo, "empresa", empresa));
		if (encontrados.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		return encontrados.getFirst();
	}

	private record Credenciais(String token) {}

	private Credenciais credenciais(Map<String, Object> configuracao) {
		String referencia = (String) configuracao.get("referencia_segredo_meta");
		if (referencia == null || !referencia.matches("APP_META_TOKEN_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal inválido");
		String token = ambiente.getProperty(referencia);
		if (token == null || token.length() < 20)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal indisponível");
		return new Credenciais(token);
	}

	private String traduzirEstado(String estado) {
		return switch (estado) {
			case "APPROVED" -> "APROVADO";
			case "REJECTED" -> "REJEITADO";
			case "PAUSED", "DISABLED" -> "PAUSADO";
			default -> "PENDENTE";
		};
	}

	private Map<String, Object> atualizarModelo(long modelo, long empresa,
			String identificador, String estado) {
		return banco.queryForMap("""
			UPDATE chat_modelo_mensagem SET identificador_meta=:identificador,estado=:estado
			WHERE id=:modelo AND empresa_id=:empresa RETURNING *
			""", dados("modelo", modelo, "empresa", empresa,
				"identificador", identificador, "estado", estado));
	}

	private String transmitir(UUID canal, Map<String, Object> mensagem) {
		if (!String.valueOf(mensagem.get("to")).matches("[0-9]{8,15}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone inválido");
		Map<String, Object> configuracao;
		try {
			configuracao = banco.queryForMap("""
				SELECT identificador_meta,referencia_segredo_meta FROM chat_canal
				WHERE id=:id AND provedor='META' AND ativo
				""", dados("id", canal));
		} catch (org.springframework.dao.EmptyResultDataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal oficial não encontrado");
		}
		String referencia = (String) configuracao.get("referencia_segredo_meta");
		if (referencia == null || !referencia.matches("APP_META_TOKEN_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal não configurado");
		String segredo = ambiente.getProperty(referencia);
		if (segredo == null || segredo.length() < 20)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token do canal indisponível");
		String identificador = (String) configuracao.get("identificador_meta");
		if (identificador == null || !identificador.matches("[0-9]{5,30}"))
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identificador do canal inválido");
		try {
			HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + versao
					+ "/" + identificador + "/messages"))
					.timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + segredo)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mensagem))).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Canal oficial recusou a mensagem");
			String externo = json.readTree(resposta.body()).path("messages").path(0).path("id").asText();
			if (externo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Envio sem confirmação");
			return externo;
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Canal oficial indisponível", erro);
		}
	}
}
