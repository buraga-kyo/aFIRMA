package com.afirma.assina;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaAssinatura {

	@GetMapping("/")
	public String inicio() {
		return "assinatura";
	}
}
