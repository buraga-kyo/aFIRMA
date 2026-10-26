package com.afirma.assina;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class FiltroSegurancaImportacao extends OncePerRequestFilter {

	private final long limiteTamanhoBytes;
	private final Set<String> ipsBloqueados = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final ConcurrentHashMap<String, List<Instant>> tentativasPorIp = new ConcurrentHashMap<>();

	private static final Pattern USER_AGENT_ATAQUE = Pattern.compile(
			".*(curl|wget|nmap|nikto|sqlmap|dirb|kali|python|postman|insomnia).*", Pattern.CASE_INSENSITIVE);

	public FiltroSegurancaImportacao(@Value("${afirma.importacao.tamanho-maximo:10485760}") long limiteTamanhoBytes) {
		this.limiteTamanhoBytes = limiteTamanhoBytes;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		return !requisicao.getRequestURI().endsWith("/importar");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
			FilterChain cadeia) throws ServletException, IOException {
		
		String ip = requisicao.getRemoteAddr();

		if (ipsBloqueados.contains(ip)) {
			resposta.sendError(HttpStatus.FORBIDDEN.value(), "IP bloqueado por atividade suspeita");
			return;
		}

		String userAgent = requisicao.getHeader("User-Agent");
		if (userAgent != null && USER_AGENT_ATAQUE.matcher(userAgent).matches()) {
			ipsBloqueados.add(ip);
			resposta.sendError(HttpStatus.FORBIDDEN.value(), "Acesso negado");
			return;
		}

		Instant agora = Instant.now();
		List<Instant> tentativas = tentativasPorIp.computeIfAbsent(ip, k -> Collections.synchronizedList(new LinkedList<>()));

		synchronized (tentativas) {
			tentativas.removeIf(t -> t.isBefore(agora.minusSeconds(10)));
			
			long emTresSegundos = tentativas.stream().filter(t -> t.isAfter(agora.minusSeconds(3))).count();
			if (emTresSegundos >= 4) { // with current it will be 5
				ipsBloqueados.add(ip);
				resposta.sendError(HttpStatus.FORBIDDEN.value(), "IP bloqueado por excesso de tentativas");
				return;
			}
			
			if (!tentativas.isEmpty()) {
				resposta.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Aguarde 10 segundos entre as importações");
				return;
			}
			tentativas.add(agora);
		}

		String contentType = requisicao.getContentType();
		if (contentType != null && contentType.startsWith("multipart/form-data")) {
			long tamanho = requisicao.getContentLengthLong();
			if (tamanho > limiteTamanhoBytes) {
				resposta.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Arquivo excede o tamanho limite");
				return;
			}
		}

		cadeia.doFilter(requisicao, resposta);
	}
}
