package com.afirma.assina;

import java.time.Instant;
import java.time.LocalTime;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relacionamento")
public class RelacionamentoControle {
	public record Contato(String telefone, String nome, String email, String informacoes) {}
	public record Etiqueta(String nome, String cor) {}
	public record Lista(String nome, String descricao) {}
	public record MensagemRapida(String atalho, String titulo, String texto) {}
	public record Modelo(String nome, String idioma, String categoria, String corpo) {}
	public record Campanha(String nome, UUID canalId, long listaId, Long modeloId,
			String texto, Instant agendadaEm, int intervaloSegundos, Integer maximoDiario,
			LocalTime horarioInicio, LocalTime horarioFim, List<Integer> diasSemana) {}
	public record Estado(String estado) {}

	private final RelacionamentoServico servico;

	public RelacionamentoControle(RelacionamentoServico servico) { this.servico = servico; }

	@GetMapping("/contatos")
	public List<Map<String, Object>> contatos(Authentication usuario,
			@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "100") int limite) {
		return servico.contatos(usuario, busca, limite);
	}

	@PostMapping("/contatos")
	public Map<String, Object> criarContato(Authentication usuario, @RequestBody Contato pedido) {
		return servico.salvarContato(usuario, null, pedido.telefone(), pedido.nome(),
				pedido.email(), pedido.informacoes());
	}

	@PutMapping("/contatos/{id}")
	public Map<String, Object> atualizarContato(Authentication usuario, @PathVariable long id,
			@RequestBody Contato pedido) {
		return servico.salvarContato(usuario, id, pedido.telefone(), pedido.nome(),
				pedido.email(), pedido.informacoes());
	}

	@GetMapping("/etiquetas")
	public List<Map<String, Object>> etiquetas(Authentication usuario) { return servico.etiquetas(usuario); }

	@PostMapping("/etiquetas")
	public Map<String, Object> criarEtiqueta(Authentication usuario, @RequestBody Etiqueta pedido) {
		return servico.salvarEtiqueta(usuario, null, pedido.nome(), pedido.cor());
	}

	@PutMapping("/etiquetas/{id}")
	public Map<String, Object> atualizarEtiqueta(Authentication usuario, @PathVariable long id,
			@RequestBody Etiqueta pedido) {
		return servico.salvarEtiqueta(usuario, id, pedido.nome(), pedido.cor());
	}

	@DeleteMapping("/etiquetas/{id}")
	public void desativarEtiqueta(Authentication usuario, @PathVariable long id) {
		servico.desativarEtiqueta(usuario, id);
	}

	@PostMapping("/atendimentos/{atendimento}/etiquetas/{etiqueta}")
	public void etiquetar(Authentication usuario, @PathVariable UUID atendimento,
			@PathVariable long etiqueta) { servico.etiquetar(usuario, atendimento, etiqueta, true); }

	@DeleteMapping("/atendimentos/{atendimento}/etiquetas/{etiqueta}")
	public void removerEtiqueta(Authentication usuario, @PathVariable UUID atendimento,
			@PathVariable long etiqueta) { servico.etiquetar(usuario, atendimento, etiqueta, false); }

	@GetMapping("/listas")
	public List<Map<String, Object>> listas(Authentication usuario) { return servico.listas(usuario); }

	@PostMapping("/listas")
	public Map<String, Object> criarLista(Authentication usuario, @RequestBody Lista pedido) {
		return servico.criarLista(usuario, pedido.nome(), pedido.descricao());
	}

	@PutMapping("/listas/{id}")
	public Map<String, Object> atualizarLista(Authentication usuario, @PathVariable long id,
			@RequestBody Lista pedido) {
		return servico.atualizarLista(usuario, id, pedido.nome(), pedido.descricao());
	}

	@DeleteMapping("/listas/{id}")
	public void desativarLista(Authentication usuario, @PathVariable long id) {
		servico.desativarLista(usuario, id);
	}

	@PostMapping("/listas/{lista}/contatos/{contato}")
	public void incluirContato(Authentication usuario, @PathVariable long lista, @PathVariable long contato) {
		servico.alterarLista(usuario, lista, contato, true);
	}

	@DeleteMapping("/listas/{lista}/contatos/{contato}")
	public void retirarContato(Authentication usuario, @PathVariable long lista, @PathVariable long contato) {
		servico.alterarLista(usuario, lista, contato, false);
	}

	@GetMapping("/mensagens-rapidas")
	public List<Map<String, Object>> mensagensRapidas(Authentication usuario) {
		return servico.mensagensRapidas(usuario);
	}

	@PostMapping("/mensagens-rapidas")
	public Map<String, Object> criarMensagemRapida(Authentication usuario, @RequestBody MensagemRapida pedido) {
		return servico.salvarMensagemRapida(usuario, null, pedido.atalho(), pedido.titulo(), pedido.texto());
	}

	@PutMapping("/mensagens-rapidas/{id}")
	public Map<String, Object> atualizarMensagemRapida(Authentication usuario, @PathVariable long id,
			@RequestBody MensagemRapida pedido) {
		return servico.salvarMensagemRapida(usuario, id, pedido.atalho(), pedido.titulo(), pedido.texto());
	}

	@DeleteMapping("/mensagens-rapidas/{id}")
	public void desativarMensagemRapida(Authentication usuario, @PathVariable long id) {
		servico.desativarMensagemRapida(usuario, id);
	}

	@GetMapping("/modelos")
	public List<Map<String, Object>> modelos(Authentication usuario) { return servico.modelos(usuario); }

	@PostMapping("/modelos")
	public Map<String, Object> criarModelo(Authentication usuario, @RequestBody Modelo pedido) {
		return servico.criarModelo(usuario, pedido.nome(), pedido.idioma(), pedido.categoria(), pedido.corpo());
	}

	@PutMapping("/modelos/{id}")
	public Map<String, Object> atualizarModelo(Authentication usuario, @PathVariable long id,
			@RequestBody Modelo pedido) {
		return servico.atualizarModelo(usuario, id, pedido.nome(), pedido.idioma(),
				pedido.categoria(), pedido.corpo());
	}

	@DeleteMapping("/modelos/{id}")
	public void arquivarModelo(Authentication usuario, @PathVariable long id) {
		servico.arquivarModelo(usuario, id);
	}

	@GetMapping("/campanhas")
	public List<Map<String, Object>> campanhas(Authentication usuario) { return servico.campanhas(usuario); }

	@PostMapping("/campanhas")
	public Map<String, Object> criarCampanha(Authentication usuario, @RequestBody Campanha pedido) {
		return servico.criarCampanha(usuario, pedido.nome(), pedido.canalId(), pedido.listaId(),
				pedido.modeloId(), pedido.texto(), pedido.agendadaEm(), pedido.intervaloSegundos(),
				pedido.maximoDiario(), pedido.horarioInicio(), pedido.horarioFim(), pedido.diasSemana());
	}

	@PutMapping("/campanhas/{id}/estado")
	public Map<String, Object> mudarCampanha(Authentication usuario, @PathVariable UUID id,
			@RequestBody Estado pedido) {
		return servico.mudarCampanha(usuario, id, pedido.estado());
	}
}
