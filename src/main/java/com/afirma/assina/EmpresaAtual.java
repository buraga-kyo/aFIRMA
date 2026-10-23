package com.afirma.assina;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EmpresaAtual {
	private final JdbcTemplate banco;

	public EmpresaAtual(JdbcTemplate banco) { this.banco = banco; }

	public long identificar(Authentication usuario) {
		if (usuario == null || !usuario.isAuthenticated())
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		Object principal = usuario.getPrincipal();
		if (principal instanceof Jwt token) {
			Object valor = token.getClaim("empresa_id");
			if (valor instanceof Number numero && numero.longValue() > 0) return conferir(numero.longValue());
			if (valor instanceof String texto && texto.matches("[1-9][0-9]*"))
				return conferir(Long.parseLong(texto));
		}
		Integer quantidade = banco.queryForObject("SELECT count(*) FROM empresa WHERE ativa", Integer.class);
		if (quantidade != null && quantidade == 1)
			return banco.queryForObject("SELECT id FROM empresa WHERE ativa", Long.class);
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Empresa não identificada no token");
	}

	private long conferir(long empresa) {
		Integer quantidade = banco.queryForObject("SELECT count(*) FROM empresa WHERE id = ? AND ativa",
				Integer.class, empresa);
		if (quantidade == null || quantidade != 1)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Empresa inativa ou inexistente");
		return empresa;
	}
}
