package com.afirma.assina;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroCabecalhosAssinatura extends OncePerRequestFilter {
	@Override protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		String caminho = requisicao.getServletPath();
		return !(caminho.equals("/assinatura") || caminho.equals("/preparar-assinaturas") || caminho.startsWith("/convite/") || caminho.startsWith("/assineai/convite/") || caminho.startsWith("/web-panels/assinar-documentos/"));
	}
	@Override protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		resposta.setHeader("Cache-Control", "no-store");
		resposta.setHeader("Referrer-Policy", "no-referrer");
		resposta.setHeader("X-Content-Type-Options", "nosniff");
		resposta.setHeader("X-Frame-Options", "DENY");
		resposta.setHeader("Permissions-Policy", "camera=(self), microphone=(), geolocation=(self)");
		resposta.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
				+ "img-src 'self' blob: data:; font-src 'self'; media-src 'self' blob:; connect-src 'self' https://api64.ipify.org; "
				+ "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
		cadeia.doFilter(requisicao, resposta);
	}
}
