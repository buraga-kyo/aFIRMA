package com.afirma.assina;

import java.time.Duration;
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

@Component
public class ProcessadorCampanhas {
	private static final Logger registro = LoggerFactory.getLogger(ProcessadorCampanhas.class);
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final ChatTransporte transporte;
	private final ServicoCanalOficial oficial;

	public ProcessadorCampanhas(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			ChatTransporte transporte, ServicoCanalOficial oficial) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.transporte = transporte;
		this.oficial = oficial;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	@Scheduled(fixedDelay = 5000)
	public void executar() {
		transacoes.executeWithoutResult(estado -> {
			banco.update("""
				UPDATE chat_campanha SET estado='EXECUTANDO',iniciada_em=COALESCE(iniciada_em,NOW())
				WHERE estado='AGENDADA' AND (agendada_em IS NULL OR agendada_em <= NOW())
				""", dados());
			banco.update("""
				UPDATE chat_campanha_envio SET estado='PENDENTE',proxima_tentativa_em=NOW(),atualizada_em=NOW()
				WHERE estado='ENVIANDO' AND atualizada_em < NOW()-INTERVAL '5 minutes'
				AND tentativas < 3
				""", dados());
			banco.update("""
				UPDATE chat_campanha_envio SET estado='FALHOU',erro='Envio sem confirmação após três tentativas',
				atualizada_em=NOW() WHERE estado='ENVIANDO'
				AND atualizada_em < NOW()-INTERVAL '5 minutes' AND tentativas >= 3
				""", dados());
			banco.update("""
				UPDATE chat_campanha campanha SET estado='CONCLUIDA',concluida_em=NOW()
				WHERE campanha.estado='EXECUTANDO' AND NOT EXISTS (
				SELECT 1 FROM chat_campanha_envio envio WHERE envio.campanha_id=campanha.id
				AND envio.estado IN ('PENDENTE','ENVIANDO'))
				""", dados());
		});
		for (int indice = 0; indice < 20; indice++) {
			Map<String, Object> envio = transacoes.execute(estado -> reservar());
			if (envio == null) break;
			enviar(envio);
		}
	}

	private Map<String, Object> reservar() {
		List<Map<String, Object>> proximos = banco.queryForList("""
			SELECT envio.id,envio.empresa_id,envio.campanha_id,envio.tentativas,
			campanha.canal_id,campanha.texto,modelo.corpo AS texto_modelo,
			modelo.nome AS nome_modelo,modelo.idioma AS idioma_modelo,
			campanha.intervalo_segundos,contato.id AS contato_id,contato.telefone,contato.nome,canal.provedor
			FROM chat_campanha_envio envio
			JOIN chat_campanha campanha ON campanha.id=envio.campanha_id
			JOIN chat_contato contato ON contato.id=envio.contato_id AND contato.empresa_id=envio.empresa_id
			JOIN chat_canal canal ON canal.id=campanha.canal_id AND canal.empresa_id=envio.empresa_id
			JOIN empresa ON empresa.id=envio.empresa_id
			LEFT JOIN chat_modelo_mensagem modelo ON modelo.id=campanha.modelo_id
			WHERE campanha.estado='EXECUTANDO' AND canal.ativo
			AND timezone(empresa.fuso_horario,NOW())::time BETWEEN campanha.horario_inicio AND campanha.horario_fim
			AND EXTRACT(DOW FROM timezone(empresa.fuso_horario,NOW()))::smallint = ANY(campanha.dias_semana)
			AND (campanha.maximo_diario IS NULL OR (
			SELECT count(*) FROM chat_campanha_envio enviados WHERE enviados.campanha_id=campanha.id
			AND enviados.estado='ENVIADO' AND
			DATE(timezone(empresa.fuso_horario,enviados.enviado_em))=
			DATE(timezone(empresa.fuso_horario,NOW()))) < campanha.maximo_diario)
			AND (campanha.proximo_envio_em IS NULL OR campanha.proximo_envio_em <= NOW())
			AND envio.estado='PENDENTE'
			AND (envio.proxima_tentativa_em IS NULL OR envio.proxima_tentativa_em <= NOW())
			ORDER BY envio.id LIMIT 1 FOR UPDATE OF envio,campanha SKIP LOCKED
			""", dados());
		if (proximos.isEmpty()) return null;
		Map<String, Object> envio = proximos.getFirst();
		banco.update("""
			UPDATE chat_campanha_envio SET estado='ENVIANDO',tentativas=tentativas+1,
			atualizada_em=NOW() WHERE id=:id
			""", dados("id", envio.get("id")));
		banco.update("""
			UPDATE chat_campanha SET proximo_envio_em=NOW()+(:segundos * INTERVAL '1 second')
			WHERE id=:id
			""", dados("id", envio.get("campanha_id"), "segundos", envio.get("intervalo_segundos")));
		return envio;
	}

	private void enviar(Map<String, Object> envio) {
		long id = ((Number) envio.get("id")).longValue();
		String telefone = (String) envio.get("telefone");
		String conteudo = (String) (envio.get("texto") == null ? envio.get("texto_modelo") : envio.get("texto"));
		String texto = conteudo == null ? null : conteudo.replace("{{nome}}",
				(String) (envio.get("nome") == null ? "" : envio.get("nome")));
		try {
			String externo = "META".equals(envio.get("provedor"))
					? oficial.enviarModelo((UUID) envio.get("canal_id"), telefone,
							(String) envio.get("nome_modelo"), (String) envio.get("idioma_modelo"))
					: transporte.enviar((UUID) envio.get("canal_id"), telefone, texto);
			transacoes.executeWithoutResult(estado -> {
				banco.update("""
					UPDATE chat_campanha_envio SET estado='ENVIADO',identificador_externo=:externo,
					enviado_em=NOW(),atualizada_em=NOW(),erro=NULL WHERE id=:id AND estado='ENVIANDO'
					""", dados("id", id, "externo", externo));
				banco.update("""
					INSERT INTO chat_mensagem(empresa_id,canal_id,contato_id,campanha_envio_id,
					identificador_externo,sentido,estado,texto,origem,tipo,destinatario,enviada_por_api)
					VALUES (:empresa,:canal,:contato,:envio,:externo,'SAIDA','ENVIADA',
					:texto,'CAMPANHA',:tipo,:telefone,TRUE)
					ON CONFLICT (campanha_envio_id) DO NOTHING
					""", dados("empresa", envio.get("empresa_id"), "canal", envio.get("canal_id"),
						"contato", envio.get("contato_id"), "envio", id, "externo", externo,
						"texto", texto, "telefone", telefone,
						"tipo", "META".equals(envio.get("provedor")) ? "MODELO" : "TEXTO"));
				concluir((UUID) envio.get("campanha_id"));
			});
		} catch (RuntimeException erro) {
			registro.warn("Envio de campanha {} falhou: {}", id, erro.getMessage());
			transacoes.executeWithoutResult(estado -> {
				int tentativas = ((Number) envio.get("tentativas")).intValue() + 1;
				banco.update("""
					UPDATE chat_campanha_envio SET estado=:estado,erro=:erro,
					proxima_tentativa_em=NOW()+(:espera * INTERVAL '1 second'),atualizada_em=NOW()
					WHERE id=:id AND estado='ENVIANDO'
					""", dados("id", id, "estado", tentativas >= 3 ? "FALHOU" : "PENDENTE",
						"erro", erro.getMessage() == null ? "Falha no transporte" : erro.getMessage().substring(0, Math.min(500, erro.getMessage().length())),
						"espera", (int) Duration.ofMinutes(tentativas).toSeconds()));
				concluir((UUID) envio.get("campanha_id"));
			});
		}
	}

	private void concluir(UUID campanha) {
		banco.update("""
			UPDATE chat_campanha SET estado='CONCLUIDA',concluida_em=NOW()
			WHERE id=:id AND estado='EXECUTANDO' AND NOT EXISTS (
			SELECT 1 FROM chat_campanha_envio WHERE campanha_id=:id
			AND estado IN ('PENDENTE','ENVIANDO'))
			""", dados("id", campanha));
	}
}
