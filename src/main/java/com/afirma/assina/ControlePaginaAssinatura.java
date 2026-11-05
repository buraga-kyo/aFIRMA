package com.afirma.assina;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControlePaginaAssinatura {

	@GetMapping({"/", "/institucional"})
	public String inicio() { return "inicio"; }

	@GetMapping("/assinatura")
	public String assinatura() {
		return "assinatura";
	}

	@GetMapping("/assineai/trator")
	public String documentos(Model modeloPagina) {
		return "documentos-assinatura";
	}

	@GetMapping("/preparar-assinaturas")
	public String prepararAssinaturas() { return "preparar-assinaturas"; }

	@GetMapping("/convite/{token}")
	public String convite() { return "convite"; }

    @GetMapping({"/web-panels/assinar-documentos/{token}","/web-panels/criar-assinatura/{token}"})
    public String conviteProducao(){return "convite-legado";}
    @GetMapping("/web-panels/autenticidade-documento/{token}")
    public String autenticidadeProducao(){return "autenticidade-assineai";}
    @GetMapping("/firma")
    public String empresa(){return "empresa-assineai";}
    @GetMapping("/empresa")
    public String empresaAntiga(){return "redirect:/firma";}
	@GetMapping("/advai")
	public String advai() {
		return "advai";
	}
}
