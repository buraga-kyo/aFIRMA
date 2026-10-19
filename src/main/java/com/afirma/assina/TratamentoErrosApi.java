package com.afirma.assina;

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

	private ResponseEntity<ErroApi> resposta(HttpStatus status, String mensagem) {
		return ResponseEntity.status(status)
				.header("Cache-Control", "no-store")
				.body(new ErroApi(status.name(), mensagem));
	}
}
