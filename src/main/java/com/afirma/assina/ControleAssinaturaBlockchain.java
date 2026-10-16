package com.afirma.assina;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/blockchain")
public class ControleAssinaturaBlockchain {
	private final ServicoAssinaturaBlockchain servico;

	public ControleAssinaturaBlockchain(ServicoAssinaturaBlockchain servico) {
		this.servico = servico;
	}

	@PostMapping(value = "/preparar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ServicoAssinaturaBlockchain.Preparacao preparar(@RequestParam("pdf") MultipartFile pdf,
			@RequestParam("carteira") String carteira) throws Exception {
		try {
			return servico.preparar(pdf, carteira);
		} catch (IllegalArgumentException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, erro.getMessage(), erro);
		}
	}

	@PostMapping(value = "/concluir", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> concluir(@RequestBody PedidoConclusao pedido) {
		if (pedido == null || pedido.identificador() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador obrigatório");
		}
		try {
			byte[] pdf = servico.concluir(pedido.identificador(), pedido.assinatura(), pedido.transacao());
			if (pdf == null) return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename("documento-blockchain.pdf").build().toString())
					.header(HttpHeaders.CACHE_CONTROL, "no-store")
					.contentType(MediaType.APPLICATION_PDF).body(pdf);
		} catch (IllegalArgumentException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, erro.getMessage(), erro);
		}
	}

	@PostMapping(value = "/verificar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResultadoVerificacao verificar(@RequestParam("pdf") MultipartFile pdf) throws Exception {
		try {
			ProvaBlockchain prova = servico.verificar(pdf);
			return new ResultadoVerificacao(true, prova.carteira(), prova.resumoPdf(),
					prova.transacao(), prova.dataHora());
		} catch (IllegalArgumentException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, erro.getMessage(), erro);
		}
	}

	public record PedidoConclusao(UUID identificador, String assinatura, String transacao) { }
	public record ResultadoVerificacao(boolean valido, String carteira, String resumoPdf,
			String transacao, long dataHora) { }
}
