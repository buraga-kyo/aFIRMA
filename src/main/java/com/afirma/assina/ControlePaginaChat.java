package com.afirma.assina;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaChat {
	private static final String TOKEN_TESTE = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIGNoYXQubWFuYWdlIGNoYXQudXNlIn0.3GXptW7Lrwl4knUYrT_cp2akCtucciv5UefkV1Qg4AY";
	private static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";
	private final Environment ambiente;

	public ControlePaginaChat(Environment ambiente) { this.ambiente = ambiente; }

	@GetMapping("/chat")
	public String chat(Model modelo) {
		modelo.addAttribute("tokenTeste", ambiente.matchesProfiles("local-test") ? TOKEN_TESTE : "");
		modelo.addAttribute("chaveTeste", ambiente.matchesProfiles("local-test") ? CHAVE_TESTE : "");
		return "chat";
	}
}
