package com.afirma.assina;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relacionamento/filtros")
public class ControleFiltroInteligente {
	public record Pedido(String pergunta, List<String> colunas) {}
	private final ServicoFiltroInteligente servico;

	public ControleFiltroInteligente(ServicoFiltroInteligente servico) { this.servico = servico; }

	@PostMapping("/interpretar")
	public ServicoFiltroInteligente.Resposta interpretar(Authentication usuario,
			@RequestBody Pedido pedido) {
		return servico.interpretar(usuario, pedido.pergunta(), pedido.colunas());
	}
}
