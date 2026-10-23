package com.afirma.assina;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relacionamento/webhooks")
public class ControleWebhooks {
	public record NovoWebhook(String nome, String destino, String referenciaSegredo,
			List<String> eventos, Boolean todosCanais, List<UUID> canais) {}
	public record Ativacao(boolean ativo) {}
	private final ServicoWebhooks servico;

	public ControleWebhooks(ServicoWebhooks servico) { this.servico = servico; }

	@GetMapping
	public List<Map<String, Object>> listar(Authentication usuario) { return servico.listar(usuario); }

	@PostMapping
	public Map<String, Object> criar(Authentication usuario, @RequestBody NovoWebhook pedido) {
		return servico.criar(usuario, pedido.nome(), pedido.destino(),
				pedido.referenciaSegredo(), pedido.eventos(),
				!Boolean.FALSE.equals(pedido.todosCanais()), pedido.canais());
	}

	@DeleteMapping("/{id}")
	public void excluir(Authentication usuario, @PathVariable UUID id) { servico.excluir(usuario, id); }

	@PutMapping("/{id}/ativo")
	public void ativar(Authentication usuario, @PathVariable UUID id, @RequestBody Ativacao pedido) {
		servico.ativar(usuario, id, pedido.ativo());
	}

	@GetMapping("/{id}/entregas")
	public List<Map<String, Object>> entregas(Authentication usuario, @PathVariable UUID id) {
		return servico.entregas(usuario, id);
	}
}
