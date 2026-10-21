package com.afirma.assina;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advai")
public class ControlePropostas {
	private final ServicoPropostas propostas;

	public ControlePropostas(ServicoPropostas propostas) { this.propostas = propostas; }

	@GetMapping("/catalogo")
	public java.util.List<ServicoPropostas.Area> catalogo() { return propostas.catalogo(); }

	@PostMapping(value = "/propostas/pdf", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> pdf(@RequestBody ServicoPropostas.Dados dados) throws Exception {
		return arquivo(propostas.pdf(dados), "proposta.pdf", MediaType.APPLICATION_PDF);
	}

	@PostMapping(value = "/propostas/docx", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> docx(@RequestBody ServicoPropostas.Dados dados) throws Exception {
		return arquivo(propostas.docx(dados), "proposta.docx",
				MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
	}

	private ResponseEntity<byte[]> arquivo(byte[] dados, String nome, MediaType tipo) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
				.contentType(tipo).body(dados);
	}
}
