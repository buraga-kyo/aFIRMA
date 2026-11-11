package com.afirma.assina;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class ControleEvidenciasAssinatura {
	private final ServicoEvidenciasAssinatura evidencias;
	private final ServicoGestaoAssinaturas gestao;

	public ControleEvidenciasAssinatura(ServicoEvidenciasAssinatura evidencias, ServicoGestaoAssinaturas gestao) {
		this.evidencias = evidencias;
		this.gestao = gestao;
	}
	@GetMapping("/envelopes/{id}/evidencias")
	public ServicoEvidenciasAssinatura.Resumo consultar(@PathVariable UUID id, Principal usuario) {
		return evidencias.consultar(id, usuario.getName());
	}
	@GetMapping(value = "/envelopes/{id}/evidencias/pacote", produces = "application/zip")
	public ResponseEntity<StreamingResponseBody> exportar(@PathVariable UUID id, Principal usuario) {
		evidencias.consultar(id, usuario.getName());
		StreamingResponseBody corpo = destino -> {
			try { evidencias.exportar(id, usuario.getName(), destino); }
			catch (Exception erro) { throw new java.io.IOException("Não foi possível concluir o pacote de evidências", erro); }
		};
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evidencias-" + id + ".zip\"")
				.header("X-Content-Type-Options", "nosniff").body(corpo);
	}
	@PostMapping("/envelopes/{id}/cancelar")
	public void cancelar(@PathVariable UUID id, @RequestBody Motivo pedido, Principal usuario) {
		gestao.cancelar(id, usuario.getName(), pedido.motivo());
	}
	@PostMapping("/envelopes/{id}/participantes/{participante}/substituir-convite")
	public ServicoGestaoAssinaturas.NovoConvite substituir(@PathVariable UUID id, @PathVariable UUID participante,
			@RequestBody Motivo pedido, Principal usuario) {
		return gestao.substituirConvite(id, participante, usuario.getName(), pedido.motivo());
	}
	@PostMapping("/envelopes/{id}/preservacao")
	public void preservar(@PathVariable UUID id, @RequestBody Preservacao pedido, Principal usuario) {
		gestao.estenderPreservacao(id, usuario.getName(), pedido.preservarAte(), pedido.motivo());
	}
	@PostMapping(value = "/envelopes/{id}/relatorios-externos", consumes = "multipart/form-data")
	public void relatorio(@PathVariable UUID id, @RequestParam("relatorio") MultipartFile relatorio,
			@RequestParam("resumoDocumento") String resumoDocumento, @RequestParam("referenciaConsulta") String referencia,
			@RequestParam("resultadoDeclarado") String resultado, Principal usuario) throws Exception {
		gestao.anexarRelatorioExterno(id, usuario.getName(), relatorio, resumoDocumento, referencia, resultado);
	}
	@PostMapping("/convites/{token}/recusar")
	public void recusar(@PathVariable String token, @RequestBody Motivo pedido, HttpServletRequest requisicao) {
		gestao.recusar(token, pedido.motivo(), (ServicoAutenticacaoConvites.Sessao) requisicao.getAttribute(FiltroAcessoConvite.ATRIBUTO_SESSAO));
	}
	public record Motivo(String motivo) { }
	public record Preservacao(Instant preservarAte, String motivo) { }
}
