package com.afirma.assina;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ChatServico {
	private static final Logger log = LoggerFactory.getLogger(ChatServico.class);
	private final NamedParameterJdbcTemplate banco;
	private final ChatTransporte transporte;
	private final ServicoInteligenciaConversacional inteligencia;

	public ChatServico(NamedParameterJdbcTemplate banco, ChatTransporte transporte,
			ServicoInteligenciaConversacional inteligencia) {
		this.banco = banco;
		this.transporte = transporte;
		this.inteligencia = inteligencia;
	}

	private static MapSqlParameterSource parametros(Object... pares) {
		MapSqlParameterSource parametros = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2) {
			parametros.addValue((String) pares[indice], pares[indice + 1]);
		}
		return parametros;
	}

	private Map<String, Object> um(String sql, MapSqlParameterSource parametros) {
		try {
			return banco.queryForMap(sql, parametros);
		} catch (EmptyResultDataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado");
		}
	}

	private void exigir(boolean condicao, HttpStatus estado, String motivo) {
		if (!condicao) throw new ResponseStatusException(estado, motivo);
	}

	@Transactional
	public Map<String, Object> criarSetor(String nome) {
		exigir(nome != null && !nome.isBlank(), HttpStatus.BAD_REQUEST, "Nome do setor obrigatório");
		return um("INSERT INTO chat_setor(nome) VALUES (:nome) RETURNING *",
				parametros("nome", nome.trim()));
	}

	public List<Map<String, Object>> setores() {
		return banco.queryForList("SELECT * FROM chat_setor ORDER BY nome", parametros());
	}

	@Transactional
	public void atualizarSetor(long id, String nome, boolean ativo) {
		exigir(nome != null && !nome.isBlank(), HttpStatus.BAD_REQUEST, "Nome do setor obrigatório");
		exigir(banco.update("""
				UPDATE chat_setor SET nome = :nome, ativo = :ativo WHERE id = :id
				""", parametros("id", id, "nome", nome.trim(), "ativo", ativo)) == 1,
				HttpStatus.NOT_FOUND, "Setor não encontrado");
		if (!ativo) {
			banco.update("UPDATE chat_canal SET setor_padrao_id = NULL WHERE setor_padrao_id = :id",
					parametros("id", id));
			banco.update("""
					UPDATE chat_atendimento SET setor_id = NULL, atualizado_em = NOW()
					WHERE setor_id = :id AND estado = 'PENDENTE'
					""", parametros("id", id));
		}
	}

	@Transactional
	public Map<String, Object> criarAtendente(String identidade, String nome, List<Long> setores) {
		exigir(identidade != null && !identidade.isBlank() && nome != null && !nome.isBlank(),
				HttpStatus.BAD_REQUEST, "Identidade e nome obrigatórios");
		Map<String, Object> atendente = um("""
				INSERT INTO chat_atendente(identidade, nome) VALUES (:identidade, :nome)
				RETURNING *
				""", parametros("identidade", identidade.trim(), "nome", nome.trim()));
		long id = ((Number) atendente.get("id")).longValue();
		if (setores != null) for (Long setor : setores) vincularSetor(id, setor);
		return atendente;
	}

	public List<Map<String, Object>> atendentes() {
		return banco.queryForList("SELECT * FROM chat_atendente ORDER BY nome", parametros());
	}

	@Transactional
	public void vincularSetor(long atendente, long setor) {
		um("SELECT id FROM chat_atendente WHERE id = :id AND ativo = TRUE", parametros("id", atendente));
		um("SELECT id FROM chat_setor WHERE id = :id AND ativo = TRUE", parametros("id", setor));
		banco.update("""
				INSERT INTO chat_atendente_setor(atendente_id, setor_id)
				VALUES (:atendente, :setor) ON CONFLICT DO NOTHING
				""", parametros("atendente", atendente, "setor", setor));
	}

	@Transactional
	public void desvincularSetor(long atendente, long setor) {
		banco.update("""
				DELETE FROM chat_atendente_setor WHERE atendente_id = :atendente AND setor_id = :setor
				""", parametros("atendente", atendente, "setor", setor));
		List<Map<String, Object>> abertos = banco.queryForList("""
				SELECT id FROM chat_atendimento WHERE atendente_id = :atendente
				AND setor_id = :setor AND estado = 'EM_ANDAMENTO' FOR UPDATE
				""", parametros("atendente", atendente, "setor", setor));
		banco.update("""
				UPDATE chat_atendimento SET atendente_id = NULL, estado = 'PENDENTE', atualizado_em = NOW()
				WHERE atendente_id = :atendente AND setor_id = :setor AND estado = 'EM_ANDAMENTO'
				""", parametros("atendente", atendente, "setor", setor));
		for (Map<String, Object> aberto : abertos)
			acao((UUID) aberto.get("id"), "SISTEMA", "REENCAMINHADO", "Atendente saiu do setor");
	}

	@Transactional
	public void atualizarAtendente(long id, String nome, boolean ativo) {
		exigir(nome != null && !nome.isBlank(), HttpStatus.BAD_REQUEST, "Nome obrigatório");
		exigir(banco.update("""
				UPDATE chat_atendente SET nome = :nome, ativo = :ativo,
				disponibilidade = CASE WHEN :ativo THEN disponibilidade ELSE 'AUSENTE' END
				WHERE id = :id
				""", parametros("id", id, "nome", nome.trim(), "ativo", ativo)) == 1,
				HttpStatus.NOT_FOUND, "Atendente não encontrado");
		if (!ativo) reencaminharAtendimentos(id);
	}

	@Transactional
	public void desativarAtendente(long id) {
		exigir(banco.update("""
				UPDATE chat_atendente SET ativo = FALSE, disponibilidade = 'AUSENTE'
				WHERE id = :id
				""", parametros("id", id)) == 1, HttpStatus.NOT_FOUND, "Atendente não encontrado");
		reencaminharAtendimentos(id);
	}

	private void reencaminharAtendimentos(long id) {
		List<Map<String, Object>> abertos = banco.queryForList("""
				SELECT id FROM chat_atendimento WHERE atendente_id = :id AND estado = 'EM_ANDAMENTO' FOR UPDATE
				""", parametros("id", id));
		banco.update("""
				UPDATE chat_atendimento SET atendente_id = NULL, estado = 'PENDENTE', atualizado_em = NOW()
				WHERE atendente_id = :id AND estado = 'EM_ANDAMENTO'
				""", parametros("id", id));
		for (Map<String, Object> atendimento : abertos)
			acao((UUID) atendimento.get("id"), "SISTEMA", "REENCAMINHADO", "Atendente desativado");
	}

	@Transactional
	public void disponibilidade(Authentication usuario, String estado) {
		exigir(List.of("DISPONIVEL", "OCUPADO", "AUSENTE").contains(estado),
				HttpStatus.BAD_REQUEST, "Disponibilidade inválida");
		exigir(banco.update("""
				UPDATE chat_atendente SET disponibilidade = :estado
				WHERE identidade = :identidade AND ativo = TRUE
				""", parametros("estado", estado, "identidade", usuario.getName())) == 1,
				HttpStatus.FORBIDDEN, "Identidade sem cadastro de atendente");
	}

	@Transactional
	public Map<String, Object> criarCanal(String nome, String telefone, Long setor,
			Integer inatividade, String encerramento) {
		exigir(nome != null && !nome.isBlank() && telefone != null && telefone.matches("[0-9]{8,15}")
				&& (inatividade == null || inatividade > 0),
				HttpStatus.BAD_REQUEST, "Nome ou telefone inválido");
		if (setor != null)
			um("SELECT id FROM chat_setor WHERE id = :id AND ativo = TRUE", parametros("id", setor));
		UUID id = UUID.randomUUID();
		return um("""
				INSERT INTO chat_canal(id, nome, telefone, setor_padrao_id, inatividade_minutos,
				finalizar_por_inatividade, mensagem_encerramento, mensagem_inatividade)
				VALUES (:id, :nome, :telefone, :setor, :inatividade,
				:finalizar, :encerramento, :encerramento) RETURNING *
				""", parametros("id", id, "nome", nome.trim(), "telefone", telefone,
					"setor", setor, "inatividade", inatividade, "finalizar", inatividade != null,
					"encerramento", encerramento));
	}

	public List<Map<String, Object>> canais() {
		return banco.queryForList("SELECT * FROM chat_canal WHERE ativo = TRUE ORDER BY nome", parametros());
	}

	public List<Map<String, Object>> canaisDisponiveis() {
		return banco.queryForList("""
				SELECT id, nome, telefone, conexao FROM chat_canal WHERE ativo = TRUE ORDER BY nome
				""", parametros());
	}

	public List<UUID> canaisAtivos() {
		return banco.query("SELECT id FROM chat_canal WHERE ativo = TRUE AND provedor = 'BAILEYS'", parametros(),
				(resultado, linha) -> resultado.getObject("id", UUID.class));
	}

	public record AvisoEncerramento(UUID atendimento, UUID canal, String telefone, String texto) {}

	@Transactional
	public List<AvisoEncerramento> finalizarInativos() {
		List<Map<String, Object>> vencidos = banco.queryForList("""
				SELECT a.id, a.canal_id, contato.telefone,
				COALESCE(canal.mensagem_inatividade, canal.mensagem_encerramento) AS mensagem_encerramento
				FROM chat_atendimento a
				JOIN chat_canal canal ON canal.id = a.canal_id
				JOIN chat_contato contato ON contato.id = a.contato_id
				WHERE a.estado = 'EM_ANDAMENTO' AND canal.ativo = TRUE
				AND canal.finalizar_por_inatividade AND canal.inatividade_minutos IS NOT NULL
				AND a.ultima_mensagem_cliente_em IS NOT NULL
				AND a.ultima_mensagem_cliente_em <= NOW() - (canal.inatividade_minutos * INTERVAL '1 minute')
				ORDER BY a.ultima_mensagem_cliente_em LIMIT 100 FOR UPDATE OF a SKIP LOCKED
				""", parametros());
		for (Map<String, Object> vencido : vencidos) {
			UUID id = (UUID) vencido.get("id");
			banco.update("""
					UPDATE chat_atendimento SET estado = 'FINALIZADO', finalizado_em = NOW(),
					contexto_ia = NULL, atualizado_em = NOW() WHERE id = :id
					""", parametros("id", id));
			acao(id, "SISTEMA", "FINALIZADO_INATIVO", null);
		}
		return vencidos.stream().map(vencido -> new AvisoEncerramento(
				(UUID) vencido.get("id"), (UUID) vencido.get("canal_id"),
				(String) vencido.get("telefone"), (String) vencido.get("mensagem_encerramento"))).toList();
	}

	public void registrarAvisoEncerramento(AvisoEncerramento aviso, String externo) {
		banco.update("""
				INSERT INTO chat_mensagem(empresa_id, atendimento_id, contato_id, canal_id,
				identificador_externo, sentido, estado, texto, destinatario)
				SELECT empresa_id,id,contato_id,canal_id,:externo,'SAIDA','ENVIADA',:texto,:telefone
				FROM chat_atendimento WHERE id=:atendimento
				""", parametros("atendimento", aviso.atendimento(), "canal", aviso.canal(),
					"externo", externo, "texto", aviso.texto(), "telefone", aviso.telefone()));
	}

	public void enviarAvisoEncerramento(AvisoEncerramento aviso) {
		if (aviso.texto() == null || aviso.texto().isBlank()) return;
		try {
			String externo = transporte.enviar(aviso.canal(), aviso.telefone(), aviso.texto());
			registrarAvisoEncerramento(aviso, externo);
		} catch (RuntimeException erro) {
			log.warn("Aviso de encerramento não enviado para atendimento {}", aviso.atendimento());
		}
	}

	@Transactional
	public void atualizarCanal(UUID id, String nome, Long setor, Integer inatividade, String encerramento) {
		exigir(nome != null && !nome.isBlank() && (inatividade == null || inatividade > 0),
				HttpStatus.BAD_REQUEST, "Configuração de canal inválida");
		if (setor != null)
			um("SELECT id FROM chat_setor WHERE id = :id AND ativo = TRUE", parametros("id", setor));
		exigir(banco.update("""
				UPDATE chat_canal SET nome = :nome, setor_padrao_id = :setor,
				inatividade_minutos = :inatividade,
				finalizar_por_inatividade = :finalizar,
				mensagem_encerramento = :encerramento,
				mensagem_inatividade = :encerramento,
				atualizado_em = NOW() WHERE id = :id
				""", parametros("id", id, "nome", nome.trim(), "setor", setor,
					"inatividade", inatividade, "finalizar", inatividade != null,
					"encerramento", encerramento)) == 1,
				HttpStatus.NOT_FOUND, "Canal não encontrado");
	}

	public void conferirCanalAtivo(UUID id) {
		um("SELECT id FROM chat_canal WHERE id = :id AND ativo = TRUE", parametros("id", id));
	}

	public String provedorCanal(UUID id) {
		return (String) um("SELECT provedor FROM chat_canal WHERE id=:id", parametros("id", id))
				.get("provedor");
	}

	@Transactional
	public UUID iniciar(UUID canal, String telefone, String nome, Long setor, Authentication usuario) {
		exigir(telefone != null && telefone.matches("[0-9]{8,15}"),
				HttpStatus.BAD_REQUEST, "Telefone inválido");
		Map<String, Object> canalRegistrado = um("""
				SELECT id, empresa_id, setor_padrao_id FROM chat_canal
				WHERE id = :id AND ativo = TRUE FOR UPDATE
				""", parametros("id", canal));
		long empresa = ((Number) canalRegistrado.get("empresa_id")).longValue();
		Long setorEscolhido = setor == null ? (Long) canalRegistrado.get("setor_padrao_id") : setor;
		long atendente = agente(usuario);
		conferirSetor(atendente, setorEscolhido);
		Map<String, Object> contato = um("""
				INSERT INTO chat_contato(empresa_id, telefone, nome)
				VALUES (:empresa, :telefone, :nome)
				ON CONFLICT (empresa_id, telefone) DO UPDATE SET nome = COALESCE(NULLIF(EXCLUDED.nome, ''), chat_contato.nome)
				RETURNING id
				""", parametros("empresa", empresa, "telefone", telefone, "nome", nome));
		UUID id = UUID.randomUUID();
		banco.update("""
			INSERT INTO chat_atendimento(id, empresa_id, canal_id, contato_id,
			setor_id, atendente_id, estado)
			VALUES (:id, :empresa, :canal, :contato, :setor, :atendente, 'EM_ANDAMENTO')
				""", parametros("id", id, "canal", canal, "contato", contato.get("id"),
					"empresa", empresa, "setor", setorEscolhido, "atendente", atendente));
		acao(id, usuario.getName(), "CRIADO", "Atendimento iniciado pelo atendente");
		return id;
	}

	@Transactional
	public void ativarCanal(UUID id, boolean ativo) {
		exigir(banco.update("""
				UPDATE chat_canal SET ativo = :ativo,
				conexao = CASE WHEN :ativo AND provedor='META' THEN 'CONECTADO' ELSE 'DESCONECTADO' END,
				atualizado_em = NOW()
				WHERE id = :id
				""", parametros("id", id, "ativo", ativo)) == 1,
				HttpStatus.NOT_FOUND, "Canal não encontrado");
	}

	public String qr(UUID canal) {
		um("SELECT id FROM chat_canal WHERE id = :id AND ativo = TRUE", parametros("id", canal));
		return transporte.qr(canal);
	}

	@Transactional
	public void estadoCanal(UUID id, String estado) {
		exigir(List.of("DESCONECTADO", "CONECTANDO", "CONECTADO", "QR_PENDENTE").contains(estado),
				HttpStatus.BAD_REQUEST, "Estado de canal inválido");
		banco.update("""
				UPDATE chat_canal SET conexao = CASE WHEN ativo THEN :estado ELSE 'DESCONECTADO' END,
				atualizado_em = NOW() WHERE id = :id
				""", parametros("id", id, "estado", estado));
	}

	@Transactional
	public boolean receber(UUID canal, String identificador, String telefone, String nome, String texto) {
		exigir(identificador != null && !identificador.isBlank() && telefone != null
				&& telefone.matches("[0-9]{8,15}") && texto != null && !texto.isBlank(),
				HttpStatus.BAD_REQUEST, "Mensagem inválida");
		Map<String, Object> dadosCanal = um("""
				SELECT id, empresa_id, setor_padrao_id FROM chat_canal
				WHERE id = :id AND ativo = TRUE FOR UPDATE
				""", parametros("id", canal));
		long empresa = ((Number) dadosCanal.get("empresa_id")).longValue();
		Long repetida = banco.queryForObject("""
				SELECT COUNT(*) FROM chat_mensagem WHERE canal_id = :canal AND identificador_externo = :externo
				""", parametros("canal", canal, "externo", identificador), Long.class);
		if (repetida != null && repetida > 0) return false;
		Map<String, Object> contato = um("""
			INSERT INTO chat_contato(empresa_id, telefone, nome) VALUES (:empresa, :telefone, :nome)
				ON CONFLICT (empresa_id, telefone) DO UPDATE SET nome = COALESCE(NULLIF(EXCLUDED.nome, ''), chat_contato.nome)
				RETURNING id
			""", parametros("empresa", empresa, "telefone", telefone, "nome", nome));
		long contatoId = ((Number) contato.get("id")).longValue();
		List<Map<String, Object>> abertos = banco.queryForList("""
				SELECT id FROM chat_atendimento
				WHERE canal_id = :canal AND contato_id = :contato
				AND empresa_id = :empresa AND estado <> 'FINALIZADO' FOR UPDATE
				""", parametros("canal", canal, "contato", contatoId, "empresa", empresa));
		UUID atendimento;
		if (abertos.isEmpty()) {
			atendimento = UUID.randomUUID();
			banco.update("""
				INSERT INTO chat_atendimento(id, empresa_id, canal_id, contato_id, setor_id, estado)
				VALUES (:id, :empresa, :canal, :contato, :setor, 'PENDENTE')
				""", parametros("id", atendimento, "canal", canal, "contato", contatoId,
						"empresa", empresa, "setor", dadosCanal.get("setor_padrao_id")));
			acao(atendimento, "SISTEMA", "CRIADO", "Mensagem recebida");
		} else {
			atendimento = (UUID) abertos.getFirst().get("id");
		}
		banco.update("""
				INSERT INTO chat_mensagem(empresa_id, atendimento_id, contato_id, canal_id,
				identificador_externo, sentido, estado, texto, remetente, ocorrida_em)
				VALUES (:empresa, :atendimento, :contato, :canal, :externo,
				'ENTRADA', 'RECEBIDA', :texto, :telefone, NOW())
				""", parametros("atendimento", atendimento, "canal", canal,
					"empresa", empresa, "contato", contatoId,
					"externo", identificador, "texto", texto, "telefone", telefone));
		banco.update("""
			UPDATE chat_atendimento SET atualizado_em = NOW(),
			mensagens_nao_lidas = mensagens_nao_lidas + 1,
			ultima_mensagem_cliente_em = NOW() WHERE id = :id
			""",
				parametros("id", atendimento));
		return true;
	}

	public List<Map<String, Object>> fila(Authentication usuario) {
		boolean gestor = usuario.getAuthorities().stream()
				.anyMatch(permissao -> permissao.getAuthority().equals("SCOPE_chat.manage"));
		String filtro = gestor ? "" : """
				AND (a.atendente_id = (SELECT id FROM chat_atendente WHERE identidade = :identidade AND ativo)
				OR (a.estado = 'PENDENTE' AND (a.setor_id IS NULL OR EXISTS (
				SELECT 1 FROM chat_atendente_setor vinculo JOIN chat_atendente agente
				ON agente.id = vinculo.atendente_id
				WHERE agente.identidade = :identidade AND agente.ativo AND vinculo.setor_id = a.setor_id))))
				""";
		return banco.queryForList("""
				SELECT a.id, a.canal_id, a.contato_id, a.setor_id, a.atendente_id, a.estado,
				       a.iniciado_em, a.atualizado_em, c.telefone, c.nome AS contato_nome
				FROM chat_atendimento a JOIN chat_contato c ON c.id = a.contato_id
				WHERE a.estado <> 'FINALIZADO'
				AND NOT EXISTS (SELECT 1 FROM chat_bot_sessao sessao
				 WHERE sessao.canal_id=a.canal_id AND sessao.contato_id=a.contato_id
				 AND sessao.estado='ATIVA' AND NOT jsonb_exists(sessao.dados,'AtendimentoId'))
				""" + filtro + " ORDER BY a.atualizado_em DESC LIMIT 200",
				parametros("identidade", usuario.getName()));
	}

	private Map<String, Object> atendimento(UUID id) {
		return um("SELECT * FROM chat_atendimento WHERE id = :id FOR UPDATE", parametros("id", id));
	}

	private long agente(Authentication usuario) {
		Map<String, Object> registro = um("""
				SELECT id FROM chat_atendente WHERE identidade = :identidade AND ativo = TRUE
				""", parametros("identidade", usuario.getName()));
		return ((Number) registro.get("id")).longValue();
	}

	private void conferirSetor(long atendente, Object setor) {
		if (setor == null) return;
		Long quantidade = banco.queryForObject("""
				SELECT COUNT(*) FROM chat_atendente_setor vinculo
				JOIN chat_setor setor ON setor.id = vinculo.setor_id
				WHERE vinculo.atendente_id = :atendente AND vinculo.setor_id = :setor AND setor.ativo
				""", parametros("atendente", atendente, "setor", setor), Long.class);
		exigir(quantidade != null && quantidade == 1, HttpStatus.FORBIDDEN,
				"Atendente não pertence ao setor");
	}

	@Transactional
	public void assumir(UUID id, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		exigir("PENDENTE".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento não está pendente");
		long atendente = agente(usuario);
		conferirSetor(atendente, ticket.get("setor_id"));
		banco.update("""
				UPDATE chat_atendimento SET atendente_id = :atendente, estado = 'EM_ANDAMENTO',
				atualizado_em = NOW() WHERE id = :id
				""", parametros("atendente", atendente, "id", id));
		banco.update("""
			UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=NOW()
			WHERE canal_id=:canal AND contato_id=:contato AND estado='ATIVA'
			""", parametros("canal", ticket.get("canal_id"), "contato", ticket.get("contato_id")));
		acao(id, usuario.getName(), "ASSUMIDO", null);
	}

	@Transactional
	public void transferir(UUID id, Long setor, Long destino, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirResponsavel(ticket, usuario);
		exigir(!"FINALIZADO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento finalizado");
		if (setor != null) um("SELECT id FROM chat_setor WHERE id = :id AND ativo = TRUE", parametros("id", setor));
		if (destino != null) {
			um("SELECT id FROM chat_atendente WHERE id = :id AND ativo = TRUE", parametros("id", destino));
			conferirSetor(destino, setor);
		}
		banco.update("""
				UPDATE chat_atendimento SET setor_id = :setor, atendente_id = :destino,
				estado = :estado, atualizado_em = NOW() WHERE id = :id
				""", parametros("setor", setor, "destino", destino,
					"estado", destino == null ? "PENDENTE" : "EM_ANDAMENTO", "id", id));
		acao(id, usuario.getName(), "TRANSFERIDO", "Setor: " + setor + "; atendente: " + destino);
	}

	private void conferirResponsavel(Map<String, Object> ticket, Authentication usuario) {
		if (usuario.getAuthorities().stream().anyMatch(
				permissao -> permissao.getAuthority().equals("SCOPE_chat.manage"))) return;
		Map<String, Object> agente = um("""
				SELECT id FROM chat_atendente WHERE identidade = :identidade AND ativo = TRUE
				""", parametros("identidade", usuario.getName()));
		exigir(agente.get("id").equals(ticket.get("atendente_id")), HttpStatus.FORBIDDEN,
				"Atendimento pertence a outro atendente");
	}

	private void conferirLeitura(Map<String, Object> ticket, Authentication usuario) {
		if (usuario.getAuthorities().stream().anyMatch(
				permissao -> permissao.getAuthority().equals("SCOPE_chat.manage"))) return;
		long atendente = agente(usuario);
		if (ticket.get("atendente_id") != null
				&& ((Number) ticket.get("atendente_id")).longValue() == atendente) return;
		if ("PENDENTE".equals(ticket.get("estado"))) {
			conferirSetor(atendente, ticket.get("setor_id"));
			return;
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Atendimento indisponível");
	}

	@Transactional
	public AvisoEncerramento finalizar(UUID id, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirResponsavel(ticket, usuario);
		exigir(!"FINALIZADO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento já finalizado");
		banco.update("""
				UPDATE chat_atendimento SET estado = 'FINALIZADO', finalizado_em = NOW(),
				atualizado_em = NOW() WHERE id = :id
				""", parametros("id", id));
		acao(id, usuario.getName(), "FINALIZADO", null);
		Map<String, Object> aviso = um("""
				SELECT contato.telefone, canal.mensagem_encerramento
				FROM chat_atendimento a JOIN chat_canal canal ON canal.id = a.canal_id
				JOIN chat_contato contato ON contato.id = a.contato_id WHERE a.id = :id
				""", parametros("id", id));
		return new AvisoEncerramento(id, (UUID) ticket.get("canal_id"),
				(String) aviso.get("telefone"), (String) aviso.get("mensagem_encerramento"));
	}

	public List<Map<String, Object>> mensagens(UUID id, Authentication usuario) {
		Map<String, Object> ticket = um("SELECT * FROM chat_atendimento WHERE id = :id", parametros("id", id));
		conferirLeitura(ticket, usuario);
		return banco.queryForList("""
				SELECT id, sentido, estado, texto, criado_em FROM chat_mensagem
				WHERE atendimento_id = :id ORDER BY id DESC LIMIT 200
				""", parametros("id", id));
	}

	@Transactional
	public void marcarComoLidas(UUID id, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirLeitura(ticket, usuario);
		banco.update("UPDATE chat_atendimento SET mensagens_nao_lidas = 0 WHERE id = :id",
				parametros("id", id));
	}

	@Transactional
	public void avaliar(UUID id, int nota, Authentication usuario) {
		exigir(nota >= 1 && nota <= 5, HttpStatus.BAD_REQUEST, "Avaliação inválida");
		Map<String, Object> ticket = atendimento(id);
		conferirLeitura(ticket, usuario);
		exigir("FINALIZADO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento ainda aberto");
		banco.update("UPDATE chat_atendimento SET avaliacao=:nota WHERE id=:id",
				parametros("id", id, "nota", nota));
	}

	public List<Map<String, Object>> finalizados(Authentication usuario) {
		boolean gestor = usuario.getAuthorities().stream()
				.anyMatch(permissao -> permissao.getAuthority().equals("SCOPE_chat.manage"));
		String filtro = gestor ? "" : "AND a.atendente_id = (SELECT id FROM chat_atendente WHERE identidade=:identidade AND ativo)";
		return banco.queryForList("""
			SELECT a.id,a.protocolo,a.canal_id,a.estado,a.iniciado_em,a.finalizado_em,
			a.duracao_segundos,a.espera_segundos,a.avaliacao,c.nome AS contato_nome,c.telefone
			FROM chat_atendimento a JOIN chat_contato c ON c.id=a.contato_id
			WHERE a.estado='FINALIZADO'
			""" + filtro + " ORDER BY a.finalizado_em DESC LIMIT 200",
				parametros("identidade", usuario.getName()));
	}

	public List<Map<String, Object>> indicadores(Authentication usuario) {
		exigir(usuario.getAuthorities().stream().anyMatch(
				permissao -> permissao.getAuthority().equals("SCOPE_chat.manage")),
				HttpStatus.FORBIDDEN, "Gestão necessária");
		return banco.queryForList("""
			SELECT canal.id AS canal_id,canal.nome AS canal,
			count(a.id) AS total,
			count(a.id) FILTER (WHERE a.estado='PENDENTE') AS pendentes,
			count(a.id) FILTER (WHERE a.estado='EM_ANDAMENTO') AS em_andamento,
			count(a.id) FILTER (WHERE a.estado='FINALIZADO') AS finalizados,
			avg(a.duracao_segundos) FILTER (WHERE a.estado='FINALIZADO') AS duracao_media_segundos,
			avg(a.espera_segundos) FILTER (WHERE a.estado='FINALIZADO') AS espera_media_segundos,
			avg(a.avaliacao) AS avaliacao_media
			FROM chat_canal canal LEFT JOIN chat_atendimento a ON a.canal_id=canal.id
			GROUP BY canal.id,canal.nome ORDER BY canal.nome
			""", parametros());
	}

	public List<Map<String, Object>> acoes(UUID id, Authentication usuario) {
		Map<String, Object> ticket = um("SELECT * FROM chat_atendimento WHERE id = :id", parametros("id", id));
		conferirLeitura(ticket, usuario);
		return banco.queryForList("""
				SELECT ator, tipo, descricao, criado_em FROM chat_acao
				WHERE atendimento_id = :id ORDER BY id
				""", parametros("id", id));
	}

	@Transactional
	public void reativar(UUID id, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirResponsavel(ticket, usuario);
		exigir("FINALIZADO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento não está finalizado");
		Long abertos = banco.queryForObject("""
			SELECT count(*) FROM chat_atendimento WHERE canal_id=:canal AND contato_id=:contato
			AND estado<>'FINALIZADO' AND id<>:id
			""", parametros("canal", ticket.get("canal_id"), "contato", ticket.get("contato_id"),
				"id", id), Long.class);
		exigir(abertos != null && abertos == 0, HttpStatus.CONFLICT,
				"Já existe atendimento aberto para este contato");
		banco.update("""
			UPDATE chat_atendimento SET estado='PENDENTE',atendente_id=NULL,
			finalizado_em=NULL,duracao_segundos=NULL,espera_segundos=NULL,atualizado_em=NOW()
			WHERE id=:id
			""", parametros("id", id));
		acao(id, usuario.getName(), "REATIVADO", null);
	}

	public List<Map<String, Object>> historicoContato(UUID id, Authentication usuario) {
		Map<String, Object> ticket = um("SELECT * FROM chat_atendimento WHERE id=:id",
				parametros("id", id));
		conferirLeitura(ticket, usuario);
		return banco.queryForList("""
			SELECT id,protocolo,canal_id,estado,iniciado_em,finalizado_em,
			atendente_id,setor_id,avaliacao
			FROM chat_atendimento WHERE contato_id=:contato AND empresa_id=:empresa
			ORDER BY iniciado_em DESC LIMIT 100
			""", parametros("contato", ticket.get("contato_id"), "empresa", ticket.get("empresa_id")));
	}

	public String sugerirResposta(UUID id, long agenteIa, Authentication usuario) {
		Map<String, Object> ticket = um("SELECT * FROM chat_atendimento WHERE id=:id",
				parametros("id", id));
		conferirLeitura(ticket, usuario);
		exigir(!"FINALIZADO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento finalizado");
		return inteligencia.responder(((Number) ticket.get("empresa_id")).longValue(), agenteIa, id);
	}

	@Transactional
	public void marcarMensagemLida(UUID id, long mensagem, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirLeitura(ticket, usuario);
		if (banco.update("""
			UPDATE chat_mensagem SET lida_em=NOW(),confirmacao=3
			WHERE id=:mensagem AND atendimento_id=:atendimento AND empresa_id=:empresa
			""", parametros("mensagem", mensagem, "atendimento", id,
				"empresa", ticket.get("empresa_id"))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	@Transactional
	public void ocultarMensagem(UUID id, long mensagem, Authentication usuario) {
		Map<String, Object> ticket = atendimento(id);
		conferirResponsavel(ticket, usuario);
		if (banco.update("""
			UPDATE chat_mensagem SET excluida=TRUE,texto=NULL
			WHERE id=:mensagem AND atendimento_id=:atendimento AND empresa_id=:empresa
			""", parametros("mensagem", mensagem, "atendimento", id,
				"empresa", ticket.get("empresa_id"))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		acao(id, usuario.getName(), "MENSAGEM_OCULTADA", "Mensagem " + mensagem);
	}

	public void enviar(UUID id, String texto, Authentication usuario) {
		exigir(texto != null && !texto.isBlank() && texto.length() <= 4096,
				HttpStatus.BAD_REQUEST, "Texto inválido");
		Map<String, Object> ticket = um("SELECT * FROM chat_atendimento WHERE id = :id", parametros("id", id));
		conferirResponsavel(ticket, usuario);
		exigir("EM_ANDAMENTO".equals(ticket.get("estado")), HttpStatus.CONFLICT,
				"Atendimento não está em andamento");
		UUID canal = (UUID) ticket.get("canal_id");
		Map<String, Object> contato = um("SELECT telefone FROM chat_contato WHERE id = :id",
				parametros("id", ticket.get("contato_id")));
		long mensagem = ((Number) um("""
				INSERT INTO chat_mensagem(empresa_id,atendimento_id,contato_id,canal_id,
				sentido,estado,texto,destinatario,enviada_por_atendente)
				VALUES (:empresa,:atendimento,:contato,:canal,'SAIDA','ENVIANDO',:texto,:telefone,TRUE)
				RETURNING id
				""", parametros("empresa", ticket.get("empresa_id"), "atendimento", id,
					"contato", ticket.get("contato_id"), "canal", canal,
					"telefone", contato.get("telefone"), "texto", texto)).get("id")).longValue();
		try {
			String externo = transporte.enviar(canal, (String) contato.get("telefone"), texto);
			banco.update("""
				UPDATE chat_mensagem SET estado = 'ENVIADA', identificador_externo = :externo WHERE id = :id
				""", parametros("externo", externo, "id", mensagem));
			banco.update("""
				UPDATE chat_atendimento SET atualizado_em = NOW(),
				primeira_resposta_em=COALESCE(primeira_resposta_em,NOW()) WHERE id = :id
				""",
					parametros("id", id));
		} catch (RuntimeException erro) {
			banco.update("UPDATE chat_mensagem SET estado = 'FALHOU' WHERE id = :id",
					parametros("id", mensagem));
			throw erro;
		}
	}

	private void acao(UUID atendimento, String ator, String tipo, String descricao) {
		banco.update("""
				INSERT INTO chat_acao(empresa_id, atendimento_id, ator, tipo, descricao)
				SELECT empresa_id, id, :ator, :tipo, :descricao
				FROM chat_atendimento WHERE id = :atendimento
				""", parametros("atendimento", atendimento, "ator", ator,
					"tipo", tipo, "descricao", descricao));
	}
}
