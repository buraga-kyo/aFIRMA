package com.afirma.assina;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MotorFluxoBot {
	private static final Pattern VARIAVEL = Pattern.compile(
		"&([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*|\\[[0-9]+\\])*)");
	private static final Pattern COMPARACAO = Pattern.compile("^\\s*(.+?)\\s*(===|!==|==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");
	private static final Pattern FUNCAO = Pattern.compile("(somenteNumero|somenteAlphaNumerico|formatarData)\\(([^()]*)\\)");
	private final NamedParameterJdbcTemplate banco;
	private final ObjectMapper json;
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER).build();

	public MotorFluxoBot(NamedParameterJdbcTemplate banco, ObjectMapper json) {
		this.banco = banco;
		this.json = json;
	}

	public record Contexto(long empresa, UUID canal, long contato, UUID atendimento,
			String telefone, String nomeContato) {}
	public record Resultado(String etapa, boolean concluida, String texto, Map<String, Object> variaveis) {}

	public void validar(JsonNode fluxo) {
		if (!fluxo.path("nodes").isArray() || !fluxo.path("edges").isArray()
				|| fluxo.path("nodes").size() > 200 || fluxo.path("edges").size() > 400)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fluxo inválido");
		Map<String, Boolean> nomes = new HashMap<>();
		for (JsonNode no : fluxo.path("nodes")) {
			String id = no.path("id").asText("");
			String tipo = no.path("type").asText("");
			if (!id.matches("[A-Za-z0-9_-]{1,100}") || nomes.putIfAbsent(id, true) != null
					|| !tipo.matches("tarefa|script|integracao"))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etapa inválida ou repetida");
			String subtipo = no.path("data").path("tipo").asText("");
			if ("script".equals(tipo) && !subtipo.matches("gerar_atendimento|transferir_setor"))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script de bot desconhecido");
			if ("integracao".equals(tipo) && !"rest".equals(subtipo))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integração do bot fora do escopo");
		}
		int inicios = 0;
		for (JsonNode aresta : fluxo.path("edges")) {
			String origem = aresta.path("source").asText("");
			String destino = aresta.path("target").asText("");
			if (!"start".equals(origem) && !nomes.containsKey(origem)
					|| !"end".equals(destino) && !nomes.containsKey(destino))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ligação do fluxo inválida");
			if ("start".equals(origem)) inicios++;
		}
		for (JsonNode no : fluxo.path("nodes")) {
			for (JsonNode acao : no.path("data").path("acoes")) {
				String destino = acao.path("valor").asText("");
				String condicao = acao.path("condicao").asText("");
				if (!"end".equals(destino) && !nomes.containsKey(destino)
						|| !condicao.isBlank() && !COMPARACAO.matcher(condicao).matches())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação do fluxo inválida");
			}
			String destino = no.path("data").path("destino_transferir").asText("");
			if (!destino.isBlank() && !"end".equals(destino) && !nomes.containsKey(destino))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transferência do fluxo inválida");
		}
		if (inicios != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fluxo exige um início");
	}

	public Resultado avancar(JsonNode fluxo, String etapa, String mensagem,
			Map<String, Object> variaveis, Contexto contexto) {
		Map<String, Object> dados = new HashMap<>(variaveis);
		dados.putIfAbsent("NumeroRemetente", contexto.telefone());
		dados.putIfAbsent("ClienteId", contexto.empresa());
		dados.putIfAbsent("CanalGUID", contexto.canal().toString());
		dados.putIfAbsent("ContatoId", contexto.contato());
		dados.putIfAbsent("ContatoNome", contexto.nomeContato());
		String atual = etapa == null ? destino(fluxo, "start", mensagem, dados, null) : etapa;
		boolean entrando = etapa == null;
		StringBuilder saida = new StringBuilder();
		for (int saltos = 0; saltos < 50; saltos++) {
			if (atual == null || "end".equals(atual)) return resultado(atual, true, saida, dados);
			JsonNode no = localizar(fluxo, atual);
			if (no == null) throw new IllegalStateException("Etapa do bot não encontrada");
			JsonNode configuracao = no.path("data");
			String tipo = no.path("type").asText();
			if ("tarefa".equals(tipo)) {
				String pergunta = configuracao.path("pergunta").asText("").trim();
				if (entrando && !pergunta.isBlank()) {
					acrescentar(saida, substituir(pergunta, dados, mensagem));
					return resultado(atual, false, saida, dados);
				}
				String variavel = configuracao.path("variavel").asText("");
				if (!pergunta.isBlank() && variavel.matches("[A-Za-z][A-Za-z0-9_]{0,79}"))
					dados.put(variavel.toUpperCase(), mensagem);
				String proximo = destino(fluxo, atual, mensagem, dados, configuracao);
				if (proximo == null && !pergunta.isBlank()) {
					acrescentar(saida, "Não entendi sua resposta. " + substituir(pergunta, dados, mensagem));
					return resultado(atual, false, saida, dados);
				}
				acrescentar(saida, substituir(configuracao.path("resposta").asText(""), dados, mensagem));
				atual = proximo;
			} else {
				if ("script".equals(tipo)) executarScript(configuracao, dados, contexto);
				if ("integracao".equals(tipo)) executarIntegracao(configuracao, dados, mensagem);
				acrescentar(saida, substituir(configuracao.path("resposta").asText(""), dados, mensagem));
				String proximo = destino(fluxo, atual, mensagem, dados, configuracao);
				if ("script".equals(tipo) && configuracao.hasNonNull("destino_transferir"))
					proximo = configuracao.path("destino_transferir").asText();
				atual = proximo;
			}
			entrando = true;
		}
		throw new IllegalStateException("Fluxo automático excedeu 50 etapas");
	}

	private Resultado resultado(String etapa, boolean concluida, StringBuilder texto,
			Map<String, Object> dados) {
		String mensagem = texto.toString();
		return new Resultado(etapa, concluida,
				mensagem.length() > 4096 ? mensagem.substring(0, 4096) : mensagem, dados);
	}

	private void acrescentar(StringBuilder saida, String texto) {
		if (texto == null || texto.isBlank()) return;
		if (!saida.isEmpty()) saida.append("\n\n");
		saida.append(texto.trim());
	}

	private JsonNode localizar(JsonNode fluxo, String id) {
		for (JsonNode no : fluxo.path("nodes")) if (id.equals(no.path("id").asText())) return no;
		return null;
	}

	private String destino(JsonNode fluxo, String origem, String mensagem,
			Map<String, Object> dados, JsonNode configuracao) {
		if (configuracao != null && configuracao.path("acoes").isArray()
				&& !configuracao.path("acoes").isEmpty()) {
			for (JsonNode acao : configuracao.path("acoes")) {
				if (condicao(acao.path("condicao").asText(""), dados, mensagem))
					return acao.path("valor").asText("end");
			}
			return null;
		}
		String unica = null;
		int quantidade = 0;
		for (JsonNode aresta : fluxo.path("edges")) {
			if (!origem.equals(aresta.path("source").asText())) continue;
			quantidade++;
			String condicao = aresta.path("sourceHandle").asText("");
			if (condicao.isBlank()) condicao = aresta.path("label").asText("");
			if (!condicao.isBlank() && (condicao.equalsIgnoreCase(mensagem.trim())
					|| condicao(condicao, dados, mensagem))) return aresta.path("target").asText();
			if (condicao.isBlank()) unica = aresta.path("target").asText();
		}
		if (unica != null) return unica;
		if (quantidade == 1) for (JsonNode aresta : fluxo.path("edges"))
			if (origem.equals(aresta.path("source").asText())) return aresta.path("target").asText();
		return null;
	}

	private boolean condicao(String expressao, Map<String, Object> dados, String mensagem) {
		if (expressao == null || expressao.isBlank()) return true;
		if (expressao.contains("||")) {
			for (String parte : expressao.split("\\|\\|"))
				if (condicao(parte, dados, mensagem)) return true;
			return false;
		}
		if (expressao.contains("&&")) {
			for (String parte : expressao.split("&&"))
				if (!condicao(parte, dados, mensagem)) return false;
			return true;
		}
		Matcher comparacao = COMPARACAO.matcher(expressao);
		if (!comparacao.matches()) return false;
		String esquerda = valor(comparacao.group(1), dados, mensagem);
		String direita = valor(comparacao.group(3), dados, mensagem);
		int ordem = esquerda.matches("-?[0-9]+(?:\\.[0-9]+)?")
				&& direita.matches("-?[0-9]+(?:\\.[0-9]+)?")
			? new java.math.BigDecimal(esquerda).compareTo(new java.math.BigDecimal(direita))
			: esquerda.compareToIgnoreCase(direita);
		return switch (comparacao.group(2)) {
			case "===", "==" -> ordem == 0;
			case "!==", "!=" -> ordem != 0;
			case ">" -> ordem > 0;
			case "<" -> ordem < 0;
			case ">=" -> ordem >= 0;
			case "<=" -> ordem <= 0;
			default -> false;
		};
	}

	private String valor(String bruto, Map<String, Object> dados, String mensagem) {
		String texto = bruto.trim();
		if (texto.startsWith("'") && texto.endsWith("'") && texto.length() >= 2)
			return texto.substring(1, texto.length() - 1);
		if (texto.startsWith("\"") && texto.endsWith("\"") && texto.length() >= 2)
			return texto.substring(1, texto.length() - 1);
		if ("&mensagem".equalsIgnoreCase(texto)) return mensagem.trim().toLowerCase();
		if (texto.startsWith("&")) return String.valueOf(resolver(dados, texto.substring(1)));
		Matcher funcao = FUNCAO.matcher(texto);
		if (funcao.matches()) return aplicarFuncao(funcao.group(1), valor(funcao.group(2), dados, mensagem));
		return texto;
	}

	private Object resolver(Map<String, Object> dados, String caminho) {
		Object atual = dados;
		for (String parte : caminho.replace('[', '.').replace("]", "").split("\\.")) {
			if (atual instanceof Map<?, ?> mapa) atual = mapa.get(parte);
			else if (atual instanceof java.util.List<?> lista && parte.matches("[0-9]+")) {
				int indice = Integer.parseInt(parte);
				atual = indice < lista.size() ? lista.get(indice) : null;
			} else if (atual instanceof JsonNode no) atual = no.path(parte);
			else return null;
		}
		return atual;
	}

	private String substituir(String texto, Map<String, Object> dados, String mensagem) {
		Matcher busca = VARIAVEL.matcher(texto.replace("&mensagem", mensagem));
		StringBuffer saida = new StringBuffer();
		while (busca.find()) {
			Object valor = resolver(dados, busca.group(1));
			busca.appendReplacement(saida, Matcher.quoteReplacement(valor == null ? "" : String.valueOf(valor)));
		}
		busca.appendTail(saida);
		String convertido = saida.toString();
		Matcher funcoes = FUNCAO.matcher(convertido);
		StringBuffer formatado = new StringBuffer();
		while (funcoes.find()) funcoes.appendReplacement(formatado, Matcher.quoteReplacement(
			aplicarFuncao(funcoes.group(1), funcoes.group(2))));
		funcoes.appendTail(formatado);
		return formatado.toString();
	}

	private String aplicarFuncao(String nome, String valor) {
		return switch (nome) {
			case "somenteNumero" -> valor.replaceAll("[^0-9]", "");
			case "somenteAlphaNumerico" -> valor.replaceAll("[^a-zA-Z0-9]", "");
			case "formatarData" -> valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
				? valor.substring(8) + "/" + valor.substring(5, 7) + "/" + valor.substring(0, 4)
				: valor;
			default -> valor;
		};
	}

	private void executarScript(JsonNode configuracao, Map<String, Object> dados, Contexto contexto) {
		String tipo = configuracao.path("tipo").asText();
		if ("transferir_setor".equals(tipo)) {
			long setor = configuracao.path("setor").path("id").asLong(0);
			if (setor > 0) dados.put("SetorId", setor);
			return;
		}
		if (!"gerar_atendimento".equals(tipo)) return;
		Object definido = dados.get("SetorId");
		long setor = definido == null ? 0 : Long.parseLong(String.valueOf(definido));
		if (setor > 0) {
			Integer existe = banco.queryForObject("""
				SELECT count(*) FROM chat_setor WHERE id=:setor AND empresa_id=:empresa AND ativo
				""", new MapSqlParameterSource().addValue("setor", setor)
					.addValue("empresa", contexto.empresa()), Integer.class);
			if (existe == null || existe != 1) throw new IllegalStateException("Setor do bot indisponível");
		}
		int atualizados = banco.update("""
			UPDATE chat_atendimento SET setor_id=:setor,estado='PENDENTE',atualizado_em=NOW()
			WHERE id=:atendimento AND empresa_id=:empresa AND atendente_id IS NULL
			""", new MapSqlParameterSource().addValue("setor", setor == 0 ? null : setor)
				.addValue("atendimento", contexto.atendimento()).addValue("empresa", contexto.empresa()));
		if (atualizados != 1) throw new IllegalStateException("Atendimento já assumido");
		dados.put("AtendimentoId", contexto.atendimento().toString());
	}

	private void executarIntegracao(JsonNode configuracao, Map<String, Object> dados, String mensagem) {
		JsonNode remoto = configuracao.path("restData");
		try {
			String endereco = substituir(remoto.path("url").asText(""), dados, mensagem);
			URI uri = URI.create(endereco);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
					|| uri.getUserInfo() != null) throw new IllegalArgumentException("Endereço HTTPS obrigatório");
			for (InetAddress resolvido : InetAddress.getAllByName(uri.getHost()))
				if (resolvido.isAnyLocalAddress() || resolvido.isLoopbackAddress()
						|| resolvido.isLinkLocalAddress() || resolvido.isSiteLocalAddress())
					throw new IllegalArgumentException("Endereço privado indisponível");
			JsonNode consultaBruta = remoto.path("queryParameters");
			String parametros = consultaBruta.asText("");
			if (consultaBruta.isArray()) {
				StringBuilder montada = new StringBuilder();
				for (JsonNode parametro : consultaBruta) {
					String nome = parametro.path("nome").asText("");
					if (!nome.matches("[A-Za-z0-9_.-]{1,100}")) continue;
					if (!montada.isEmpty()) montada.append('&');
					montada.append(URLEncoder.encode(nome, StandardCharsets.UTF_8)).append('=')
						.append(URLEncoder.encode(substituir(parametro.path("valor").asText(""),
							dados, mensagem), StandardCharsets.UTF_8));
				}
				parametros = montada.toString();
			}
			if (remoto.path("enviarQueryParameters").asBoolean() && !parametros.isBlank()) {
				StringBuilder consulta = new StringBuilder(endereco);
				consulta.append(endereco.contains("?") ? '&' : '?');
				consulta.append(substituir(parametros, dados, mensagem));
				uri = URI.create(consulta.toString());
			}
			String metodo = remoto.path("metodo").asText("GET").toUpperCase();
			if (!metodo.matches("GET|POST|PUT|PATCH|DELETE")) throw new IllegalArgumentException("Método inválido");
			JsonNode corpoBruto = remoto.path("body");
			String corpo = substituir(corpoBruto.isTextual() ? corpoBruto.asText()
				: corpoBruto.isMissingNode() || corpoBruto.isNull() ? ""
				: json.writeValueAsString(corpoBruto), dados, mensagem);
			HttpRequest.Builder pedido = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
			if (remoto.path("enviarHeaders").asBoolean()) {
				JsonNode cabecalhos = remoto.path("headers");
				if (cabecalhos.isObject()) for (var propriedade : cabecalhos.properties())
					adicionarCabecalho(pedido, propriedade.getKey(), propriedade.getValue().asText(), dados, mensagem);
				if (cabecalhos.isArray()) for (JsonNode cabecalho : cabecalhos)
					adicionarCabecalho(pedido, cabecalho.path("chave").asText(),
						cabecalho.path("valor").asText(), dados, mensagem);
			}
			pedido.method(metodo, corpo.isBlank() ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(corpo));
			HttpResponse<String> resposta = cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) throw new IllegalStateException("HTTP " + resposta.statusCode());
			Object retorno = resposta.body().isBlank() ? null : json.readValue(resposta.body(), Object.class);
			dados.put("SucessoRest", true);
			String variavel = configuracao.path("variavel").asText("");
			if (variavel.matches("[A-Za-z][A-Za-z0-9_]{0,79}")) dados.put(variavel.toUpperCase(), retorno);
			else dados.put("RetornoRest", retorno);
		} catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			dados.put("SucessoRest", false);
		}
	}

	private void adicionarCabecalho(HttpRequest.Builder pedido, String nome, String valor,
			Map<String, Object> dados, String mensagem) {
		if (nome.matches("[A-Za-z0-9-]{1,100}") && !nome.equalsIgnoreCase("Host"))
			pedido.header(nome, substituir(valor, dados, mensagem));
	}
}
