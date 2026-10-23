package com.afirma.assina;

import java.time.Instant;
import java.time.LocalTime;
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

@Service
public class RelacionamentoServico {
	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;

	public RelacionamentoServico(NamedParameterJdbcTemplate banco, EmpresaAtual empresas) {
		this.banco = banco;
		this.empresas = empresas;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	private static void exigir(boolean condicao, String mensagem) {
		if (!condicao) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
	}

	private Map<String, Object> um(String consulta, MapSqlParameterSource valores) {
		try { return banco.queryForMap(consulta, valores); }
		catch (EmptyResultDataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado");
		}
	}

	public List<Map<String, Object>> contatos(Authentication usuario, String busca, int limite) {
		long empresa = empresas.identificar(usuario);
		exigir(limite > 0 && limite <= 200, "Limite inválido");
		return banco.queryForList("""
			SELECT id, telefone, nome, email, informacoes, avatar_chave, identificador_whatsapp, criado_em
			FROM chat_contato WHERE empresa_id = :empresa
			AND (CAST(:busca AS text) IS NULL OR nome ILIKE :busca OR telefone LIKE :busca)
			ORDER BY id DESC LIMIT :limite
			""", dados("empresa", empresa, "busca", busca == null || busca.isBlank() ? null : "%" + busca.trim() + "%", "limite", limite));
	}

	@Transactional
	public Map<String, Object> salvarContato(Authentication usuario, Long id, String telefone,
			String nome, String email, String informacoes) {
		long empresa = empresas.identificar(usuario);
		exigir(telefone != null && telefone.matches("[0-9]{8,15}"), "Telefone inválido");
		exigir(nome == null || nome.length() <= 100, "Nome muito longo");
		exigir(email == null || email.length() <= 100, "Email muito longo");
		String informacoesValidadas = informacoes == null ? "{}" : informacoes;
		exigir(informacoesValidadas.length() <= 32000, "Informações muito longas");
		if (id == null) return um("""
			INSERT INTO chat_contato(empresa_id, telefone, nome, email, informacoes)
			VALUES (:empresa, :telefone, :nome, :email, CAST(:informacoes AS jsonb))
			ON CONFLICT (empresa_id, telefone) DO UPDATE SET
			nome = COALESCE(EXCLUDED.nome, chat_contato.nome),
			email = COALESCE(EXCLUDED.email, chat_contato.email),
			informacoes = EXCLUDED.informacoes RETURNING *
			""", dados("empresa", empresa, "telefone", telefone, "nome", nome,
				"email", email, "informacoes", informacoesValidadas));
		return um("""
			UPDATE chat_contato SET telefone = :telefone, nome = :nome, email = :email,
			informacoes = CAST(:informacoes AS jsonb) WHERE id = :id AND empresa_id = :empresa RETURNING *
			""", dados("id", id, "empresa", empresa, "telefone", telefone,
				"nome", nome, "email", email, "informacoes", informacoesValidadas));
	}

	public List<Map<String, Object>> etiquetas(Authentication usuario) {
		return banco.queryForList("SELECT * FROM chat_etiqueta WHERE empresa_id = :empresa ORDER BY nome",
				dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> salvarEtiqueta(Authentication usuario, Long id, String nome, String cor) {
		long empresa = empresas.identificar(usuario);
		exigir(nome != null && !nome.isBlank() && nome.length() <= 80, "Nome inválido");
		exigir(cor != null && cor.matches("#[0-9A-Fa-f]{6}"), "Cor inválida");
		if (id == null) return um("""
			INSERT INTO chat_etiqueta(empresa_id,nome,cor) VALUES (:empresa,:nome,:cor) RETURNING *
			""", dados("empresa", empresa, "nome", nome.trim(), "cor", cor));
		return um("""
			UPDATE chat_etiqueta SET nome=:nome,cor=:cor WHERE id=:id AND empresa_id=:empresa RETURNING *
			""", dados("id", id, "empresa", empresa, "nome", nome.trim(), "cor", cor));
	}

	public void desativarEtiqueta(Authentication usuario, long id) {
		if (banco.update("""
			UPDATE chat_etiqueta SET ativa=FALSE WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	@Transactional
	public void etiquetar(Authentication usuario, UUID atendimento, long etiqueta, boolean incluir) {
		long empresa = empresas.identificar(usuario);
		um("SELECT id FROM chat_atendimento WHERE id=:id AND empresa_id=:empresa",
				dados("id", atendimento, "empresa", empresa));
		um("SELECT id FROM chat_etiqueta WHERE id=:id AND empresa_id=:empresa AND ativa",
				dados("id", etiqueta, "empresa", empresa));
		if (incluir) banco.update("""
			INSERT INTO chat_atendimento_etiqueta(atendimento_id,etiqueta_id,empresa_id)
			VALUES (:atendimento,:etiqueta,:empresa) ON CONFLICT DO NOTHING
			""", dados("atendimento", atendimento, "etiqueta", etiqueta, "empresa", empresa));
		else banco.update("""
			DELETE FROM chat_atendimento_etiqueta WHERE atendimento_id=:atendimento
			AND etiqueta_id=:etiqueta AND empresa_id=:empresa
			""", dados("atendimento", atendimento, "etiqueta", etiqueta, "empresa", empresa));
	}

	public List<Map<String, Object>> listas(Authentication usuario) {
		return banco.queryForList("""
			SELECT lista.*, count(destino.contato_id) AS total_contatos
			FROM chat_lista_contatos lista LEFT JOIN chat_lista_destinatario destino
			ON destino.lista_id=lista.id WHERE lista.empresa_id=:empresa
			GROUP BY lista.id ORDER BY lista.nome
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> criarLista(Authentication usuario, String nome, String descricao) {
		long empresa = empresas.identificar(usuario);
		exigir(nome != null && !nome.isBlank() && nome.length() <= 120, "Nome inválido");
		return um("""
			INSERT INTO chat_lista_contatos(empresa_id,nome,descricao)
			VALUES (:empresa,:nome,:descricao) RETURNING *
			""", dados("empresa", empresa, "nome", nome.trim(), "descricao", descricao));
	}

	public Map<String, Object> atualizarLista(Authentication usuario, long id, String nome, String descricao) {
		exigir(nome != null && !nome.isBlank() && nome.length() <= 120, "Nome inválido");
		return um("""
			UPDATE chat_lista_contatos SET nome=:nome,descricao=:descricao
			WHERE id=:id AND empresa_id=:empresa RETURNING *
			""", dados("id", id, "empresa", empresas.identificar(usuario),
				"nome", nome.trim(), "descricao", descricao));
	}

	public void desativarLista(Authentication usuario, long id) {
		if (banco.update("""
			UPDATE chat_lista_contatos SET ativa=FALSE WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	@Transactional
	public void alterarLista(Authentication usuario, long lista, long contato, boolean incluir) {
		long empresa = empresas.identificar(usuario);
		um("SELECT id FROM chat_lista_contatos WHERE id=:id AND empresa_id=:empresa",
				dados("id", lista, "empresa", empresa));
		um("SELECT id FROM chat_contato WHERE id=:id AND empresa_id=:empresa",
				dados("id", contato, "empresa", empresa));
		if (incluir) banco.update("""
			INSERT INTO chat_lista_destinatario(lista_id,contato_id,empresa_id)
			VALUES (:lista,:contato,:empresa) ON CONFLICT DO NOTHING
			""", dados("lista", lista, "contato", contato, "empresa", empresa));
		else banco.update("""
			DELETE FROM chat_lista_destinatario WHERE lista_id=:lista AND contato_id=:contato
			AND empresa_id=:empresa
			""", dados("lista", lista, "contato", contato, "empresa", empresa));
	}

	public List<Map<String, Object>> mensagensRapidas(Authentication usuario) {
		return banco.queryForList("""
			SELECT * FROM chat_mensagem_rapida WHERE empresa_id=:empresa AND ativa ORDER BY atalho
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> salvarMensagemRapida(Authentication usuario, Long id, String atalho,
			String titulo, String texto) {
		long empresa = empresas.identificar(usuario);
		exigir(atalho != null && atalho.matches("[a-zA-Z0-9_-]{1,80}"), "Atalho inválido");
		exigir(titulo != null && !titulo.isBlank() && titulo.length() <= 120, "Título inválido");
		exigir(texto != null && !texto.isBlank(), "Texto obrigatório");
		if (id == null) return um("""
			INSERT INTO chat_mensagem_rapida(empresa_id,atalho,titulo,texto)
			VALUES (:empresa,:atalho,:titulo,:texto) RETURNING *
			""", dados("empresa", empresa, "atalho", atalho, "titulo", titulo, "texto", texto));
		return um("""
			UPDATE chat_mensagem_rapida SET atalho=:atalho,titulo=:titulo,texto=:texto
			WHERE id=:id AND empresa_id=:empresa RETURNING *
			""", dados("id", id, "empresa", empresa, "atalho", atalho, "titulo", titulo, "texto", texto));
	}

	public void desativarMensagemRapida(Authentication usuario, long id) {
		if (banco.update("""
			UPDATE chat_mensagem_rapida SET ativa=FALSE WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public List<Map<String, Object>> modelos(Authentication usuario) {
		return banco.queryForList("""
			SELECT * FROM chat_modelo_mensagem WHERE empresa_id=:empresa ORDER BY nome,idioma
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> criarModelo(Authentication usuario, String nome, String idioma,
			String categoria, String corpo) {
		long empresa = empresas.identificar(usuario);
		exigir(nome != null && nome.matches("[a-z0-9_]{1,120}"), "Nome inválido");
		exigir(idioma != null && idioma.matches("[a-z]{2}_[A-Z]{2}"), "Idioma inválido");
		exigir(List.of("UTILIDADE", "MARKETING", "AUTENTICACAO").contains(categoria), "Categoria inválida");
		exigir(corpo != null && !corpo.isBlank(), "Corpo obrigatório");
		return um("""
			INSERT INTO chat_modelo_mensagem(empresa_id,nome,idioma,categoria,corpo)
			VALUES (:empresa,:nome,:idioma,:categoria,:corpo) RETURNING *
			""", dados("empresa", empresa, "nome", nome, "idioma", idioma,
				"categoria", categoria, "corpo", corpo));
	}

	public Map<String, Object> atualizarModelo(Authentication usuario, long id, String nome,
			String idioma, String categoria, String corpo) {
		exigir(nome != null && nome.matches("[a-z0-9_]{1,120}"), "Nome inválido");
		exigir(idioma != null && idioma.matches("[a-z]{2}_[A-Z]{2}"), "Idioma inválido");
		exigir(List.of("UTILIDADE", "MARKETING", "AUTENTICACAO").contains(categoria), "Categoria inválida");
		exigir(corpo != null && !corpo.isBlank(), "Corpo obrigatório");
		return um("""
			UPDATE chat_modelo_mensagem SET nome=:nome,idioma=:idioma,categoria=:categoria,corpo=:corpo
			WHERE id=:id AND empresa_id=:empresa AND estado='RASCUNHO' RETURNING *
			""", dados("id", id, "empresa", empresas.identificar(usuario),
				"nome", nome, "idioma", idioma, "categoria", categoria, "corpo", corpo));
	}

	public void arquivarModelo(Authentication usuario, long id) {
		if (banco.update("""
			UPDATE chat_modelo_mensagem SET estado='ARQUIVADO'
			WHERE id=:id AND empresa_id=:empresa AND estado<>'APROVADO'
			""", dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Modelo aprovado não pode ser arquivado");
	}

	public List<Map<String, Object>> campanhas(Authentication usuario) {
		return banco.queryForList("""
			SELECT campanha.*, count(envio.id) AS destinatarios,
			count(envio.id) FILTER (WHERE envio.estado='ENVIADO') AS enviados,
			count(envio.id) FILTER (WHERE envio.estado='FALHOU') AS falhas
			FROM chat_campanha campanha LEFT JOIN chat_campanha_envio envio ON envio.campanha_id=campanha.id
			WHERE campanha.empresa_id=:empresa GROUP BY campanha.id ORDER BY campanha.criada_em DESC
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> criarCampanha(Authentication usuario, String nome, UUID canal,
			long lista, Long modelo, String texto, Instant agendada, int intervalo,
			Integer maximoDiario, LocalTime horarioInicio, LocalTime horarioFim,
			List<Integer> diasSemana) {
		long empresa = empresas.identificar(usuario);
		exigir(nome != null && !nome.isBlank() && nome.length() <= 120, "Nome inválido");
		exigir(intervalo > 0 && intervalo <= 86400, "Intervalo inválido");
		exigir(maximoDiario == null || maximoDiario > 0, "Máximo diário inválido");
		LocalTime inicio = horarioInicio == null ? LocalTime.MIN : horarioInicio;
		LocalTime fim = horarioFim == null ? LocalTime.of(23, 59, 59) : horarioFim;
		exigir(!inicio.isAfter(fim), "Horário inválido");
		List<Integer> dias = diasSemana == null ? List.of(0, 1, 2, 3, 4, 5, 6) : diasSemana;
		exigir(!dias.isEmpty() && dias.size() <= 7 && dias.stream().allMatch(dia -> dia != null && dia >= 0 && dia <= 6),
				"Dias de envio inválidos");
		String diasSql = "{" + dias.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "}";
		exigir((texto != null && !texto.isBlank()) || modelo != null, "Mensagem obrigatória");
		Map<String, Object> dadosCanal = um("SELECT id,provedor FROM chat_canal WHERE id=:id AND empresa_id=:empresa AND ativo",
				dados("id", canal, "empresa", empresa));
		if ("META".equals(dadosCanal.get("provedor")) && modelo == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal oficial exige modelo aprovado");
		um("SELECT id FROM chat_lista_contatos WHERE id=:id AND empresa_id=:empresa",
				dados("id", lista, "empresa", empresa));
		if (modelo != null) um("""
			SELECT id FROM chat_modelo_mensagem WHERE id=:id AND empresa_id=:empresa AND estado='APROVADO'
			""", dados("id", modelo, "empresa", empresa));
		UUID id = UUID.randomUUID();
		Map<String, Object> campanha = um("""
			INSERT INTO chat_campanha(id,empresa_id,nome,canal_id,lista_id,modelo_id,texto,agendada_em,
			intervalo_segundos,maximo_diario,horario_inicio,horario_fim,dias_semana)
			VALUES (:id,:empresa,:nome,:canal,:lista,:modelo,:texto,:agendada,:intervalo,
			:maximo,:inicio,:fim,CAST(:dias AS smallint[])) RETURNING *
			""", dados("id", id, "empresa", empresa, "nome", nome.trim(), "canal", canal,
				"lista", lista, "modelo", modelo, "texto", texto, "agendada", agendada,
				"intervalo", intervalo, "maximo", maximoDiario, "inicio", inicio,
				"fim", fim, "dias", diasSql));
		banco.update("""
			INSERT INTO chat_campanha_envio(empresa_id,campanha_id,contato_id)
			SELECT :empresa,:campanha,contato_id FROM chat_lista_destinatario
			WHERE lista_id=:lista AND empresa_id=:empresa
			""", dados("empresa", empresa, "campanha", id, "lista", lista));
		return campanha;
	}

	@Transactional
	public Map<String, Object> mudarCampanha(Authentication usuario, UUID id, String estado) {
		long empresa = empresas.identificar(usuario);
		exigir(List.of("AGENDADA", "PAUSADA", "CANCELADA", "EXECUTANDO").contains(estado), "Estado inválido");
		Map<String, Object> anterior = um("""
			SELECT * FROM chat_campanha WHERE id=:id AND empresa_id=:empresa FOR UPDATE
			""", dados("id", id, "empresa", empresa));
		String atual = (String) anterior.get("estado");
		boolean permitido = switch (estado) {
			case "AGENDADA" -> "RASCUNHO".equals(atual) || "PAUSADA".equals(atual);
			case "EXECUTANDO" -> "RASCUNHO".equals(atual) || "AGENDADA".equals(atual) || "PAUSADA".equals(atual);
			case "PAUSADA" -> "EXECUTANDO".equals(atual) || "AGENDADA".equals(atual);
			case "CANCELADA" -> !"CONCLUIDA".equals(atual) && !"CANCELADA".equals(atual);
			default -> false;
		};
		if (!permitido) throw new ResponseStatusException(HttpStatus.CONFLICT, "Transição de campanha inválida");
		Map<String, Object> campanha = um("""
			UPDATE chat_campanha SET estado=:estado,
			iniciada_em=CASE WHEN :estado='EXECUTANDO' THEN COALESCE(iniciada_em,NOW()) ELSE iniciada_em END
			WHERE id=:id AND empresa_id=:empresa RETURNING *
			""", dados("id", id, "empresa", empresa, "estado", estado));
		if ("CANCELADA".equals(estado)) banco.update("""
			UPDATE chat_campanha_envio SET estado='CANCELADO' WHERE campanha_id=:id
			AND empresa_id=:empresa AND estado='PENDENTE'
			""", dados("id", id, "empresa", empresa));
		return campanha;
	}
}
