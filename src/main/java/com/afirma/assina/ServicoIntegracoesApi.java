package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoIntegracoesApi {
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final EmpresaAtual empresas;
	private final ChatTransporte transporte;

	public ServicoIntegracoesApi(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			EmpresaAtual empresas, ChatTransporte transporte) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.empresas = empresas;
		this.transporte = transporte;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	private String resumo(String chave) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(chave.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception erro) { throw new IllegalStateException("SHA-256 indisponível", erro); }
	}

	public List<Map<String, Object>> chaves(Authentication usuario) {
		return banco.queryForList("""
			SELECT id,nome,escopos,ativa,expira_em,criada_em FROM chat_chave_integracao
			WHERE empresa_id=:empresa ORDER BY criada_em DESC
			""", dados("empresa", empresas.identificar(usuario)));
	}

	public Map<String, Object> criarChave(Authentication usuario, String nome,
			List<String> escopos, Instant expira) {
		long empresa = empresas.identificar(usuario);
		List<String> permitidos = List.of("canais.ler", "setores.ler", "contatos.escrever",
				"mensagens.enviar", "atendimentos.finalizar");
		if (nome == null || nome.isBlank() || nome.length() > 120 || escopos == null
				|| escopos.isEmpty() || escopos.stream().anyMatch(escopo -> !permitidos.contains(escopo))
				|| expira != null && !expira.isAfter(Instant.now()))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chave inválida");
		byte[] aleatorios = new byte[32];
		new SecureRandom().nextBytes(aleatorios);
		String chave = "firma_" + HexFormat.of().formatHex(aleatorios);
		UUID id = UUID.randomUUID();
		banco.update("""
			INSERT INTO chat_chave_integracao(id,empresa_id,nome,resumo_sha256,escopos,expira_em)
			VALUES (:id,:empresa,:nome,:resumo,:escopos,:expira)
			""", dados("id", id, "empresa", empresa, "nome", nome.trim(),
				"resumo", resumo(chave), "escopos", escopos.toArray(String[]::new), "expira", expira));
		return Map.of("id", id, "chave", chave);
	}

	public void revogar(Authentication usuario, UUID id) {
		if (banco.update("""
			UPDATE chat_chave_integracao SET ativa=FALSE WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	private Map<String, Object> autorizar(String chave, String escopo) {
		if (chave == null || !chave.matches("firma_[0-9a-f]{64}"))
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		List<Map<String, Object>> encontradas = banco.queryForList("""
			SELECT chave.id,chave.empresa_id FROM chat_chave_integracao chave
			JOIN empresa ON empresa.id=chave.empresa_id
			WHERE chave.resumo_sha256=:resumo AND chave.ativa AND empresa.ativa
			AND (chave.expira_em IS NULL OR chave.expira_em>NOW()) AND :escopo=ANY(chave.escopos)
			""", dados("resumo", resumo(chave), "escopo", escopo));
		if (encontradas.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		return encontradas.getFirst();
	}

	public List<Map<String, Object>> canais(String chave) {
		Map<String, Object> acesso = autorizar(chave, "canais.ler");
		return banco.queryForList("""
			SELECT id,nome,telefone,provedor,conexao FROM chat_canal
			WHERE empresa_id=:empresa AND ativo ORDER BY nome
			""", dados("empresa", acesso.get("empresa_id")));
	}

	public List<Map<String, Object>> setores(String chave) {
		Map<String, Object> acesso = autorizar(chave, "setores.ler");
		return banco.queryForList("""
			SELECT id,nome FROM chat_setor WHERE empresa_id=:empresa AND ativo ORDER BY nome
			""", dados("empresa", acesso.get("empresa_id")));
	}

	public Map<String, Object> contato(String chave, String telefone, String nome) {
		Map<String, Object> acesso = autorizar(chave, "contatos.escrever");
		if (telefone == null || !telefone.matches("[0-9]{8,15}")
				|| nome != null && nome.length() > 100)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contato inválido");
		return banco.queryForMap("""
			INSERT INTO chat_contato(empresa_id,telefone,nome) VALUES (:empresa,:telefone,:nome)
			ON CONFLICT (empresa_id,telefone) DO UPDATE
			SET nome=COALESCE(NULLIF(EXCLUDED.nome,''),chat_contato.nome)
			RETURNING id,telefone,nome
			""", dados("empresa", acesso.get("empresa_id"), "telefone", telefone, "nome", nome));
	}

	public Map<String, Object> enviar(String chave, UUID identificador, UUID canal,
			String telefone, String texto) {
		Map<String, Object> acesso = autorizar(chave, "mensagens.enviar");
		long empresa = ((Number) acesso.get("empresa_id")).longValue();
		if (canal == null || telefone == null || !telefone.matches("[0-9]{8,15}")
				|| texto == null || texto.isBlank() || texto.length() > 4096)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensagem inválida");
		UUID id = identificador == null ? UUID.randomUUID() : identificador;
		AtomicBoolean novo = new AtomicBoolean();
		Map<String, Object> envio = transacoes.execute(estado -> {
			Integer canalValido = banco.queryForObject("""
				SELECT count(*) FROM chat_canal WHERE id=:canal AND empresa_id=:empresa AND ativo
				""", dados("canal", canal, "empresa", empresa), Integer.class);
			if (canalValido == null || canalValido != 1)
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal não encontrado");
			Map<String, Object> contato = banco.queryForMap("""
				INSERT INTO chat_contato(empresa_id,telefone) VALUES (:empresa,:telefone)
				ON CONFLICT (empresa_id,telefone) DO UPDATE SET telefone=EXCLUDED.telefone RETURNING id
				""", dados("empresa", empresa, "telefone", telefone));
			novo.set(banco.update("""
				INSERT INTO chat_envio_api(id,empresa_id,canal_id,contato_id,texto)
				VALUES (:id,:empresa,:canal,:contato,:texto) ON CONFLICT DO NOTHING
				""", dados("id", id, "empresa", empresa, "canal", canal,
					"contato", contato.get("id"), "texto", texto)) == 1);
			return banco.queryForMap("""
				SELECT envio.*,contato.telefone FROM chat_envio_api envio
				JOIN chat_contato contato ON contato.id=envio.contato_id
				WHERE envio.id=:id AND envio.empresa_id=:empresa FOR UPDATE OF envio
				""", dados("id", id, "empresa", empresa));
		});
		if (!canal.equals(envio.get("canal_id")) || !telefone.equals(envio.get("telefone"))
				|| !texto.equals(envio.get("texto")))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Identificador usado com outra mensagem");
		if (!novo.get()) return envio;
		try {
			String externo = transporte.enviar(canal, telefone, texto);
			transacoes.executeWithoutResult(estado -> {
				banco.update("""
					UPDATE chat_envio_api SET estado='ENVIADO',identificador_externo=:externo,
					enviado_em=NOW(),erro=NULL WHERE id=:id AND estado='ENVIANDO'
					""", dados("id", id, "externo", externo));
				banco.update("""
					INSERT INTO chat_mensagem(empresa_id,canal_id,contato_id,api_envio_id,
					identificador_externo,sentido,estado,texto,origem,destinatario,enviada_por_api)
					VALUES (:empresa,:canal,:contato,:id,:externo,'SAIDA','ENVIADA',:texto,
					'API',:telefone,TRUE)
					ON CONFLICT (api_envio_id) DO NOTHING
					""", dados("empresa", empresa, "canal", canal, "contato", envio.get("contato_id"),
						"id", id, "externo", externo, "texto", texto, "telefone", telefone));
				banco.update("""
					INSERT INTO chat_log_api(empresa_id,chave_id,acao,recurso_id,sucesso)
					VALUES (:empresa,:chave,'mensagens.enviar',:recurso,TRUE)
					""", dados("empresa", empresa, "chave", acesso.get("id"), "recurso", id.toString()));
			});
			return Map.of("id", id, "estado", "ENVIADO", "identificadorExterno", externo);
		} catch (RuntimeException erro) {
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_envio_api SET estado='FALHOU',erro=:erro WHERE id=:id
				""", dados("id", id, "erro", erro.getMessage() == null ? "Falha no transporte" : erro.getMessage())));
			throw erro;
		}
	}

	public Map<String, Object> consultarEnvio(String chave, UUID id) {
		Map<String, Object> acesso = autorizar(chave, "mensagens.enviar");
		var envios = banco.queryForList("""
			SELECT id,estado,identificador_externo,erro,criado_em,enviado_em
			FROM chat_envio_api WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", acesso.get("empresa_id")));
		if (envios.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		return envios.getFirst();
	}

	public void finalizar(String chave, UUID atendimento) {
		Map<String, Object> acesso = autorizar(chave, "atendimentos.finalizar");
		int atualizados = banco.update("""
			UPDATE chat_atendimento SET estado='FINALIZADO',finalizado_em=NOW(),atualizado_em=NOW()
			WHERE id=:id AND empresa_id=:empresa AND estado<>'FINALIZADO'
			""", dados("id", atendimento, "empresa", acesso.get("empresa_id")));
		if (atualizados != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		banco.update("""
			INSERT INTO chat_log_api(empresa_id,chave_id,acao,recurso_id,sucesso)
			VALUES (:empresa,:chave,'atendimentos.finalizar',:recurso,TRUE)
			""", dados("empresa", acesso.get("empresa_id"), "chave", acesso.get("id"),
				"recurso", atendimento.toString()));
	}
}
