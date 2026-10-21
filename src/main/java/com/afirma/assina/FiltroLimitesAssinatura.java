package com.afirma.assina;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroLimitesAssinatura extends OncePerRequestFilter {

	private static final long TETO_REQUISICAO = 23L * 1024 * 1024;
	private static final DefaultRedisScript<Long> CONTADOR = new DefaultRedisScript<>("""
			local valor = redis.call('INCR', KEYS[1])
			if valor == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
			return valor
			""", Long.class);

	private final StringRedisTemplate redis;
	private final int limitePorMinuto;
	private final Semaphore vagas;

	public FiltroLimitesAssinatura(StringRedisTemplate redis,
			@Value("${app.assinatura.requisicoes-por-minuto:30}") int limitePorMinuto,
			@Value("${app.assinatura.assinaturas-simultaneas:2}") int assinaturasSimultaneas) {
		if (limitePorMinuto < 1 || assinaturasSimultaneas < 1) {
			throw new IllegalArgumentException("Limites da API devem ser positivos");
		}
		this.redis = redis;
		this.limitePorMinuto = limitePorMinuto;
		this.vagas = new Semaphore(assinaturasSimultaneas);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		return !"/api/pdfs/assinar".equals(requisicao.getServletPath())
				&& !requisicao.getServletPath().startsWith("/api/blockchain/")
				&& !requisicao.getServletPath().startsWith("/api/envelopes")
				&& !requisicao.getServletPath().startsWith("/api/convites/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
			FilterChain cadeia) throws ServletException, IOException {
		resposta.setHeader("Cache-Control", "no-store");
		if (requisicao.getContentLengthLong() > TETO_REQUISICAO) {
			resposta.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}

		Long tentativas;
		try {
			String endereco = requisicao.getRemoteAddr();
			tentativas = redis.execute(CONTADOR,
					List.of("assinatura:ip:" + (endereco == null ? "desconhecido" : endereco)), "60000");
		} catch (RuntimeException erro) {
			resposta.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}
		if (tentativas == null) {
			resposta.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}
		if (tentativas > limitePorMinuto) {
			resposta.setHeader("Retry-After", "60");
			resposta.setStatus(429);
			return;
		}
		if (!vagas.tryAcquire()) {
			resposta.setHeader("Retry-After", "1");
			resposta.setStatus(429);
			return;
		}
		try {
			cadeia.doFilter(requisicao, resposta);
		} finally {
			vagas.release();
		}
	}
}
