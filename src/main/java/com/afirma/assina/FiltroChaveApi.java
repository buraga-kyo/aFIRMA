package com.afirma.assina;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroChaveApi extends OncePerRequestFilter {

	private final byte[] resumoEsperado;

	public FiltroChaveApi(String resumoHexadecimal) {
		if (resumoHexadecimal == null || !resumoHexadecimal.matches("[0-9a-fA-F]{64}")) {
			throw new IllegalArgumentException("APP_API_KEY_SHA256 deve conter 64 caracteres hexadecimais");
		}
		this.resumoEsperado = HexFormat.of().parseHex(resumoHexadecimal);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		String caminho = requisicao.getServletPath();
		return !"/api/pdfs/assinar".equals(caminho)
				&& (!caminho.startsWith("/api/chat/") || "/api/chat/interno/eventos".equals(caminho));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
			FilterChain cadeia) throws ServletException, IOException {
		String chave = requisicao.getHeader("X-API-Key");
		if (chave == null || chave.length() < 32 || chave.length() > 256 || !conferir(chave)) {
			resposta.setHeader("Cache-Control", "no-store");
			resposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		cadeia.doFilter(requisicao, resposta);
	}

	private boolean conferir(String chave) {
		try {
			byte[] resumo = MessageDigest.getInstance("SHA-256")
					.digest(chave.getBytes(StandardCharsets.UTF_8));
			return MessageDigest.isEqual(resumo, resumoEsperado);
		} catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException("SHA-256 indisponível", erro);
		}
	}
}
