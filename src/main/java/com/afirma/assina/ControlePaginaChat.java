package com.afirma.assina;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaChat {
	private static final String TOKEN_TESTE = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIGNoYXQubWFuYWdlIGNoYXQudXNlIn0.3GXptW7Lrwl4knUYrT_cp2akCtucciv5UefkV1Qg4AY";
	private static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";
	private final Environment ambiente;
	private final String urlChatwoot;

	public ControlePaginaChat(Environment ambiente,
			@Value("${APP_CHATWOOT_URL:}") String urlChatwoot) {
		this.ambiente = ambiente;
		this.urlChatwoot = urlChatwoot;
	}

	@GetMapping("/chat")
	public String chat(Model modelo) {
		if (!urlChatwoot.isBlank()) {
			modelo.addAttribute("urlChat", urlChatwoot);
			return "chat-integrado";
		}
		modelo.addAttribute("tokenTeste", ambiente.matchesProfiles("local-test") ? TOKEN_TESTE : "");
		modelo.addAttribute("chaveTeste", ambiente.matchesProfiles("local-test") ? CHAVE_TESTE : "");
		return "chat";
	}

	@GetMapping("/chat/canais")
	public String canais(Model modelo) {
		modelo.addAttribute("tokenTeste", ambiente.matchesProfiles("local-test") ? TOKEN_TESTE : "");
		modelo.addAttribute("chaveTeste", ambiente.matchesProfiles("local-test") ? CHAVE_TESTE : "");
		return "chat-canais";
	}

	@GetMapping("/chat/gestao")
	public String gestao(Model modelo) {
		modelo.addAttribute("tokenTeste", ambiente.matchesProfiles("local-test") ? TOKEN_TESTE : "");
		modelo.addAttribute("chaveTeste", ambiente.matchesProfiles("local-test") ? CHAVE_TESTE : "");
		return "chat-gestao";
	}
}
