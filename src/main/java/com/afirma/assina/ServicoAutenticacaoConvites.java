package com.afirma.assina;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoAutenticacaoConvites {
	private final JdbcTemplate banco;
	private final SegredosAssinatura segredos;
	private final EnviadorCodigoConvite enviador;
	private final ServicoEvidenciasAssinatura evidencias;

	public ServicoAutenticacaoConvites(JdbcTemplate banco, SegredosAssinatura segredos,
			EnviadorCodigoConvite enviador, ServicoEvidenciasAssinatura evidencias) {
		this.banco = banco;
		this.segredos = segredos;
		this.enviador = enviador;
		this.evidencias = evidencias;
	}

	@Transactional(readOnly = true)
	public Acesso consultarAcesso(String token, String segredoSessao) {
		Convite convite = obterConvite(token, false);
		evidencias.conferir(convite.envelope());
		Sessao sessao = encontrarSessao(convite, segredoSessao);
		return new Acesso(mascararEmail(convite.email()), sessao != null, sessao == null ? null : sessao.expiraEm(), enviador.configurado(), evidencias.avisoPrivacidade(convite.envelope()), convite.nomeEmpresa(), segundosParaReenvio(convite.participante()));
	}

	// O prazo vem do banco para continuar valendo em outra aba ou após recarregar.
	private long segundosParaReenvio(UUID identificadorSignatario) {
		var prazos = banco.query("""
				SELECT ultimo_envio_em,bloqueado_ate,janela_envios_em,quantidade_envios
				FROM autenticacao_convite WHERE signatario_id=?
				""", (linha, indice) -> {
			Instant liberadoEm = Instant.EPOCH;
			if (linha.getTimestamp(1) != null) liberadoEm = linha.getTimestamp(1).toInstant().plusSeconds(60);
			if (linha.getTimestamp(2) != null && linha.getTimestamp(2).toInstant().isAfter(liberadoEm))
				liberadoEm = linha.getTimestamp(2).toInstant();
			if (linha.getInt(4) >= 5 && linha.getTimestamp(3) != null
					&& linha.getTimestamp(3).toInstant().plusSeconds(3600).isAfter(liberadoEm))
				liberadoEm = linha.getTimestamp(3).toInstant().plusSeconds(3600);
			return Math.max(0, (long) Math.ceil(java.time.Duration.between(Instant.now(), liberadoEm).toMillis() / 1000.0));
		}, identificadorSignatario);
		return prazos.isEmpty() ? 0 : prazos.getFirst();
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public void solicitarCodigo(String token) {
		segredos.exigirConfiguracao();
		Convite convite = obterConvite(token, true);
		if (!enviador.configurado()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Envio de código não configurado. Contate a empresa responsável pelo documento");
		Instant agora = Instant.now();
		banco.update("INSERT INTO autenticacao_convite(signatario_id,janela_envios_em) VALUES (?,?) ON CONFLICT DO NOTHING",
				convite.participante(), Timestamp.from(agora));
		var registros = banco.queryForList("SELECT * FROM autenticacao_convite WHERE signatario_id=? FOR UPDATE", convite.participante());
		var estado = registros.getFirst();
		Instant bloqueadoAte = instante(estado.get("bloqueado_ate"));
		Instant ultimoEnvio = instante(estado.get("ultimo_envio_em"));
		Instant janela = instante(estado.get("janela_envios_em"));
		int quantidade = ((Number) estado.get("quantidade_envios")).intValue();
		if (bloqueadoAte != null && agora.isBefore(bloqueadoAte)) throw limite();
		if (ultimoEnvio != null && agora.isBefore(ultimoEnvio.plusSeconds(60))) throw limite();
		if (agora.isBefore(janela.plusSeconds(3600)) && quantidade >= 5) throw limite();
		if (!agora.isBefore(janela.plusSeconds(3600))) { janela = agora; quantidade = 0; }
		String codigo = SegredosAssinatura.novoCodigo();
		String resumo = segredos.selar("codigo-convite:" + convite.participante(), codigo);
		// Falha no SMTP não habilita acesso nem grava um código que nunca foi enviado.
		enviador.enviar(convite.email(), convite.nomeDocumento(), codigo);
		agora = Instant.now();
		banco.update("""
				UPDATE autenticacao_convite SET resumo_codigo=?,codigo_expira_em=?,ultimo_envio_em=?,
				janela_envios_em=?,quantidade_envios=?,tentativas_incorretas=CASE WHEN bloqueado_ate IS NOT NULL THEN 0 ELSE tentativas_incorretas END,
				bloqueado_ate=NULL WHERE signatario_id=?
				""", resumo, Timestamp.from(agora.plusSeconds(600)), Timestamp.from(agora), Timestamp.from(janela), quantidade + 1,
				convite.participante());
		evidencias.registrar(convite.envelope(), convite.participante(), "CODIGO_ENVIADO", Map.of("destino", mascararEmail(convite.email()),
				"expiraEm", agora.plusSeconds(600).toString()));
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public Autenticado autenticar(String token, String codigo, String enderecoIp, String agenteUsuario) {
		segredos.exigirConfiguracao();
		Convite convite = obterConvite(token, true);
		var estados = banco.queryForList("SELECT * FROM autenticacao_convite WHERE signatario_id=? FOR UPDATE", convite.participante());
		if (estados.isEmpty()) throw codigoInvalido();
		var estado = estados.getFirst();
		Instant agora = Instant.now();
		Instant bloqueadoAte = instante(estado.get("bloqueado_ate"));
		if (bloqueadoAte != null && agora.isBefore(bloqueadoAte)) throw limite();
		Instant expiraEm = instante(estado.get("codigo_expira_em"));
		if (expiraEm == null || !agora.isBefore(expiraEm) || estado.get("resumo_codigo") == null) throw codigoInvalido();
		boolean correto = codigo != null && codigo.matches("[0-9]{8}") && SegredosAssinatura.iguais(
				(String) estado.get("resumo_codigo"), segredos.selar("codigo-convite:" + convite.participante(), codigo));
		if (!correto) {
			int tentativas = ((Number) estado.get("tentativas_incorretas")).intValue() + 1;
			banco.update("UPDATE autenticacao_convite SET tentativas_incorretas=?,bloqueado_ate=? WHERE signatario_id=?",
					tentativas, tentativas >= 5 ? Timestamp.from(agora.plusSeconds(600)) : null, convite.participante());
			evidencias.registrar(convite.envelope(), convite.participante(), "CODIGO_RECUSADO", Map.of("tentativa", tentativas,
					"enderecoIp", limitar(enderecoIp, 64)));
			throw tentativas >= 5 ? limite() : codigoInvalido();
		}
		UUID identificador = UUID.randomUUID();
		String segredoSessao = SegredosAssinatura.novoSegredo();
		Instant vencimento = agora.plusSeconds(1800);
		banco.update("UPDATE sessao_convite SET revogada_em=? WHERE signatario_id=? AND revogada_em IS NULL",
				Timestamp.from(agora), convite.participante());
		banco.update("""
				INSERT INTO sessao_convite(id,signatario_id,resumo_segredo,criada_em,expira_em,endereco_ip,agente_usuario)
				VALUES (?,?,?,?,?,?,?)
				""", identificador, convite.participante(), SegredosAssinatura.resumo(segredoSessao), Timestamp.from(agora),
				Timestamp.from(vencimento), limitar(enderecoIp, 64), limitar(agenteUsuario, 512));
		banco.update("UPDATE autenticacao_convite SET resumo_codigo=NULL,codigo_expira_em=NULL,tentativas_incorretas=0 WHERE signatario_id=?",
				convite.participante());
		evidencias.registrar(convite.envelope(), convite.participante(), "EMAIL_AUTENTICADO", Map.of("autenticacao", identificador.toString(),
				"metodo", "CODIGO_EMAIL", "enderecoIp", limitar(enderecoIp, 64), "agenteUsuario", limitar(agenteUsuario, 512),
				"expiraEm", vencimento.toString()));
		return new Autenticado(segredoSessao, vencimento);
	}

	@Transactional(readOnly = true)
	public Sessao exigirSessao(String token, String segredoSessao) {
		Sessao sessao = encontrarSessao(obterConvite(token, false), segredoSessao);
		if (sessao == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
				"Confirme o código enviado ao seu e-mail para acessar o documento");
		return sessao;
	}

	@Transactional
	public void encerrar(String token, String segredoSessao) {
		Convite convite = obterConvite(token, true);
		Sessao sessao = encontrarSessao(convite, segredoSessao);
		if (sessao == null) return;
		banco.update("UPDATE sessao_convite SET revogada_em=? WHERE id=?", Timestamp.from(Instant.now()), sessao.identificador());
		evidencias.registrar(convite.envelope(), convite.participante(), "SESSAO_ENCERRADA", Map.of("autenticacao", sessao.identificador().toString()));
	}

	public void conferirSessaoParaAceite(UUID participante, Sessao sessao) {
		if (sessao == null || !participante.equals(sessao.participante())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		Integer quantidade = banco.queryForObject("""
				SELECT count(*) FROM sessao_convite WHERE id=? AND signatario_id=? AND revogada_em IS NULL AND expira_em>CURRENT_TIMESTAMP
				""", Integer.class, sessao.identificador(), participante);
		if (quantidade == null || quantidade != 1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Confirme um novo código");
	}

	@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
	@Transactional
	public void limparSegredosExpirados() {
		banco.update("UPDATE autenticacao_convite SET resumo_codigo=NULL,codigo_expira_em=NULL WHERE codigo_expira_em<=CURRENT_TIMESTAMP");
		banco.update("UPDATE sessao_convite SET revogada_em=CURRENT_TIMESTAMP WHERE revogada_em IS NULL AND expira_em<=CURRENT_TIMESTAMP");
	}

	private Sessao encontrarSessao(Convite convite, String segredoSessao) {
		if (segredoSessao == null || !segredoSessao.matches("[A-Za-z0-9_-]{43}")) return null;
		List<Sessao> sessoes = banco.query("""
				SELECT id,criada_em,expira_em FROM sessao_convite WHERE signatario_id=? AND resumo_segredo=?
				AND revogada_em IS NULL AND expira_em>CURRENT_TIMESTAMP
				""", (linha, indice) -> new Sessao(linha.getObject(1, UUID.class), convite.participante(), convite.envelope(),
				linha.getTimestamp(2).toInstant(), linha.getTimestamp(3).toInstant()), convite.participante(), SegredosAssinatura.resumo(segredoSessao));
		return sessoes.isEmpty() ? null : sessoes.getFirst();
	}

	private Convite obterConvite(String token, boolean bloquear) {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido");
		var convites = banco.query("""
				SELECT participante.id,envelope.id,participante.email,envelope.nome_documento,envelope.expira_em,
				envelope.finalizado_em,envelope.excluido_em,envelope.cancelado_em,envelope.nome_empresa
				FROM signatario_envelope participante JOIN envelope_assinatura envelope ON envelope.id=participante.envelope_id
				WHERE participante.resumo_convite=?
				""" + (bloquear ? " FOR UPDATE OF envelope" : ""), (linha, indice) -> {
			if (linha.getTimestamp(7) != null || linha.getTimestamp(8) != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			Instant limite = linha.getTimestamp(6) == null ? linha.getTimestamp(5).toInstant() : linha.getTimestamp(6).toInstant().plusSeconds(7 * 86400);
			if (!Instant.now().isBefore(limite)) throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado");
			return new Convite(linha.getObject(1, UUID.class), linha.getObject(2, UUID.class), linha.getString(3), linha.getString(4), linha.getString(9));
		}, SegredosAssinatura.resumo(token));
		if (convites.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido");
		if (bloquear) {
			Integer aindaVigente = banco.queryForObject("SELECT count(*) FROM signatario_envelope WHERE id=? AND resumo_convite=?",
					Integer.class, convites.getFirst().participante(), SegredosAssinatura.resumo(token));
			if (aindaVigente == null || aindaVigente != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite substituído");
		}
		return convites.getFirst();
	}

	public static String mascararEmail(String email) {
		int separacao = email.lastIndexOf('@');
		if (separacao < 1) return "***";
		return email.substring(0, 1) + "***@" + email.substring(separacao + 1);
	}
	private Instant instante(Object valor) { return valor == null ? null : ((Timestamp) valor).toInstant(); }
	private String limitar(String texto, int tamanho) { return texto == null ? "" : texto.substring(0, Math.min(texto.length(), tamanho)); }
	private ResponseStatusException codigoInvalido() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código inválido ou expirado"); }
	private ResponseStatusException limite() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de códigos ou tentativas atingido. Aguarde antes de tentar novamente"); }
	private record Convite(UUID participante, UUID envelope, String email, String nomeDocumento, String nomeEmpresa) { }
	public record Sessao(UUID identificador, UUID participante, UUID envelope, Instant autenticadaEm, Instant expiraEm) { }
	public record Autenticado(String segredoSessao, Instant expiraEm) { }
	public record Acesso(String emailMascarado, boolean autenticado, Instant expiraEm, boolean envioDisponivel, String avisoPrivacidade, String nomeEmpresa, long segundosParaReenvio) { }
}
