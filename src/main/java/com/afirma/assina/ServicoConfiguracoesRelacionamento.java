package com.afirma.assina;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoConfiguracoesRelacionamento {
	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;
	private final ObjectMapper json;

	public ServicoConfiguracoesRelacionamento(NamedParameterJdbcTemplate banco,
			EmpresaAtual empresas, ObjectMapper json) {
		this.banco = banco;
		this.empresas = empresas;
		this.json = json;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	private JsonNode validarJson(String texto, boolean lista) {
		if (texto == null || texto.length() > 32000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON inválido");
		try {
			JsonNode dados = json.readTree(texto);
			if (lista ? !dados.isArray() : !dados.isObject())
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estrutura JSON inválida");
			return dados;
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON inválido", erro); }
	}

	public List<Map<String, Object>> automacoes(Authentication usuario) {
		return banco.queryForList("""
			SELECT * FROM chat_automacao WHERE empresa_id=:empresa ORDER BY prioridade DESC,id
			""", dados("empresa", empresas.identificar(usuario)));
	}

	public Map<String, Object> criarAutomacao(Authentication usuario, String nome, String gatilho,
			String condicoes, String acoes, int prioridade) {
		long empresa = empresas.identificar(usuario);
		if (nome == null || nome.isBlank() || nome.length() > 120
				|| !"MENSAGEM_RECEBIDA".equals(gatilho) || prioridade < -1000 || prioridade > 1000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Automação inválida");
		validarJson(condicoes, false);
		JsonNode lista = validarJson(acoes, true);
		if (lista.isEmpty() || lista.size() > 20)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ações inválidas");
		for (JsonNode acao : lista) {
			String tipo = acao.path("tipo").asText();
			if (!List.of("RESPONDER", "ETIQUETAR", "TRANSFERIR", "FINALIZAR", "IA").contains(tipo))
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de ação inválido");
		}
		return banco.queryForMap("""
			INSERT INTO chat_automacao(empresa_id,nome,gatilho,condicoes,acoes,prioridade)
			VALUES (:empresa,:nome,:gatilho,CAST(:condicoes AS jsonb),CAST(:acoes AS jsonb),:prioridade)
			RETURNING *
			""", dados("empresa", empresa, "nome", nome.trim(), "gatilho", gatilho,
				"condicoes", condicoes, "acoes", acoes, "prioridade", prioridade));
	}

	public void ativarAutomacao(Authentication usuario, long id, boolean ativo) {
		if (banco.update("""
			UPDATE chat_automacao SET ativa=:ativo WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario), "ativo", ativo)) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public List<Map<String, Object>> agentes(Authentication usuario) {
		return banco.queryForList("""
			SELECT id,nome,provedor,modelo,instrucoes,ativador,temperatura,
			limite_resposta,ativa,criada_em FROM chat_agente_ia
			WHERE empresa_id=:empresa ORDER BY nome
			""", dados("empresa", empresas.identificar(usuario)));
	}

	public Map<String, Object> criarAgente(Authentication usuario, String nome, String provedor,
			String modelo, String instrucoes, String referenciaSegredo, String ativador) {
		long empresa = empresas.identificar(usuario);
		if (nome == null || nome.isBlank() || nome.length() > 120
				|| !List.of("OPENAI", "DEEPSEEK").contains(provedor)
				|| modelo == null || !modelo.matches("[a-zA-Z0-9._:/-]{1,100}")
				|| instrucoes == null || instrucoes.isBlank() || instrucoes.length() > 16000
				|| referenciaSegredo == null || !referenciaSegredo.matches("APP_IA_TOKEN_[A-Z0-9_]{1,80}")
				|| ativador == null || ativador.length() > 80)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agente inválido");
		return banco.queryForMap("""
			INSERT INTO chat_agente_ia(empresa_id,nome,provedor,modelo,instrucoes,
			referencia_segredo,ativador)
			VALUES (:empresa,:nome,:provedor,:modelo,:instrucoes,:segredo,:ativador)
			RETURNING id,nome,provedor,modelo,instrucoes,ativador,ativa,criada_em
			""", dados("empresa", empresa, "nome", nome.trim(), "provedor", provedor,
				"modelo", modelo, "instrucoes", instrucoes,
				"segredo", referenciaSegredo, "ativador", ativador));
	}

	public void ativarAgente(Authentication usuario, long id, boolean ativo) {
		if (banco.update("""
			UPDATE chat_agente_ia SET ativa=:ativo WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario), "ativo", ativo)) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public List<Map<String, Object>> integracoes(Authentication usuario) {
		return banco.queryForList("""
			SELECT id,nome,tipo,configuracao,ativa FROM chat_integracao
			WHERE empresa_id=:empresa ORDER BY nome
			""", dados("empresa", empresas.identificar(usuario)));
	}

	public Map<String, Object> criarIntegracao(Authentication usuario, String nome, String tipo,
			String configuracao, String referenciaSegredo) {
		long empresa = empresas.identificar(usuario);
		if (nome == null || nome.isBlank() || nome.length() > 120
				|| tipo == null || !tipo.matches("[A-Z_]{2,40}")
				|| referenciaSegredo != null && !referenciaSegredo.matches("APP_INTEGRACAO_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integração inválida");
		validarJson(configuracao, false);
		Integer permitido = banco.queryForObject("""
			SELECT count(*) FROM chat_assinatura_plano assinatura JOIN chat_plano plano
			ON plano.id=assinatura.plano_id WHERE assinatura.empresa_id=:empresa
			AND assinatura.estado='ATIVA' AND plano.permite_integracao
			""", dados("empresa", empresa), Integer.class);
		if (permitido == null || permitido != 1)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Plano não permite integração");
		return banco.queryForMap("""
			INSERT INTO chat_integracao(empresa_id,nome,tipo,configuracao,referencia_segredo)
			VALUES (:empresa,:nome,:tipo,CAST(:configuracao AS jsonb),:segredo)
			RETURNING id,nome,tipo,configuracao,ativa
			""", dados("empresa", empresa, "nome", nome.trim(), "tipo", tipo,
				"configuracao", configuracao, "segredo", referenciaSegredo));
	}

	public void ativarIntegracao(Authentication usuario, long id, boolean ativo) {
		if (banco.update("""
			UPDATE chat_integracao SET ativa=:ativo WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario), "ativo", ativo)) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public Map<String, Object> plano(Authentication usuario) {
		var planos = banco.queryForList("""
			SELECT plano.nome,plano.valor_centavos,plano.renovacao_dias,
			plano.limite_envios_dia,plano.limite_canais,plano.limite_atendentes,
			plano.permite_integracao,plano.permite_webhook,
			assinatura.estado,assinatura.iniciada_em,assinatura.termina_em
			FROM chat_assinatura_plano assinatura JOIN chat_plano plano ON plano.id=assinatura.plano_id
			WHERE assinatura.empresa_id=:empresa
			""", dados("empresa", empresas.identificar(usuario)));
		if (planos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado");
		return planos.getFirst();
	}
}
