package com.afirma.assina;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relacionamento/bots")
public class ControleBots {
	public record Definicao(UUID canalId, String nome, String provedor, boolean ativo,
			String funcionamento, String fluxo, String enderecoExterno) {}
	public record Continuacao(String endereco) {}
	public record MensagemExterna(String texto) {}
	public record AtendimentoExterno(Long setorId) {}
	private final ServicoBots servico;

	public ControleBots(ServicoBots servico) { this.servico = servico; }

	@GetMapping
	public List<Map<String, Object>> listar(Authentication usuario) { return servico.listar(usuario); }

	@PostMapping
	public Map<String, Object> criar(Authentication usuario, @RequestBody Definicao pedido) {
		return servico.salvar(usuario, null, pedido.canalId(), pedido.nome(), pedido.provedor(),
			pedido.ativo(), pedido.funcionamento(), pedido.fluxo(), pedido.enderecoExterno());
	}

	@PutMapping("/{id}")
	public Map<String, Object> atualizar(Authentication usuario, @PathVariable long id,
			@RequestBody Definicao pedido) {
		return servico.salvar(usuario, id, pedido.canalId(), pedido.nome(), pedido.provedor(),
			pedido.ativo(), pedido.funcionamento(), pedido.fluxo(), pedido.enderecoExterno());
	}

	@DeleteMapping("/{id}")
	public void excluir(Authentication usuario, @PathVariable long id) { servico.excluir(usuario, id); }

	@GetMapping("/{id}/sessoes")
	public List<Map<String, Object>> sessoes(Authentication usuario, @PathVariable long id) {
		return servico.sessoes(usuario, id);
	}

	@DeleteMapping("/sessoes/{id}")
	public void cancelarSessao(Authentication usuario, @PathVariable UUID id) {
		servico.cancelarSessao(usuario, id);
	}

	@PutMapping("/sessoes/{id}/continuacao")
	public void definirContinuacao(Authentication usuario, @PathVariable UUID id,
			@RequestBody Continuacao pedido) {
		servico.definirContinuacao(usuario, id, pedido.endereco());
	}

	@PostMapping("/sessoes/{id}/mensagens")
	public void enviarMensagemExterna(Authentication usuario, @PathVariable UUID id,
			@RequestBody MensagemExterna pedido) {
		servico.enviarMensagemExterna(usuario, id, pedido.texto());
	}

	@PostMapping("/sessoes/{id}/atendimento")
	public Map<String, UUID> gerarAtendimentoExterno(Authentication usuario, @PathVariable UUID id,
			@RequestBody AtendimentoExterno pedido) {
		return Map.of("atendimentoId", servico.gerarAtendimentoExterno(usuario, id, pedido.setorId()));
	}

	@PostMapping("/sessoes/{id}/finalizar")
	public void finalizarExterno(Authentication usuario, @PathVariable UUID id) {
		servico.finalizarExterno(usuario, id);
	}
}
