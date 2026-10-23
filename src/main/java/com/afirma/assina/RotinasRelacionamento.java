package com.afirma.assina;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RotinasRelacionamento {
	private static final Logger log = LoggerFactory.getLogger(RotinasRelacionamento.class);
	private static final String AVISO_BOT = "Olá, percebi que você não pode responder agora.\n\nQuando quiser, mande Oi para começarmos um novo atendimento.";
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final ChatTransporte transporte;
	private final Path diretorio;

	public RotinasRelacionamento(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			ChatTransporte transporte,
			@Value("${APP_CHAT_ARQUIVOS_DIR:./chat-arquivos}") String diretorio) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.transporte = transporte;
		this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	@Scheduled(cron = "0 */10 * * * *", zone = "America/Sao_Paulo")
	public void encerrarBotsInativos() {
		List<Map<String, Object>> vencidas = transacoes.execute(estado -> banco.queryForList("""
			UPDATE chat_bot_sessao sessao SET estado='CANCELADA',atualizada_em=NOW()
			FROM chat_contato contato
			WHERE sessao.contato_id=contato.id AND sessao.empresa_id=contato.empresa_id
			AND sessao.estado='ATIVA' AND sessao.atualizada_em <= NOW()-INTERVAL '30 minutes'
			RETURNING sessao.canal_id,contato.telefone,sessao.id
			""", dados()));
		if (vencidas == null) return;
		for (Map<String, Object> sessao : vencidas) {
			try {
				transporte.enviar((UUID) sessao.get("canal_id"), (String) sessao.get("telefone"), AVISO_BOT);
			} catch (RuntimeException erro) {
				log.warn("Aviso de inatividade não enviado para sessão {}", sessao.get("id"));
			}
		}
	}

	@Scheduled(cron = "0 0 1 * * *", zone = "America/Sao_Paulo")
	public void limparLogs() {
		banco.update("DELETE FROM chat_log_api WHERE criado_em < NOW()-INTERVAL '30 days'", dados());
		banco.update("""
			DELETE FROM chat_webhook_entrega WHERE criada_em < NOW()-INTERVAL '30 days'
			AND estado IN ('ENTREGUE','FALHOU')
			""", dados());
	}

	@Scheduled(cron = "0 0 2 * * *", zone = "America/Sao_Paulo")
	public void limparArquivosSemReferencia() {
		List<Map<String, Object>> arquivos = banco.queryForList("""
			SELECT arquivo.id,arquivo.chave_armazenamento FROM chat_arquivo arquivo
			WHERE arquivo.criado_em < NOW()-INTERVAL '2 days'
			AND NOT EXISTS (SELECT 1 FROM chat_mensagem WHERE arquivo_id=arquivo.id)
			AND NOT EXISTS (SELECT 1 FROM chat_mensagem_rapida WHERE arquivo_id=arquivo.id)
			AND NOT EXISTS (SELECT 1 FROM chat_modelo_mensagem WHERE arquivo_id=arquivo.id)
			AND NOT EXISTS (SELECT 1 FROM chat_campanha WHERE arquivo_id=arquivo.id)
			LIMIT 200
			""", dados());
		for (Map<String, Object> arquivo : arquivos) {
			UUID id = (UUID) arquivo.get("id");
			int removidos = banco.update("""
				DELETE FROM chat_arquivo WHERE id=:id
				AND NOT EXISTS (SELECT 1 FROM chat_mensagem WHERE arquivo_id=:id)
				AND NOT EXISTS (SELECT 1 FROM chat_mensagem_rapida WHERE arquivo_id=:id)
				AND NOT EXISTS (SELECT 1 FROM chat_modelo_mensagem WHERE arquivo_id=:id)
				AND NOT EXISTS (SELECT 1 FROM chat_campanha WHERE arquivo_id=:id)
				""", dados("id", id));
			if (removidos != 1) continue;
			String chave = (String) arquivo.get("chave_armazenamento");
			if (!id.toString().equals(chave)) continue;
			try { Files.deleteIfExists(diretorio.resolve(chave)); }
			catch (IOException erro) { log.warn("Arquivo local não removido: {}", id); }
		}
	}

	@Scheduled(cron = "0 30 2 * * *", zone = "America/Sao_Paulo")
	public void limparTokensExpirados() {
		banco.update("DELETE FROM chat_token_notificacao WHERE expira_em < NOW()", dados());
	}
}
