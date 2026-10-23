package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ControleArquivosChat {
	private final ServicoArquivosChat servico;

	public ControleArquivosChat(ServicoArquivosChat servico) { this.servico = servico; }

	@PostMapping(value = "/atendimentos/{id}/arquivos", consumes = "multipart/form-data")
	public Map<String, Object> enviar(@PathVariable UUID id, @RequestParam MultipartFile arquivo,
			@RequestParam(required = false) String legenda, Authentication usuario) {
		return servico.enviar(id, arquivo, legenda, usuario);
	}

	@GetMapping("/arquivos/{id}")
	public ResponseEntity<ByteArrayResource> baixar(@PathVariable UUID id, Authentication usuario) {
		ServicoArquivosChat.Arquivo arquivo = servico.baixar(id, usuario);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.parseMediaType(arquivo.tipo()))
				.contentLength(arquivo.conteudo().length)
				.body(new ByteArrayResource(arquivo.conteudo()));
	}
}
