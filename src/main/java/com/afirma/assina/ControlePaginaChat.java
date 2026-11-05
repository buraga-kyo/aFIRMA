package com.afirma.assina;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaChat {
	@GetMapping("/chat")
	public String chat() { return "relacionamento-chat"; }

	@GetMapping("/chat/canais")
	public String canais() { return "redirect:/sistema?modulo=canais"; }

	@GetMapping("/chat/gestao")
	public String gestao() { return "redirect:/sistema?modulo=contatos"; }
}
