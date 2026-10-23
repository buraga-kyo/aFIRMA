package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatControle {
	public record NovoSetor(String nome) {}
	public record AtualizacaoSetor(String nome, Boolean ativo) {}
	public record NovoAtendente(String identidade, String nome, List<Long> setores) {}
	public record AtualizacaoAtendente(String nome, Boolean ativo) {}
	public record NovoCanal(String nome, String telefone, Long setorPadraoId,
			Integer inatividadeMinutos, String mensagemEncerramento) {}
	public record Disponibilidade(String estado) {}
	public record Transferencia(Long setorId, Long atendenteId) {}
	public record Texto(String texto) {}
	public record Avaliacao(int nota) {}
	public record PedidoSugestao(long agenteId) {}
	public record NovoAtendimento(UUID canalId, String telefone, String nome, Long setorId, String texto) {}
	public record Evento(String tipo, UUID canalId, String estado, String identificador,
			String telefone, String nome, String texto, String tipoMime,
			String nomeArquivo, String conteudo) {}

	private final ChatServico servico;
	private final ChatTransporte transporte;
	private final byte[] chaveInterna;
	private final byte[] segredoChatwoot;
	private final UUID canalChatwoot;
	private final int caixaChatwoot;
	private final ObjectMapper json;
	private final ServicoArquivosChat arquivos;
	private final ServicoBots bots;

	public ChatControle(ChatServico servico, ChatTransporte transporte, ObjectMapper json,
			ServicoArquivosChat arquivos, ServicoBots bots,
			@Value("${APP_CHAT_BRIDGE_KEY:}") String chaveInterna,
			@Value("${APP_CHATWOOT_WEBHOOK_SECRET:}") String segredoChatwoot,
			@Value("${APP_CHATWOOT_CANAL_ID:}") String canalChatwoot,
			@Value("${APP_CHATWOOT_INBOX_NUMERO:0}") int caixaChatwoot) {
		this.servico = servico;
		this.transporte = transporte;
		this.json = json;
		this.arquivos = arquivos;
		this.bots = bots;
		this.chaveInterna = chaveInterna.getBytes(StandardCharsets.UTF_8);
		this.segredoChatwoot = segredoChatwoot.getBytes(StandardCharsets.UTF_8);
		this.canalChatwoot = canalChatwoot.isBlank() ? null : UUID.fromString(canalChatwoot);
		this.caixaChatwoot = caixaChatwoot;
	}

	@GetMapping("/admin/setores")
	public List<Map<String, Object>> setores() { return servico.setores(); }

	@PostMapping("/admin/setores")
	public Map<String, Object> criarSetor(@RequestBody NovoSetor pedido) {
		return servico.criarSetor(pedido.nome());
	}

	@PutMapping("/admin/setores/{id}")
	public void atualizarSetor(@PathVariable long id, @RequestBody AtualizacaoSetor pedido) {
		if (pedido.ativo() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado obrigatório");
		servico.atualizarSetor(id, pedido.nome(), pedido.ativo());
	}

	@GetMapping("/admin/atendentes")
	public List<Map<String, Object>> atendentes() { return servico.atendentes(); }

	@PostMapping("/admin/atendentes")
	public Map<String, Object> criarAtendente(@RequestBody NovoAtendente pedido) {
		return servico.criarAtendente(pedido.identidade(), pedido.nome(), pedido.setores());
	}

	@PostMapping("/admin/atendentes/{id}/setores/{setor}")
	public void vincularSetor(@PathVariable long id, @PathVariable long setor) {
		servico.vincularSetor(id, setor);
	}

	@DeleteMapping("/admin/atendentes/{id}/setores/{setor}")
	public void desvincularSetor(@PathVariable long id, @PathVariable long setor) {
		servico.desvincularSetor(id, setor);
	}

	@PutMapping("/admin/atendentes/{id}")
	public void atualizarAtendente(@PathVariable long id, @RequestBody AtualizacaoAtendente pedido) {
		if (pedido.ativo() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado obrigatório");
		servico.atualizarAtendente(id, pedido.nome(), pedido.ativo());
	}

	@DeleteMapping("/admin/atendentes/{id}")
	public void desativarAtendente(@PathVariable long id) { servico.desativarAtendente(id); }

	@PutMapping("/disponibilidade")
	public void disponibilidade(@RequestBody Disponibilidade pedido, Authentication usuario) {
		servico.disponibilidade(usuario, pedido.estado());
	}

	@GetMapping("/admin/canais")
	public List<Map<String, Object>> canais() { return servico.canais(); }

	@PostMapping("/admin/canais")
	public Map<String, Object> criarCanal(@RequestBody NovoCanal pedido) {
		return servico.criarCanal(pedido.nome(), pedido.telefone(), pedido.setorPadraoId(),
				pedido.inatividadeMinutos(), pedido.mensagemEncerramento());
	}

	@PutMapping("/admin/canais/{id}")
	public void atualizarCanal(@PathVariable UUID id, @RequestBody NovoCanal pedido) {
		servico.atualizarCanal(id, pedido.nome(), pedido.setorPadraoId(),
				pedido.inatividadeMinutos(), pedido.mensagemEncerramento());
	}

	@PostMapping("/admin/canais/{id}/conectar")
	public void conectar(@PathVariable UUID id) {
		servico.conferirCanalAtivo(id);
		if (!"BAILEYS".equals(servico.provedorCanal(id)))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Conexão por QR indisponível");
		transporte.conectar(id);
	}

	@PostMapping("/admin/canais/{id}/desconectar")
	public void desconectar(@PathVariable UUID id) {
		servico.conferirCanalAtivo(id);
		if (!"BAILEYS".equals(servico.provedorCanal(id)))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Canal oficial não usa sessão local");
		transporte.desconectar(id);
	}

	@DeleteMapping("/admin/canais/{id}")
	public void excluirCanal(@PathVariable UUID id) {
		servico.conferirCanalAtivo(id);
		if ("BAILEYS".equals(servico.provedorCanal(id))) transporte.desconectar(id);
		servico.ativarCanal(id, false);
	}

	@PutMapping("/admin/canais/{id}/ativo")
	public void ativarCanal(@PathVariable UUID id, @RequestBody Map<String, Boolean> pedido) {
		if (!pedido.containsKey("ativo")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		String provedor = servico.provedorCanal(id);
		servico.ativarCanal(id, pedido.get("ativo"));
		if (!pedido.get("ativo") && "BAILEYS".equals(provedor)) transporte.desconectar(id);
	}

	@GetMapping("/admin/canais/{id}/qr")
	public Map<String, String> qr(@PathVariable UUID id) {
		if (!"BAILEYS".equals(servico.provedorCanal(id)))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "QR indisponível");
		return Map.of("qr", servico.qr(id));
	}

	@GetMapping("/atendimentos")
	public List<Map<String, Object>> fila(Authentication usuario) { return servico.fila(usuario); }

	@GetMapping("/atendimentos/finalizados")
	public List<Map<String, Object>> finalizados(Authentication usuario) { return servico.finalizados(usuario); }

	@GetMapping("/admin/indicadores")
	public List<Map<String, Object>> indicadores(Authentication usuario) { return servico.indicadores(usuario); }

	@GetMapping("/canais")
	public List<Map<String, Object>> canaisDisponiveis() { return servico.canaisDisponiveis(); }

	@PostMapping("/atendimentos")
	public Map<String, UUID> iniciar(@RequestBody NovoAtendimento pedido, Authentication usuario) {
		UUID id = servico.iniciar(pedido.canalId(), pedido.telefone(), pedido.nome(),
				pedido.setorId(), usuario);
		if (pedido.texto() != null && !pedido.texto().isBlank()) servico.enviar(id, pedido.texto(), usuario);
		return Map.of("id", id);
	}

	@PostMapping("/atendimentos/{id}/assumir")
	public void assumir(@PathVariable UUID id, Authentication usuario) { servico.assumir(id, usuario); }

	@PostMapping("/atendimentos/{id}/transferir")
	public void transferir(@PathVariable UUID id, @RequestBody Transferencia pedido,
			Authentication usuario) {
		servico.transferir(id, pedido.setorId(), pedido.atendenteId(), usuario);
	}

	@PostMapping("/atendimentos/{id}/finalizar")
	public void finalizar(@PathVariable UUID id, Authentication usuario) {
		servico.enviarAvisoEncerramento(servico.finalizar(id, usuario));
	}

	@PostMapping("/atendimentos/{id}/reativar")
	public void reativar(@PathVariable UUID id, Authentication usuario) { servico.reativar(id, usuario); }

	@GetMapping("/atendimentos/{id}/historico-contato")
	public List<Map<String, Object>> historicoContato(@PathVariable UUID id, Authentication usuario) {
		return servico.historicoContato(id, usuario);
	}

	@PostMapping("/atendimentos/{id}/sugestao-ia")
	public Map<String, String> sugerirResposta(@PathVariable UUID id, @RequestBody PedidoSugestao pedido,
			Authentication usuario) {
		return Map.of("texto", servico.sugerirResposta(id, pedido.agenteId(), usuario));
	}

	@PostMapping("/atendimentos/{id}/lidas")
	public void marcarComoLidas(@PathVariable UUID id, Authentication usuario) {
		servico.marcarComoLidas(id, usuario);
	}

	@PostMapping("/atendimentos/{id}/avaliacao")
	public void avaliar(@PathVariable UUID id, @RequestBody Avaliacao pedido, Authentication usuario) {
		servico.avaliar(id, pedido.nota(), usuario);
	}

	@GetMapping("/atendimentos/{id}/mensagens")
	public List<Map<String, Object>> mensagens(@PathVariable UUID id, Authentication usuario) {
		return servico.mensagens(id, usuario);
	}

	@GetMapping("/atendimentos/{id}/acoes")
	public List<Map<String, Object>> acoes(@PathVariable UUID id, Authentication usuario) {
		return servico.acoes(id, usuario);
	}

	@PostMapping("/atendimentos/{id}/mensagens")
	public void enviar(@PathVariable UUID id, @RequestBody Texto pedido, Authentication usuario) {
		servico.enviar(id, pedido.texto(), usuario);
	}

	@PostMapping("/atendimentos/{id}/mensagens/{mensagem}/lida")
	public void marcarMensagemLida(@PathVariable UUID id, @PathVariable long mensagem,
			Authentication usuario) { servico.marcarMensagemLida(id, mensagem, usuario); }

	@DeleteMapping("/atendimentos/{id}/mensagens/{mensagem}")
	public void ocultarMensagem(@PathVariable UUID id, @PathVariable long mensagem,
			Authentication usuario) { servico.ocultarMensagem(id, mensagem, usuario); }

	@PostMapping("/interno/eventos")
	public void evento(@RequestHeader(value = "X-Chat-Bridge-Key", required = false) String chave,
			@RequestBody Evento evento) {
		byte[] recebida = chave == null ? new byte[0] : chave.getBytes(StandardCharsets.UTF_8);
		if (chaveInterna.length < 32 || !MessageDigest.isEqual(chaveInterna, recebida)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		if (evento == null || evento.canalId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		switch (evento.tipo()) {
			case "estado" -> servico.estadoCanal(evento.canalId(), evento.estado());
			case "mensagem" -> {
				if (evento.conteudo() == null) {
					boolean nova = servico.receber(evento.canalId(), evento.identificador(),
							evento.telefone(), evento.nome(), evento.texto());
					if (nova) bots.enviarResposta(bots.processarEntrada(
							evento.canalId(), evento.telefone(), evento.texto(), evento.identificador()));
				}
				else arquivos.receber(evento.canalId(), evento.identificador(), evento.telefone(),
						evento.nome(), evento.texto(), evento.nomeArquivo(), evento.tipoMime(), evento.conteudo());
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido");
		}
	}

	@PostMapping("/interno/chatwoot")
	public void respostaChatwoot(@RequestHeader(value = "X-Chatwoot-Timestamp", required = false) String instante,
			@RequestHeader(value = "X-Chatwoot-Signature", required = false) String assinatura,
			@RequestBody byte[] corpo) {
		if (segredoChatwoot.length < 32 || canalChatwoot == null || caixaChatwoot < 1)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
		try {
			long segundos = Long.parseLong(instante);
			if (Math.abs(Instant.now().getEpochSecond() - segundos) > 300)
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(segredoChatwoot, "HmacSHA256"));
			byte[] esperado = mac.doFinal((instante + "." + new String(corpo, StandardCharsets.UTF_8))
					.getBytes(StandardCharsets.UTF_8));
			byte[] recebido = assinatura != null && assinatura.matches("sha256=[0-9a-fA-F]{64}")
					? HexFormat.of().parseHex(assinatura.substring(7)) : new byte[0];
			if (!MessageDigest.isEqual(esperado, recebido))
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			JsonNode evento = json.readTree(corpo);
			if (!"message_created".equals(evento.path("event").asText())
					|| !"outgoing".equals(evento.path("message_type").asText())
					|| evento.path("private").asBoolean()
					|| evento.path("inbox").path("id").asInt() != caixaChatwoot) return;
			String telefone = evento.path("conversation").path("meta").path("sender")
					.path("phone_number").asText().replaceFirst("^\\+", "");
			String texto = evento.path("content").asText();
			if (!telefone.matches("[0-9]{8,15}") || texto.isBlank())
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resposta sem destino ou texto");
			transporte.enviar(canalChatwoot, telefone, texto);
		} catch (ResponseStatusException erro) {
			throw erro;
		} catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook inválido", erro);
		}
	}
}
