package com.afirma.assina;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProcessadorAutomacoes {
	private static final Logger registro = LoggerFactory.getLogger(ProcessadorAutomacoes.class);
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final ChatTransporte transporte;
	private final ServicoInteligenciaConversacional inteligencia;
	private final ObjectMapper json;

	public ProcessadorAutomacoes(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			ChatTransporte transporte, ServicoInteligenciaConversacional inteligencia, ObjectMapper json) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.transporte = transporte;
		this.inteligencia = inteligencia;
		this.json = json;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	@Scheduled(fixedDelay = 3000)
	public void executar() {
		banco.update("""
			UPDATE chat_automacao_execucao fila SET estado='CONCLUIDA',erro='Automação desativada'
			WHERE fila.estado='PENDENTE' AND NOT EXISTS (
			SELECT 1 FROM chat_automacao configuracao
			WHERE configuracao.id=fila.automacao_id AND configuracao.ativa)
			""", dados());
		for (int indice = 0; indice < 20; indice++) {
			Map<String, Object> execucao = transacoes.execute(estado -> reservar());
			if (execucao == null) break;
			processar(execucao);
		}
	}

	private Map<String, Object> reservar() {
		List<Map<String, Object>> pendentes = banco.queryForList("""
			SELECT fila.id,fila.empresa_id,fila.atendimento_id,fila.indice_acao,
			config.condicoes,config.acoes,mensagem.texto,
			atendimento.canal_id,atendimento.estado AS estado_atendimento,contato.telefone
			FROM chat_automacao_execucao fila
			JOIN chat_automacao config ON config.id=fila.automacao_id
			JOIN chat_mensagem mensagem ON mensagem.id=fila.mensagem_id
			JOIN chat_atendimento atendimento ON atendimento.id=fila.atendimento_id
			JOIN chat_contato contato ON contato.id=atendimento.contato_id
			WHERE fila.estado IN ('PENDENTE','EXECUTANDO') AND config.ativa
			AND fila.proxima_tentativa_em <= NOW()
			ORDER BY config.prioridade DESC,fila.id LIMIT 1
			FOR UPDATE OF fila SKIP LOCKED
			""", dados());
		if (pendentes.isEmpty()) return null;
		Map<String, Object> execucao = pendentes.getFirst();
		banco.update("""
			UPDATE chat_automacao_execucao SET estado='EXECUTANDO',tentativas=tentativas+1,
			proxima_tentativa_em=NOW()+INTERVAL '2 minutes' WHERE id=:id
			""", dados("id", execucao.get("id")));
		return execucao;
	}

	private void processar(Map<String, Object> execucao) {
		long id = ((Number) execucao.get("id")).longValue();
		try {
			JsonNode condicoes = json.readTree(String.valueOf(execucao.get("condicoes")));
			String recebido = (String) execucao.get("texto");
			if (!corresponde(condicoes, recebido)) {
				concluir(id);
				return;
			}
			JsonNode acoes = json.readTree(String.valueOf(execucao.get("acoes")));
			for (int indice = ((Number) execucao.get("indice_acao")).intValue(); indice < acoes.size(); indice++) {
				JsonNode acao = acoes.path(indice);
				executarAcao(execucao, indice, acao);
				int proximo = indice + 1;
				transacoes.executeWithoutResult(estado -> banco.update("""
					UPDATE chat_automacao_execucao SET indice_acao=:indice WHERE id=:id
					""", dados("id", id, "indice", proximo)));
			}
			concluir(id);
		} catch (Exception erro) {
			registro.warn("Automação {} falhou: {}", id, erro.getMessage());
			String mensagem = erro.getMessage() == null ? "Falha de automação" : erro.getMessage();
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_automacao_execucao SET estado='FALHOU',erro=:erro WHERE id=:id
				""", dados("id", id, "erro", mensagem.substring(0, Math.min(500, mensagem.length())))));
		}
	}

	private boolean corresponde(JsonNode condicoes, String recebido) {
		String texto = normalizar(recebido == null ? "" : recebido);
		String igual = condicoes.path("igual").asText("");
		String contem = condicoes.path("contem").asText("");
		String inicio = condicoes.path("iniciaCom").asText("");
		return (igual.isBlank() || texto.equals(normalizar(igual)))
				&& (contem.isBlank() || texto.contains(normalizar(contem)))
				&& (inicio.isBlank() || texto.startsWith(normalizar(inicio)));
	}

	private String normalizar(String valor) {
		return Normalizer.normalize(valor, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
	}

	private void executarAcao(Map<String, Object> execucao, int indice, JsonNode acao) {
		long empresa = ((Number) execucao.get("empresa_id")).longValue();
		long id = ((Number) execucao.get("id")).longValue();
		UUID atendimento = (UUID) execucao.get("atendimento_id");
		switch (acao.path("tipo").asText()) {
			case "RESPONDER" -> responder(execucao, indice, acao.path("texto").asText(), "AUTOMACAO");
			case "IA" -> {
				long agente = acao.path("agenteId").asLong();
				if (agente < 1) throw new IllegalArgumentException("Agente inválido");
				String texto = inteligencia.responder(empresa, agente, atendimento);
				responder(execucao, indice, texto, "IA");
			}
			case "ETIQUETAR" -> {
				long etiqueta = acao.path("etiquetaId").asLong();
				if (etiqueta < 1) throw new IllegalArgumentException("Etiqueta inválida");
				transacoes.executeWithoutResult(estado -> banco.update("""
					INSERT INTO chat_atendimento_etiqueta(atendimento_id,etiqueta_id,empresa_id)
					SELECT :atendimento,id,:empresa FROM chat_etiqueta
					WHERE id=:etiqueta AND empresa_id=:empresa AND ativa ON CONFLICT DO NOTHING
					""", dados("atendimento", atendimento, "etiqueta", etiqueta, "empresa", empresa)));
			}
			case "TRANSFERIR" -> {
				long setor = acao.path("setorId").asLong();
				if (setor < 1) throw new IllegalArgumentException("Setor inválido");
				transacoes.executeWithoutResult(estado -> banco.update("""
					UPDATE chat_atendimento SET setor_id=:setor,atendente_id=NULL,
					estado='PENDENTE',atualizado_em=NOW()
					WHERE id=:atendimento AND empresa_id=:empresa AND estado<>'FINALIZADO'
					AND EXISTS (SELECT 1 FROM chat_setor WHERE id=:setor AND empresa_id=:empresa AND ativo)
					""", dados("atendimento", atendimento, "setor", setor, "empresa", empresa)));
			}
			case "FINALIZAR" -> transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_atendimento SET estado='FINALIZADO',finalizado_em=NOW(),atualizado_em=NOW()
				WHERE id=:atendimento AND empresa_id=:empresa AND estado<>'FINALIZADO'
				""", dados("atendimento", atendimento, "empresa", empresa)));
			default -> throw new IllegalArgumentException("Ação inválida na automação " + id);
		}
	}

	private void responder(Map<String, Object> execucao, int indice, String texto, String origem) {
		if (texto == null || texto.isBlank() || texto.length() > 4096)
			throw new IllegalArgumentException("Resposta inválida");
		long id = ((Number) execucao.get("id")).longValue();
		UUID atendimento = (UUID) execucao.get("atendimento_id");
		UUID canal = (UUID) execucao.get("canal_id");
		long empresa = ((Number) execucao.get("empresa_id")).longValue();
		List<Map<String, Object>> existentes = banco.queryForList("""
			SELECT id,estado FROM chat_mensagem WHERE automacao_execucao_id=:id
			AND automacao_acao_indice=:indice
			""", dados("id", id, "indice", indice));
		if (!existentes.isEmpty()) {
			if ("ENVIADA".equals(existentes.getFirst().get("estado"))) return;
			throw new IllegalStateException("Envio anterior sem confirmação; revisão necessária");
		}
		long mensagem = transacoes.execute(estado -> ((Number) banco.queryForMap("""
			INSERT INTO chat_mensagem(empresa_id,atendimento_id,canal_id,sentido,estado,texto,
			origem,automacao_execucao_id,automacao_acao_indice)
			VALUES (:empresa,:atendimento,:canal,'SAIDA','ENVIANDO',:texto,:origem,:execucao,:indice)
			RETURNING id
			""", dados("empresa", empresa, "atendimento", atendimento, "canal", canal,
				"texto", texto, "origem", origem, "execucao", id, "indice", indice)).get("id")).longValue());
		try {
			String externo = transporte.enviar(canal, (String) execucao.get("telefone"), texto);
			transacoes.executeWithoutResult(estado -> {
				banco.update("""
					UPDATE chat_mensagem SET estado='ENVIADA',identificador_externo=:externo WHERE id=:id
					""", dados("id", mensagem, "externo", externo));
				banco.update("""
					UPDATE chat_atendimento SET primeira_resposta_em=COALESCE(primeira_resposta_em,NOW()),
					atualizado_em=NOW() WHERE id=:id
					""", dados("id", atendimento));
			});
		} catch (RuntimeException erro) {
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_mensagem SET estado='FALHOU' WHERE id=:id
				""", dados("id", mensagem)));
			throw erro;
		}
	}

	private void concluir(long id) {
		transacoes.executeWithoutResult(estado -> banco.update("""
			UPDATE chat_automacao_execucao SET estado='CONCLUIDA',erro=NULL WHERE id=:id
			""", dados("id", id)));
	}
}
