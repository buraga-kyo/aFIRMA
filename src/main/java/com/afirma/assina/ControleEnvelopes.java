package com.afirma.assina;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ControleEnvelopes {
	private final ServicoEnvelopes servico;

	public ControleEnvelopes(ServicoEnvelopes servico) {
		this.servico = servico;
	}

	@PostMapping(value = "/envelopes/previsualizar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> previsualizar(@RequestParam("pdf") MultipartFile pdf,
			@RequestParam("pagina") int pagina) throws Exception {
		return imagem(servico.previsualizar(pdf, pagina));
	}

	@PostMapping(value = "/envelopes/paginas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public NumeroPaginas paginas(@RequestParam("pdf") MultipartFile pdf) throws Exception {
		return new NumeroPaginas(servico.paginas(pdf));
	}

	@PostMapping(value = "/envelopes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ServicoEnvelopes.Criado criar(@RequestParam("pdf") MultipartFile pdf,
			@RequestPart("dados") ServicoEnvelopes.PedidoCriacao dados, Principal usuario) throws Exception {
		return servico.criar(pdf, dados, usuario.getName());
	}

	@GetMapping("/envelopes/{id}")
	public ServicoEnvelopes.EstadoEnvelope consultar(@PathVariable UUID id, Principal usuario) {
		return servico.consultar(id, usuario.getName());
	}

	@PostMapping(value = "/envelopes/{id}/finalizar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> finalizar(@PathVariable UUID id,
			@RequestParam("certificado") MultipartFile certificado, @RequestParam("senha") String senha,
			@RequestParam(value = "paginaPersonalizada", defaultValue = "false") boolean paginaPersonalizada,
			@RequestParam(value = "imagemEmpresa", required = false) MultipartFile imagemEmpresa,
			Principal usuario) throws Exception {
		return pdf(servico.finalizar(id, usuario.getName(), certificado, senha,
				paginaPersonalizada, imagemEmpresa));
	}

	@GetMapping(value = "/envelopes/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> documentoFinal(@PathVariable UUID id, Principal usuario) throws Exception {
		return pdf(servico.documentoFinal(id, usuario.getName()));
	}

	@GetMapping("/convites/{token}")
	public ServicoEnvelopes.DadosConvite convite(@PathVariable String token) {
		return servico.consultarConvite(token);
	}

	@GetMapping(value = "/convites/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> documentoConvite(@PathVariable String token) throws Exception {
		return pdf(servico.documentoConvite(token));
	}

	@GetMapping(value = "/convites/{token}/paginas/{pagina}", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> paginaConvite(@PathVariable String token,
			@PathVariable int pagina) throws Exception {
		return imagem(servico.previsualizarConvite(token, pagina));
	}

	@GetMapping(value = "/convites/{token}/final", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> documentoFinalConvite(@PathVariable String token) {
		return pdf(servico.documentoFinalConvite(token));
	}

	@PostMapping(value = "/convites/{token}/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Void> assinar(@PathVariable String token,
			@RequestParam(value = "assinatura", required = false) MultipartFile assinatura,
			@RequestParam(value = "rubrica", required = false) MultipartFile rubrica,
			@RequestParam(value = "selfie", required = false) MultipartFile selfie,
			@RequestParam(value = "aceite", defaultValue = "false") boolean aceite,
			HttpServletRequest requisicao) throws Exception {
		servico.assinarConvite(token, assinatura, rubrica, selfie, aceite, requisicao.getRemoteAddr());
		return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
	}

	private ResponseEntity<byte[]> pdf(byte[] dados) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header("Referrer-Policy", "no-referrer")
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("documento.pdf").build().toString())
				.contentType(MediaType.APPLICATION_PDF).body(dados);
	}

	private ResponseEntity<byte[]> imagem(byte[] dados) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.contentType(MediaType.IMAGE_PNG).body(dados);
	}

	public record NumeroPaginas(int paginas) { }
}
