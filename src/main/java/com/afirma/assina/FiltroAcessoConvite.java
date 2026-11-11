package com.afirma.assina;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FiltroAcessoConvite extends OncePerRequestFilter {
	public static final String NOME_COOKIE = "afirma_acesso_convite";
	public static final String ATRIBUTO_SESSAO = "sessaoConvite";
	private final ServicoAutenticacaoConvites autenticacao;
	private final ObjectMapper json;
	private final String origemPublica;
	private final boolean testeLocal;

	public FiltroAcessoConvite(ServicoAutenticacaoConvites autenticacao, ObjectMapper json, Environment ambiente) {
		this.autenticacao = autenticacao;
		this.json = json;
		origemPublica = ambiente.getProperty("ASSINEAI_URL_PUBLICA", "");
		testeLocal = ambiente.matchesProfiles("local-test");
		if (!origemPublica.isBlank()) {
			URI origem = URI.create(origemPublica);
			if (origem.getHost() == null || origem.getUserInfo() != null || origem.getQuery() != null || origem.getFragment() != null
					|| !(origem.getPath().isEmpty() || origem.getPath().equals("/"))
					|| !"https".equalsIgnoreCase(origem.getScheme()) && !testeLocal) {
				throw new IllegalArgumentException("ASSINEAI_URL_PUBLICA precisa informar a origem HTTPS da aplicação");
			}
		}
	}

	@Override protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		return !requisicao.getServletPath().startsWith("/api/convites/");
	}

	@Override protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
			FilterChain cadeia) throws ServletException, IOException {
		resposta.setHeader("Cache-Control", "no-store");
		resposta.setHeader("Referrer-Policy", "no-referrer");
		resposta.setHeader("X-Content-Type-Options", "nosniff");
		try {
			String[] partes = requisicao.getServletPath().split("/");
			if (partes.length < 4 || !partes[3].matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			String token = partes[3];
			String operacao = partes.length == 5 ? partes[4] : "";
			if (!"GET".equals(requisicao.getMethod()) && !"HEAD".equals(requisicao.getMethod())) exigirOrigem(requisicao);
			boolean publico = partes.length == 5 && ("acesso".equals(operacao) || "codigo".equals(operacao) || "autenticar".equals(operacao));
			if (!publico) requisicao.setAttribute(ATRIBUTO_SESSAO, autenticacao.exigirSessao(token, segredoSessao(requisicao)));
			cadeia.doFilter(requisicao, resposta);
		} catch (ResponseStatusException erro) {
			resposta.setStatus(erro.getStatusCode().value());
			resposta.setContentType("application/json");
			resposta.setCharacterEncoding("UTF-8");
			json.writeValue(resposta.getOutputStream(), new ErroApi("ACESSO_CONVITE", erro.getReason() == null ? "Acesso recusado" : erro.getReason()));
		}
	}

	private void exigirOrigem(HttpServletRequest requisicao) {
		String esperada = origemPublica.replaceAll("/$", "");
		if (esperada.isBlank() && testeLocal && "127.0.0.1".equals(requisicao.getServerName())) {
			esperada = "http://127.0.0.1:" + requisicao.getServerPort();
		}
		if (esperada.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure a origem pública da aplicação");
		if (!esperada.equals(requisicao.getHeader("Origin"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origem da requisição não autorizada");
	}

	public static String segredoSessao(HttpServletRequest requisicao) {
		if (requisicao.getCookies() == null) return null;
		var encontrados = Arrays.stream(requisicao.getCookies()).filter(cookie -> NOME_COOKIE.equals(cookie.getName())).toList();
		return encontrados.size() == 1 ? encontrados.getFirst().getValue() : null;
	}
}
