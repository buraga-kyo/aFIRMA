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
	private static final Logger registroLog = LoggerFactory.getLogger(TratamentoErrosApi.class);

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErroApi> tamanhoExcedido() {
		return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede limite permitido");
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, MultipartException.class,
			org.springframework.web.bind.MissingServletRequestParameterException.class,
			org.springframework.web.multipart.support.MissingServletRequestPartException.class,
			org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
			org.springframework.http.converter.HttpMessageNotReadableException.class})
	ResponseEntity<ErroApi> pedidoInvalido(Exception erro) {
        Throwable causa = erro;
        for (int indice = 0; causa != null && indice < 10; indice++, causa = causa.getCause()) {
            if (causa instanceof ErroAssinatura dadoInvalido) return resposta(HttpStatus.BAD_REQUEST, dadoInvalido.getMessage());
        }
		return resposta(HttpStatus.BAD_REQUEST, "Pedido inválido");
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ErroApi> erroConhecido(ResponseStatusException erro) {
		HttpStatus situacao = HttpStatus.resolve(erro.getStatusCode().value());
		if (situacao == null) {
			situacao = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		registroLog.warn("Pedido recusado. Status: {}. Motivo: {}", situacao.value(), erro.getReason());
		return resposta(situacao, erro.getReason() == null ? situacao.getReasonPhrase() : erro.getReason());
	}

	@ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
	ResponseEntity<ErroApi> documentoEmAtualizacao() {
		return resposta(HttpStatus.CONFLICT, "O documento está sendo atualizado. Aguarde e tente novamente");
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

	@ExceptionHandler(ErroAssinatura.class)
	ResponseEntity<ErroApi> assinaturaInvalida(ErroAssinatura erro) {
		return resposta(HttpStatus.BAD_REQUEST, erro.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErroApi> dadosInvalidos() {
		return resposta(HttpStatus.BAD_REQUEST, "Dados inválidos");
	}

	@ExceptionHandler(IOException.class)
	ResponseEntity<ErroApi> arquivoInvalido(IOException erro) {
        registroLog.warn("Falha ao ler PDF ou imagem: {}",erro.toString());
		return resposta(HttpStatus.BAD_REQUEST, "PDF ou imagem inválidos");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErroApi> falhaInterna(Exception erro) {
		registroLog.error("Falha interna na API", erro);
		return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Falha interna ao processar pedido");
	}

	private ResponseEntity<ErroApi> resposta(HttpStatus situacao, String mensagem) {
		return ResponseEntity.status(situacao)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.header("Cache-Control", "no-store")
				.body(new ErroApi(situacao.name(), mensagem));
	}
}
