package com.afirma.assina;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaAssinatura {

	private static final String TOKEN_TESTE = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIn0.n1fXvSU8qCLktT4SwVGcerEsfzXi8e7KVvTaT4XRSDU";
	private static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";

	private final Environment ambiente;

	public ControlePaginaAssinatura(Environment ambiente) {
		this.ambiente = ambiente;
	}

	@GetMapping("/")
	public String inicio() {
		return "inicio";
	}

	@GetMapping("/assinatura")
	public String assinatura() {
		return "assinatura";
	}

	@GetMapping("/assineai/trator")
	public String documentos(Model model) {
		model.addAttribute("tokenTeste", ambiente.matchesProfiles("local-test") ? TOKEN_TESTE : "");
		model.addAttribute("chaveTeste", ambiente.matchesProfiles("local-test") ? CHAVE_TESTE : "");
		return "documentos-assinatura";
	}

	@GetMapping("/preparar-assinaturas")
	public String prepararAssinaturas() { return "preparar-assinaturas"; }

	@GetMapping("/convite/{token}")
	public String convite() { return "convite"; }

	@GetMapping("/advai")
	public String advai() {
		return "advai";
	}
}
