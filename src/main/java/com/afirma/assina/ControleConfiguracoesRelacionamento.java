package com.afirma.assina;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relacionamento")
public class ControleConfiguracoesRelacionamento {
	public record Automacao(String nome, String gatilho, String condicoes, String acoes, int prioridade) {}
	public record Agente(String nome, String provedor, String modelo, String instrucoes,
			String referenciaSegredo, String ativador) {}
	public record Integracao(String nome, String tipo, String configuracao, String referenciaSegredo) {}
	public record Ativacao(boolean ativo) {}

	private final ServicoConfiguracoesRelacionamento servico;

	public ControleConfiguracoesRelacionamento(ServicoConfiguracoesRelacionamento servico) {
		this.servico = servico;
	}

	@GetMapping("/automacoes")
	public List<Map<String, Object>> automacoes(Authentication usuario) { return servico.automacoes(usuario); }

	@PostMapping("/automacoes")
	public Map<String, Object> criarAutomacao(Authentication usuario, @RequestBody Automacao pedido) {
		return servico.criarAutomacao(usuario, pedido.nome(), pedido.gatilho(),
				pedido.condicoes(), pedido.acoes(), pedido.prioridade());
	}

	@PutMapping("/automacoes/{id}/ativo")
	public void ativarAutomacao(Authentication usuario, @PathVariable long id, @RequestBody Ativacao pedido) {
		servico.ativarAutomacao(usuario, id, pedido.ativo());
	}

	@GetMapping("/agentes")
	public List<Map<String, Object>> agentes(Authentication usuario) { return servico.agentes(usuario); }

	@PostMapping("/agentes")
	public Map<String, Object> criarAgente(Authentication usuario, @RequestBody Agente pedido) {
		return servico.criarAgente(usuario, pedido.nome(), pedido.provedor(), pedido.modelo(),
				pedido.instrucoes(), pedido.referenciaSegredo(), pedido.ativador());
	}

	@PutMapping("/agentes/{id}/ativo")
	public void ativarAgente(Authentication usuario, @PathVariable long id, @RequestBody Ativacao pedido) {
		servico.ativarAgente(usuario, id, pedido.ativo());
	}

	@GetMapping("/integracoes")
	public List<Map<String, Object>> integracoes(Authentication usuario) { return servico.integracoes(usuario); }

	@PostMapping("/integracoes")
	public Map<String, Object> criarIntegracao(Authentication usuario, @RequestBody Integracao pedido) {
		return servico.criarIntegracao(usuario, pedido.nome(), pedido.tipo(),
				pedido.configuracao(), pedido.referenciaSegredo());
	}

	@PutMapping("/integracoes/{id}/ativo")
	public void ativarIntegracao(Authentication usuario, @PathVariable long id, @RequestBody Ativacao pedido) {
		servico.ativarIntegracao(usuario, id, pedido.ativo());
	}

	@GetMapping("/plano")
	public Map<String, Object> plano(Authentication usuario) { return servico.plano(usuario); }
}
