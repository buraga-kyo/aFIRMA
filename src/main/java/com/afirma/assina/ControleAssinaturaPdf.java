package com.afirma.assina;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pdfs")
public class ControleAssinaturaPdf {

	private final ServicoAssinaturaPdf servico;

	public ControleAssinaturaPdf(ServicoAssinaturaPdf servico) {
		this.servico = servico;
	}

	@PostMapping(value = "/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> assinar(@RequestParam("pdf") MultipartFile pdf,
			@RequestParam("certificado") MultipartFile certificado,
			@RequestParam("senha") String senha) throws Exception {
		char[] senhaCertificado = senha.toCharArray();
		try {
			byte[] assinado = servico.assinar(pdf.getBytes(), certificado.getBytes(), senhaCertificado);
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename("documento-assinado.pdf").build().toString())
					.contentType(MediaType.APPLICATION_PDF)
					.body(assinado);
		} catch (IllegalArgumentException | GeneralSecurityException | IOException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF, certificado ou senha inválidos", erro);
		} finally {
			Arrays.fill(senhaCertificado, '\0');
		}
	}
}
