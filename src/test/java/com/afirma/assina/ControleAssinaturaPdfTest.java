package com.afirma.assina;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.UnrecoverableKeyException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ControleAssinaturaPdfTest {

	@Test
	void devolveMotivoDaSenhaIncorreta() throws Exception {
		RepositorioConfiguracaoAssinatura repositorio = mock(RepositorioConfiguracaoAssinatura.class);
		ConfiguracaoAssinatura configuracao = mock(ConfiguracaoAssinatura.class);
		when(configuracao.getTamanhoMaximoPdfBytes()).thenReturn(1024L);
		when(repositorio.findById(1L)).thenReturn(Optional.of(configuracao));
		ServicoAssinaturaPdf servico = mock(ServicoAssinaturaPdf.class);
		when(servico.assinar(any(byte[].class), any(byte[].class), any(char[].class), eq(false)))
				.thenThrow(new UnrecoverableKeyException("Senha incorreta"));
		var mvc = MockMvcBuilders.standaloneSetup(
				new ControleAssinaturaPdf(servico, new ValidadorPedidoAssinatura(repositorio), false))
				.setControllerAdvice(new TratamentoErrosApi()).build();

		mvc.perform(multipart("/api/pdfs/assinar")
				.file(new MockMultipartFile("pdf", "arquivo.pdf", "application/pdf",
						"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)))
				.file(new MockMultipartFile("certificado", "certificado.pfx", "application/octet-stream",
						new byte[] {1, 2, 3}))
				.param("senha", "senha-incorreta")
				.param("permitirCertificadoVencido", "true"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("Senha do certificado incorreta"));
	}

	@Test
	void recebeMultipartEDevolvePdf() throws Exception {
		RepositorioConfiguracaoAssinatura repositorio = mock(RepositorioConfiguracaoAssinatura.class);
		ConfiguracaoAssinatura configuracao = mock(ConfiguracaoAssinatura.class);
		when(configuracao.getTamanhoMaximoPdfBytes()).thenReturn(1024L);
		when(repositorio.findById(1L)).thenReturn(Optional.of(configuracao));
		ServicoAssinaturaPdf servico = mock(ServicoAssinaturaPdf.class);
		byte[] assinado = "%PDF-assinado".getBytes(StandardCharsets.US_ASCII);
		when(servico.assinar(any(byte[].class), any(byte[].class), any(char[].class), eq(true)))
				.thenReturn(assinado);
		var controle = new ControleAssinaturaPdf(servico, new ValidadorPedidoAssinatura(repositorio), true);
		var mvc = MockMvcBuilders.standaloneSetup(controle)
				.setControllerAdvice(new TratamentoErrosApi()).build();

		mvc.perform(multipart("/api/pdfs/assinar")
				.file(new MockMultipartFile("pdf", "arquivo.pdf", "application/pdf",
						"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)))
				.file(new MockMultipartFile("certificado", "certificado.pfx", "application/octet-stream",
						new byte[] {1, 2, 3}))
				.param("senha", "senha-de-teste"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().bytes(assinado));
		verify(servico).assinar(any(byte[].class), any(byte[].class), any(char[].class), eq(true));
	}
}
