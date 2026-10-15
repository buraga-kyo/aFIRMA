package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ValidadorPedidoAssinaturaTest {

	private RepositorioConfiguracaoAssinatura repositorio;
	private ValidadorPedidoAssinatura validador;

	@BeforeEach
	void preparar() {
		repositorio = mock(RepositorioConfiguracaoAssinatura.class);
		ConfiguracaoAssinatura configuracao = mock(ConfiguracaoAssinatura.class);
		when(configuracao.getTamanhoMaximoPdfBytes()).thenReturn(1024L);
		when(repositorio.findById(1L)).thenReturn(Optional.of(configuracao));
		validador = new ValidadorPedidoAssinatura(repositorio);
	}

	@Test
	void aceitaMimeECabecalhoPdf() {
		byte[] conteudo = "%PDF-1.7\nconteudo".getBytes(StandardCharsets.US_ASCII);
		assertArrayEquals(conteudo, validador.validar(pedido("application/pdf", conteudo)).pdf());
	}

	@Test
	void rejeitaMimeDeclaradoIncorreto() {
		ResponseStatusException erro = assertThrows(ResponseStatusException.class,
				() -> validador.validar(pedido("text/plain", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, erro.getStatusCode());
	}

	@Test
	void rejeitaConteudoDisfarcadoDePdf() {
		ResponseStatusException erro = assertThrows(ResponseStatusException.class,
				() -> validador.validar(pedido("application/pdf", "não é pdf".getBytes(StandardCharsets.UTF_8))));
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, erro.getStatusCode());
	}

	@Test
	void aplicaLimiteDoBanco() {
		ConfiguracaoAssinatura configuracao = mock(ConfiguracaoAssinatura.class);
		when(configuracao.getTamanhoMaximoPdfBytes()).thenReturn(7L);
		when(repositorio.findById(1L)).thenReturn(Optional.of(configuracao));
		ResponseStatusException erro = assertThrows(ResponseStatusException.class,
				() -> validador.validar(pedido("application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))));
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, erro.getStatusCode());
	}

	@Test
	void falhaFechadoSemConfiguracao() {
		when(repositorio.findById(1L)).thenReturn(Optional.empty());
		ResponseStatusException erro = assertThrows(ResponseStatusException.class,
				() -> validador.validar(pedido("application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, erro.getStatusCode());
	}

	private PedidoAssinaturaPdf pedido(String mime, byte[] conteudo) {
		PedidoAssinaturaPdf pedido = new PedidoAssinaturaPdf();
		pedido.setPdf(new MockMultipartFile("pdf", "arquivo.pdf", mime, conteudo));
		pedido.setCertificado(new MockMultipartFile("certificado", "certificado.pfx", "application/octet-stream", new byte[] {1}));
		pedido.setSenha("senha-de-teste");
		return pedido;
	}
}
