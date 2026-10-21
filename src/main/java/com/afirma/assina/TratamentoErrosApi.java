package com.afirma.assina;

import java.io.IOException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class TratamentoErrosApi {
	private static final Logger log = LoggerFactory.getLogger(TratamentoErrosApi.class);

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErroApi> tamanhoExcedido() {
		return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede limite permitido");
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, MultipartException.class})
	ResponseEntity<ErroApi> pedidoInvalido() {
		return resposta(HttpStatus.BAD_REQUEST, "Pedido inválido");
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ErroApi> erroConhecido(ResponseStatusException erro) {
		HttpStatus status = HttpStatus.resolve(erro.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		log.warn("Pedido recusado. Status: {}. Motivo: {}", status.value(), erro.getReason());
		return resposta(status, erro.getReason() == null ? status.getReasonPhrase() : erro.getReason());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErroApi> conflitoDeDados() {
		return resposta(HttpStatus.CONFLICT, "Registro duplicado ou ainda em uso");

	}

	@ExceptionHandler(UnrecoverableKeyException.class)
	ResponseEntity<ErroApi> senhaCertificadoIncorreta() {
		return resposta(HttpStatus.BAD_REQUEST, "Senha do certificado incorreta");
	}

	@ExceptionHandler(CertificateExpiredException.class)
	ResponseEntity<ErroApi> certificadoVencido() {
		return resposta(HttpStatus.BAD_REQUEST, "Certificado vencido");
	}

	@ExceptionHandler(CertificateNotYetValidException.class)
	ResponseEntity<ErroApi> certificadoFuturo() {
		return resposta(HttpStatus.BAD_REQUEST, "Certificado ainda não válido");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErroApi> dadosInvalidos() {
		return resposta(HttpStatus.BAD_REQUEST, "Dados inválidos");
	}

	@ExceptionHandler(IOException.class)
	ResponseEntity<ErroApi> arquivoInvalido() {
		return resposta(HttpStatus.BAD_REQUEST, "PDF ou imagem inválidos");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErroApi> falhaInterna(Exception erro) {
		log.error("Falha interna na API", erro);
		return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Falha interna ao processar pedido");
	}

	private ResponseEntity<ErroApi> resposta(HttpStatus status, String mensagem) {
		return ResponseEntity.status(status)
				.header("Cache-Control", "no-store")
				.body(new ErroApi(status.name(), mensagem));
	}
}
