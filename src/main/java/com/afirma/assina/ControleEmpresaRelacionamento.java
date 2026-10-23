package com.afirma.assina;

import java.time.ZoneId;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/relacionamento/empresa")
public class ControleEmpresaRelacionamento {
	public record Perfil(String nome, String razaoSocial, String documento,
			String endereco, String telefone, String email, String fusoHorario) {}
	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;
	private final ObjectMapper json;

	public ControleEmpresaRelacionamento(NamedParameterJdbcTemplate banco,
			EmpresaAtual empresas, ObjectMapper json) {
		this.banco = banco;
		this.empresas = empresas;
		this.json = json;
	}

	@GetMapping
	public Map<String, Object> perfil(Authentication usuario) {
		return banco.queryForMap("""
			SELECT id,nome,ativa,razao_social,documento,endereco,telefone,email,
			fuso_horario,provedor_mensagens,identificador_aplicativo_oficial,criada_em
			FROM empresa WHERE id=:id
			""", new MapSqlParameterSource("id", empresas.identificar(usuario)));
	}

	@PutMapping
	public Map<String, Object> atualizar(Authentication usuario, @RequestBody Perfil pedido) {
		if (pedido.nome() == null || pedido.nome().isBlank() || pedido.nome().length() > 120
				|| pedido.razaoSocial() != null && pedido.razaoSocial().length() > 180
				|| pedido.documento() != null && !pedido.documento().matches("[0-9]{11}|[0-9]{14}")
				|| pedido.telefone() != null && !pedido.telefone().matches("[0-9+() -]{8,30}")
				|| pedido.email() != null && !pedido.email().matches("[^@ ]+@[^@ ]+\\.[^@ ]+")
				|| pedido.fusoHorario() == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido");
		try { ZoneId.of(pedido.fusoHorario()); }
		catch (RuntimeException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fuso horário inválido", erro);
		}
		String endereco = pedido.endereco() == null ? "{}" : pedido.endereco();
		if (endereco.length() > 10000)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço muito longo");
		try {
			JsonNode dados = json.readTree(endereco);
			if (!dados.isObject()) throw new IllegalArgumentException();
		} catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço inválido", erro);
		}
		MapSqlParameterSource valores = new MapSqlParameterSource()
			.addValue("id", empresas.identificar(usuario))
			.addValue("nome", pedido.nome().trim())
			.addValue("razao", pedido.razaoSocial())
			.addValue("documento", pedido.documento())
			.addValue("endereco", endereco)
			.addValue("telefone", pedido.telefone())
			.addValue("email", pedido.email())
			.addValue("fuso", pedido.fusoHorario());
		return banco.queryForMap("""
			UPDATE empresa SET nome=:nome,razao_social=:razao,documento=:documento,
			endereco=CAST(:endereco AS jsonb),telefone=:telefone,email=:email,
			fuso_horario=:fuso WHERE id=:id
			RETURNING id,nome,razao_social,documento,endereco,telefone,email,fuso_horario
			""", valores);
	}
}
