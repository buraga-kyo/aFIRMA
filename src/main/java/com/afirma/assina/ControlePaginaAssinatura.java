package com.afirma.assina;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaAssinatura {

	@GetMapping("/")
	public String inicio() {
		return "inicio";
	}

	@GetMapping("/assinatura")
	public String assinatura() {
		return "assinatura";
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
