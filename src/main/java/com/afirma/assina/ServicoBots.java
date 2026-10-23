package com.afirma.assina;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoBots {
	private static final Logger log = LoggerFactory.getLogger(ServicoBots.class);
	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;
	private final ObjectMapper json;
	private final ChatTransporte transporte;
	private final MotorFluxoBot motor;
	private final HttpClient clienteExterno = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

	public ServicoBots(NamedParameterJdbcTemplate banco, EmpresaAtual empresas,
			ObjectMapper json, ChatTransporte transporte, MotorFluxoBot motor) {
		this.banco = banco;
		this.empresas = empresas;
		this.json = json;
		this.transporte = transporte;
		this.motor = motor;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	private JsonNode validarJson(String texto, boolean fluxo) {
		if (texto == null || texto.isBlank() || texto.length() > 100000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON obrigatório ou muito grande");
		try {
			JsonNode valor = json.readTree(texto);
			if (!valor.isObject() || fluxo && (!valor.path("nodes").isArray() || !valor.path("edges").isArray()))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estrutura do fluxo inválida");
			return valor;
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON inválido", erro);
		}
	}

	public List<Map<String, Object>> listar(Authentication usuario) {
		return banco.queryForList("""
			SELECT bot.id,bot.canal_id,canal.nome AS canal_nome,bot.nome,bot.provedor,
			bot.ativa,bot.funcionamento,bot.fluxo,bot.endereco_externo,
			bot.criada_em,bot.atualizada_em,
			(SELECT count(*) FROM chat_bot_sessao sessao WHERE sessao.definicao_id=bot.id
			 AND sessao.estado='ATIVA') AS sessoes_ativas
			FROM chat_bot_definicao bot JOIN chat_canal canal ON canal.id=bot.canal_id
			WHERE bot.empresa_id=:empresa ORDER BY bot.nome,bot.id
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> salvar(Authentication usuario, Long id, UUID canal, String nome,
			String provedor, boolean ativo, String funcionamento, String fluxo, String enderecoExterno) {
		long empresa = empresas.identificar(usuario);
		if (canal == null || nome == null || nome.isBlank() || nome.length() > 100
				|| !List.of("INTERNO", "N8N").contains(provedor)
				|| "N8N".equals(provedor) && (enderecoExterno == null || enderecoExterno.length() > 2000))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Definição de bot inválida");
		if ("N8N".equals(provedor)) validarDestino(enderecoExterno);
		validarJson(funcionamento, false);
		JsonNode fluxoValidado = validarJson(fluxo, true);
		if ("INTERNO".equals(provedor)) motor.validar(fluxoValidado);
		Integer canais = banco.queryForObject("""
			SELECT count(*) FROM chat_canal WHERE id=:canal AND empresa_id=:empresa AND ativo
			""", dados("canal", canal, "empresa", empresa), Integer.class);
		if (canais == null || canais != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal indisponível");
		MapSqlParameterSource valores = dados("id", id, "empresa", empresa, "canal", canal,
			"nome", nome.trim(), "provedor", provedor, "ativo", ativo,
			"funcionamento", funcionamento, "fluxo", fluxo,
			"endereco", "N8N".equals(provedor) ? enderecoExterno : null);
		if (id == null) return banco.queryForMap("""
			INSERT INTO chat_bot_definicao(empresa_id,canal_id,nome,provedor,ativa,
			funcionamento,fluxo,endereco_externo)
			VALUES (:empresa,:canal,:nome,:provedor,:ativo,
			CAST(:funcionamento AS jsonb),CAST(:fluxo AS jsonb),:endereco)
			RETURNING id,canal_id,nome,provedor,ativa,funcionamento,fluxo,endereco_externo
			""", valores);
		Map<String, Object> anterior;
		try {
			anterior = banco.queryForMap("""
				SELECT canal_id,provedor,ativa,fluxo,funcionamento,endereco_externo
				WHERE id=:id AND empresa_id=:empresa FOR UPDATE
				""", valores);
		} catch (EmptyResultDataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot não encontrado");
		}
		Map<String, Object> atualizado = banco.queryForMap("""
			UPDATE chat_bot_definicao SET canal_id=:canal,nome=:nome,provedor=:provedor,
			ativa=:ativo,funcionamento=CAST(:funcionamento AS jsonb),
			fluxo=CAST(:fluxo AS jsonb),endereco_externo=:endereco,atualizada_em=NOW()
			WHERE id=:id AND empresa_id=:empresa
			RETURNING id,canal_id,nome,provedor,ativa,funcionamento,fluxo,endereco_externo
			""", valores);
		if (!ativo || !canal.equals(anterior.get("canal_id")) || !provedor.equals(anterior.get("provedor"))
				|| !ler(anterior.get("fluxo")).equals(fluxoValidado)
				|| !ler(anterior.get("funcionamento")).equals(validarJson(funcionamento, false))
				|| !java.util.Objects.equals(anterior.get("endereco_externo"), enderecoExterno))
			banco.update("""
				UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=NOW()
				WHERE definicao_id=:id AND empresa_id=:empresa AND estado='ATIVA'
				""", valores);
		return atualizado;
	}

	@Transactional
	public void excluir(Authentication usuario, long id) {
		MapSqlParameterSource valores = dados("id", id, "empresa", empresas.identificar(usuario));
		banco.update("DELETE FROM chat_bot_sessao WHERE definicao_id=:id AND empresa_id=:empresa", valores);
		if (banco.update("DELETE FROM chat_bot_definicao WHERE id=:id AND empresa_id=:empresa", valores) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot não encontrado");
	}

	public List<Map<String, Object>> sessoes(Authentication usuario, long definicao) {
		return banco.queryForList("""
			SELECT sessao.id,sessao.etapa,sessao.estado,sessao.dados,sessao.mensagens,
			sessao.criada_em,sessao.atualizada_em,contato.nome AS contato_nome,
			contato.telefone AS contato_telefone
			FROM chat_bot_sessao sessao JOIN chat_contato contato ON contato.id=sessao.contato_id
			WHERE sessao.definicao_id=:definicao AND sessao.empresa_id=:empresa
			ORDER BY sessao.atualizada_em DESC LIMIT 200
			""", dados("definicao", definicao, "empresa", empresas.identificar(usuario)));
	}

	public void cancelarSessao(Authentication usuario, UUID sessao) {
		if (banco.update("""
			UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=NOW()
			WHERE id=:id AND empresa_id=:empresa AND estado='ATIVA'
			""", dados("id", sessao, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão ativa não encontrada");
	}

	public record RespostaBot(long empresa, UUID canal, long contato, UUID atendimento,
			String telefone, String texto) {}

	private JsonNode ler(Object valor) {
		try { return json.readTree(String.valueOf(valor)); }
		catch (Exception erro) { throw new IllegalStateException("Fluxo de bot armazenado inválido", erro); }
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> lerDados(Object valor) {
		try { return json.readValue(String.valueOf(valor), Map.class); }
		catch (Exception erro) { throw new IllegalStateException("Dados da sessão inválidos", erro); }
	}

	private String serializar(Object valor) {
		try { return json.writeValueAsString(valor); }
		catch (Exception erro) { throw new IllegalStateException("Dados do bot inválidos", erro); }
	}

	private void validarDestino(String endereco) {
		try {
			if (endereco == null || endereco.length() > 2000) throw new IllegalArgumentException();
			URI uri = URI.create(endereco);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
					|| uri.getUserInfo() != null) throw new IllegalArgumentException();
			for (InetAddress resolvido : InetAddress.getAllByName(uri.getHost()))
				if (resolvido.isAnyLocalAddress() || resolvido.isLoopbackAddress()
						|| resolvido.isLinkLocalAddress() || resolvido.isSiteLocalAddress()
						|| resolvido.isMulticastAddress()) throw new IllegalArgumentException();
		} catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço HTTPS público obrigatório");
		}
	}

	private void acionarFluxoExterno(String endereco, UUID sessao, UUID canal,
			String telefone, String mensagem, boolean inicio) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() {
				try {
					validarDestino(endereco);
					Map<String, Object> corpo = inicio
						? Map.of("canal", canal, "sessao", sessao, "telefone", telefone,
							"mensagem", mensagem)
						: Map.of("sessao", sessao, "mensagem", mensagem);
					HttpRequest pedido = HttpRequest.newBuilder(URI.create(endereco))
						.timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(serializar(corpo))).build();
					HttpResponse<Void> resposta = clienteExterno.send(pedido,
						HttpResponse.BodyHandlers.discarding());
					if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
						throw new IllegalStateException("HTTP " + resposta.statusCode());
				} catch (Exception erro) {
					if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
					log.warn("Fluxo externo da sessão {} indisponível: {}", sessao, erro.getMessage());
				}
			}
		});
	}

	private Map<String, Object> sessaoExterna(Authentication usuario, UUID sessao) {
		List<Map<String, Object>> registros = banco.queryForList("""
			SELECT sessao.id,sessao.empresa_id,sessao.canal_id,sessao.contato_id,
			contato.telefone,atendimento.id AS atendimento_id
			FROM chat_bot_sessao sessao JOIN chat_bot_definicao bot
			ON bot.id=sessao.definicao_id AND bot.empresa_id=sessao.empresa_id
			JOIN chat_contato contato ON contato.id=sessao.contato_id
			LEFT JOIN LATERAL (
			 SELECT id FROM chat_atendimento WHERE canal_id=sessao.canal_id
			 AND contato_id=sessao.contato_id AND estado<>'FINALIZADO'
			 ORDER BY iniciado_em DESC LIMIT 1
			) atendimento ON TRUE
			WHERE sessao.id=:sessao AND sessao.empresa_id=:empresa
			AND sessao.estado='ATIVA' AND bot.provedor='N8N'
			""", dados("sessao", sessao, "empresa", empresas.identificar(usuario)));
		if (registros.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão externa não encontrada");
		return registros.getFirst();
	}

	public void definirContinuacao(Authentication usuario, UUID sessao, String endereco) {
		sessaoExterna(usuario, sessao);
		if (endereco != null && !endereco.isBlank()) validarDestino(endereco);
		banco.update("""
			UPDATE chat_bot_sessao SET dados=jsonb_set(dados,'{urlContinuacao}',
			to_jsonb(CAST(:endereco AS text)),true),atualizada_em=NOW()
			WHERE id=:sessao AND empresa_id=:empresa
			""", dados("sessao", sessao, "empresa", empresas.identificar(usuario),
				"endereco", endereco == null ? "" : endereco));
	}

	public void enviarMensagemExterna(Authentication usuario, UUID sessao, String texto) {
		if (texto == null || texto.isBlank() || texto.length() > 4096)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensagem inválida");
		Map<String, Object> registro = sessaoExterna(usuario, sessao);
		if (registro.get("atendimento_id") == null)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento não encontrado");
		enviarResposta(new RespostaBot(((Number) registro.get("empresa_id")).longValue(),
			(UUID) registro.get("canal_id"), ((Number) registro.get("contato_id")).longValue(),
			(UUID) registro.get("atendimento_id"), (String) registro.get("telefone"), texto));
	}

	public void finalizarExterno(Authentication usuario, UUID sessao) {
		sessaoExterna(usuario, sessao);
		banco.update("""
			UPDATE chat_bot_sessao SET estado='CONCLUIDA',atualizada_em=NOW()
			WHERE id=:sessao AND empresa_id=:empresa
			""", dados("sessao", sessao, "empresa", empresas.identificar(usuario)));
	}

	public UUID gerarAtendimentoExterno(Authentication usuario, UUID sessao, Long setor) {
		Map<String, Object> registro = sessaoExterna(usuario, sessao);
		long empresa = ((Number) registro.get("empresa_id")).longValue();
		if (setor != null) {
			Integer existe = banco.queryForObject("""
				SELECT count(*) FROM chat_setor WHERE id=:setor AND empresa_id=:empresa AND ativo
				""", dados("setor", setor, "empresa", empresa), Integer.class);
			if (existe == null || existe != 1)
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setor inválido");
		}
		UUID atendimento = (UUID) registro.get("atendimento_id");
		if (atendimento == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento não encontrado");
		int atualizados = banco.update("""
			UPDATE chat_atendimento SET setor_id=:setor,estado='PENDENTE',atualizado_em=NOW()
			WHERE id=:atendimento AND empresa_id=:empresa AND atendente_id IS NULL
			""", dados("setor", setor, "atendimento", atendimento, "empresa", empresa));
		if (atualizados != 1)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento já assumido");
		banco.update("""
			UPDATE chat_bot_sessao SET dados=jsonb_set(dados,'{AtendimentoId}',
			to_jsonb(CAST(:atendimento AS text)),true),atualizada_em=NOW()
			WHERE id=:sessao AND empresa_id=:empresa
			""", dados("sessao", sessao, "empresa", empresa,
				"atendimento", atendimento.toString()));
		return atendimento;
	}

	private boolean emFuncionamento(JsonNode funcionamento, String fuso) {
		if (funcionamento.path("Integral").asBoolean()) return true;
		ZonedDateTime agora = ZonedDateTime.now(ZoneId.of(fuso));
		DayOfWeek dia = agora.getDayOfWeek();
		String[] nomes = {"", "Segunda", "Terca", "Quarta", "Quinta", "Sexta", "Sabado"};
		String nome = dia == DayOfWeek.SUNDAY ? "Domingo" : nomes[dia.getValue()];
		JsonNode horario = funcionamento.path(nome);
		if (!horario.path("Habilitado").asBoolean()) return false;
		try {
			LocalTime inicio = LocalTime.parse(horario.path("HoraInicio").asText());
			LocalTime fim = LocalTime.parse(horario.path("HoraFim").asText());
			LocalTime minuto = agora.toLocalTime().withSecond(0).withNano(0);
			boolean dentro = !minuto.isBefore(inicio) && !minuto.isAfter(fim);
			return horario.path("Inverter").asBoolean() ? !dentro : dentro;
		} catch (RuntimeException erro) { return false; }
	}

	@Transactional
	public RespostaBot processarEntrada(UUID canal, String telefone, String texto,
			String identificadorMensagem) {
		if (texto == null || texto.isBlank()) return null;
		List<Map<String, Object>> conversas = banco.queryForList("""
			SELECT contato.id AS contato_id,contato.nome AS contato_nome,contato.empresa_id,atendimento.id AS atendimento_id,
			atendimento.estado,atendimento.atendente_id,empresa.fuso_horario
			FROM chat_contato contato JOIN chat_atendimento atendimento
			ON atendimento.contato_id=contato.id AND atendimento.empresa_id=contato.empresa_id
			JOIN empresa ON empresa.id=contato.empresa_id
			WHERE atendimento.canal_id=:canal AND contato.telefone=:telefone
			AND atendimento.estado<>'FINALIZADO' ORDER BY atendimento.iniciado_em DESC LIMIT 1
			""", dados("canal", canal, "telefone", telefone));
		if (conversas.isEmpty() || conversas.getFirst().get("atendente_id") != null) return null;
		Map<String, Object> conversa = conversas.getFirst();
		long empresa = ((Number) conversa.get("empresa_id")).longValue();
		long contato = ((Number) conversa.get("contato_id")).longValue();
		UUID atendimento = (UUID) conversa.get("atendimento_id");
		MotorFluxoBot.Contexto contexto = new MotorFluxoBot.Contexto(empresa, canal, contato,
			atendimento, telefone, (String) conversa.get("contato_nome"));
		MapSqlParameterSource valores = dados("empresa", empresa, "canal", canal,
			"contato", contato, "texto", texto);
		Long mensagem = banco.queryForObject("""
			SELECT id FROM chat_mensagem WHERE canal_id=:canal
			AND identificador_externo=:externo
			""", dados("canal", canal, "externo", identificadorMensagem), Long.class);
		List<Map<String, Object>> sessoes = banco.queryForList("""
			SELECT sessao.id,sessao.etapa,sessao.dados,bot.fluxo,bot.provedor,
			bot.endereco_externo FROM chat_bot_sessao sessao
			JOIN chat_bot_definicao bot ON bot.id=sessao.definicao_id
			WHERE sessao.empresa_id=:empresa AND sessao.canal_id=:canal
			AND sessao.contato_id=:contato AND sessao.estado='ATIVA' FOR UPDATE OF sessao
			""", valores);
		if (!sessoes.isEmpty()) {
			Map<String, Object> sessao = sessoes.getFirst();
			if ("N8N".equals(sessao.get("provedor"))) {
				Map<String, Object> contextoExterno = lerDados(sessao.get("dados"));
				String continuacao = String.valueOf(contextoExterno.getOrDefault("urlContinuacao", ""));
				boolean iniciar = continuacao.isBlank();
				String endereco = iniciar ? (String) sessao.get("endereco_externo") : continuacao;
				if (!iniciar) contextoExterno.remove("urlContinuacao");
				banco.update("""
					UPDATE chat_bot_sessao SET dados=CAST(:dados AS jsonb),
					mensagens=mensagens || jsonb_build_array(:mensagem),atualizada_em=NOW()
					WHERE id=:id AND empresa_id=:empresa
					""", dados("id", sessao.get("id"), "empresa", empresa,
					"dados", serializar(contextoExterno), "mensagem", mensagem));
				acionarFluxoExterno(endereco, (UUID) sessao.get("id"), canal, telefone, texto, iniciar);
				return null;
			}
			MotorFluxoBot.Resultado retorno = motor.avancar(ler(sessao.get("fluxo")),
				(String) sessao.get("etapa"), texto, lerDados(sessao.get("dados")), contexto);
			banco.update("""
				UPDATE chat_bot_sessao SET etapa=:etapa,dados=CAST(:dados AS jsonb),
				mensagens=mensagens || jsonb_build_array(:mensagem),
				estado=:estado,atualizada_em=NOW()
				WHERE id=:id AND empresa_id=:empresa
				""", dados("id", sessao.get("id"), "empresa", empresa,
				"mensagem", mensagem,
				"etapa", retorno.etapa(), "dados", serializar(retorno.variaveis()),
				"estado", retorno.concluida() ? "CONCLUIDA" : "ATIVA"));
			return retorno.texto().isBlank() ? null : new RespostaBot(empresa, canal, contato,
				atendimento, telefone, retorno.texto());
		}
		List<Map<String, Object>> definicoes = banco.queryForList("""
			SELECT id,funcionamento,fluxo,provedor,endereco_externo FROM chat_bot_definicao
			WHERE empresa_id=:empresa AND canal_id=:canal AND ativa
			ORDER BY id DESC
			""", valores);
		for (Map<String, Object> definicao : definicoes) {
			if (!emFuncionamento(ler(definicao.get("funcionamento")),
					(String) conversa.get("fuso_horario"))) continue;
			MotorFluxoBot.Resultado retorno = "N8N".equals(definicao.get("provedor")) ? null
				: motor.avancar(ler(definicao.get("fluxo")), null, texto, Map.of(), contexto);
			UUID sessao = UUID.randomUUID();
			banco.update("""
				INSERT INTO chat_bot_sessao(id,empresa_id,definicao_id,canal_id,contato_id,etapa,
				estado,dados,mensagens)
				VALUES (:id,:empresa,:definicao,:canal,:contato,:etapa,:estado,
				CAST(:dados AS jsonb),jsonb_build_array(:mensagem))
				""", dados("id", sessao, "empresa", empresa,
				"definicao", definicao.get("id"), "canal", canal,
				"contato", contato, "etapa", retorno == null ? null : retorno.etapa(),
				"estado", retorno != null && retorno.concluida() ? "CONCLUIDA" : "ATIVA",
				"dados", retorno == null ? "{}" : serializar(retorno.variaveis()),
				"mensagem", mensagem));
			if (retorno == null) {
				acionarFluxoExterno((String) definicao.get("endereco_externo"), sessao,
					canal, telefone, texto, true);
				return null;
			}
			return retorno.texto().isBlank() ? null : new RespostaBot(empresa, canal, contato,
				atendimento, telefone, retorno.texto());
		}
		return null;
	}

	public void enviarResposta(RespostaBot resposta) {
		if (resposta == null) return;
		long mensagem = ((Number) banco.queryForMap("""
			INSERT INTO chat_mensagem(empresa_id,atendimento_id,contato_id,canal_id,
			sentido,estado,texto,origem,destinatario,enviada_por_bot)
			VALUES (:empresa,:atendimento,:contato,:canal,'SAIDA','ENVIANDO',:texto,
			'BOT',:telefone,TRUE)
			RETURNING id
			""", dados("empresa", resposta.empresa(), "atendimento", resposta.atendimento(),
				"contato", resposta.contato(), "canal", resposta.canal(),
				"texto", resposta.texto(), "telefone", resposta.telefone())).get("id")).longValue();
		banco.update("""
			UPDATE chat_bot_sessao SET mensagens=mensagens || jsonb_build_array(:mensagem)
			WHERE id=(SELECT id FROM chat_bot_sessao WHERE canal_id=:canal
			AND contato_id=:contato ORDER BY atualizada_em DESC LIMIT 1)
			""", dados("canal", resposta.canal(), "contato", resposta.contato(),
				"mensagem", mensagem));
		try {
			String externo = transporte.enviar(resposta.canal(), resposta.telefone(), resposta.texto());
			banco.update("""
				UPDATE chat_mensagem SET estado='ENVIADA',identificador_externo=:externo,
				enviada_em=NOW() WHERE id=:id
				""", dados("id", mensagem, "externo", externo));
		} catch (RuntimeException erro) {
			banco.update("UPDATE chat_mensagem SET estado='FALHOU' WHERE id=:id",
				dados("id", mensagem));
			log.warn("Resposta automática não enviada para atendimento {}", resposta.atendimento());
		}
	}
}
