package com.afirma.assina;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pdfs")
public class ControleAssinaturaPdf {

	private final ServicoAssinaturaPdf servico;
	private final ValidadorPedidoAssinatura validador;

	public ControleAssinaturaPdf(ServicoAssinaturaPdf servico, ValidadorPedidoAssinatura validador) {
		this.servico = servico;
		this.validador = validador;
	}

	@PostMapping(value = "/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> assinar(@Valid @ModelAttribute PedidoAssinaturaPdf pedido) throws Exception {
		ValidadorPedidoAssinatura.ArquivosValidados arquivos = validador.validar(pedido);
		char[] senhaCertificado = pedido.getSenha().toCharArray();
		try {
			byte[] assinado = servico.assinar(arquivos.pdf(), arquivos.certificado(), senhaCertificado,
					pedido.isPermitirCertificadoVencido());
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename("documento-assinado.pdf").build().toString())
					.header(HttpHeaders.CACHE_CONTROL, "no-store")
					.header("X-Content-Type-Options", "nosniff")
					.contentType(MediaType.APPLICATION_PDF)
					.body(assinado);
		} catch (IllegalArgumentException | GeneralSecurityException | IOException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF, certificado ou senha inválidos", erro);
		} finally {
			Arrays.fill(senhaCertificado, '\0');
		}
	}
}
