package com.afirma.assina;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControleIntegracoesApi {
	public record NovaChave(String nome, List<String> escopos, Instant expiraEm) {}
	public record Contato(String telefone, String nome) {}
	public record Mensagem(UUID identificador, UUID canalId, String telefone, String texto) {}

	private final ServicoIntegracoesApi servico;

	public ControleIntegracoesApi(ServicoIntegracoesApi servico) { this.servico = servico; }

	@GetMapping("/api/relacionamento/chaves")
	public List<Map<String, Object>> chaves(Authentication usuario) { return servico.chaves(usuario); }

	@PostMapping("/api/relacionamento/chaves")
	public Map<String, Object> criarChave(Authentication usuario, @RequestBody NovaChave pedido) {
		return servico.criarChave(usuario, pedido.nome(), pedido.escopos(), pedido.expiraEm());
	}

	@DeleteMapping("/api/relacionamento/chaves/{id}")
	public void revogar(Authentication usuario, @PathVariable UUID id) { servico.revogar(usuario, id); }

	@GetMapping("/api/integracoes/v1/canais")
	public List<Map<String, Object>> canais(@RequestHeader("X-Firma-Integracao") String chave) {
		return servico.canais(chave);
	}

	@GetMapping("/api/integracoes/v1/setores")
	public List<Map<String, Object>> setores(@RequestHeader("X-Firma-Integracao") String chave) {
		return servico.setores(chave);
	}

	@PostMapping("/api/integracoes/v1/contatos")
	public Map<String, Object> contato(@RequestHeader("X-Firma-Integracao") String chave,
			@RequestBody Contato pedido) {
		return servico.contato(chave, pedido.telefone(), pedido.nome());
	}

	@PostMapping("/api/integracoes/v1/mensagens")
	public Map<String, Object> enviar(@RequestHeader("X-Firma-Integracao") String chave,
			@RequestBody Mensagem pedido) {
		return servico.enviar(chave, pedido.identificador(), pedido.canalId(),
				pedido.telefone(), pedido.texto());
	}

	@GetMapping("/api/integracoes/v1/mensagens/{id}")
	public Map<String, Object> consultarEnvio(@RequestHeader("X-Firma-Integracao") String chave,
			@PathVariable UUID id) {
		return servico.consultarEnvio(chave, id);
	}

	@PostMapping("/api/integracoes/v1/atendimentos/{id}/finalizar")
	public void finalizar(@RequestHeader("X-Firma-Integracao") String chave, @PathVariable UUID id) {
		servico.finalizar(chave, id);
	}
}
