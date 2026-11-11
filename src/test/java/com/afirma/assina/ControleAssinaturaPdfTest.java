package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.security.UnrecoverableKeyException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ControleAssinaturaPdfTest {

  private ValidadorPedidoAssinatura validador() {
    var repositorio = mock(RepositorioConfiguracaoAssinatura.class);
    var configuracao = mock(ConfiguracaoAssinatura.class);
    when(configuracao.getTamanhoMaximoPdfBytes()).thenReturn(1024L);
    when(repositorio.findById(1L)).thenReturn(Optional.of(configuracao));
    return new ValidadorPedidoAssinatura(repositorio);
  }

  private MockMultipartHttpServletRequestBuilder pedido() {
    var requisicao = multipart("/api/pdfs/assinar")
      .file(
        new MockMultipartFile(
          "pdf",
          "documento.pdf",
          "application/pdf",
          "%PDF-1.7\n".getBytes()
        )
      )
      .file(
        new MockMultipartFile(
          "certificado",
          "empresa.pfx",
          "application/octet-stream",
          new byte[] { 1, 2, 3 }
        )
      );
    requisicao
      .param("idRequisicao", UUID.randomUUID().toString())
      .param("senha", "senha-teste")
      .param("tipoDocumento", "CONTRATO_PARTICULAR")
      .param(
        "declaracaoFormalidades",
        PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES
      )
      .param(
        "declaracaoPoderes",
        PoliticaDocumentosAssinatura.DECLARACAO_PODERES
      )
      .principal(() -> "criador");
    return requisicao;
  }

  @Test
  void devolveMotivoDaSenhaIncorreta() throws Exception {
    var servico = mock(ServicoAssinaturaDireta.class);
    when(
      servico.assinar(any(), any(), any(), eq(false), eq("criador"))
    ).thenThrow(new UnrecoverableKeyException("Senha incorreta"));
    var mvc = MockMvcBuilders.standaloneSetup(
      new ControleAssinaturaPdf(servico, validador(), false, mock(PaginaAssinaturaEmpresa.class))
    )
      .setControllerAdvice(new TratamentoErrosApi())
      .build();
    mvc
      .perform(pedido().param("permitirCertificadoVencido", "true"))
      .andExpect(status().isBadRequest())
      .andExpect(
        jsonPath("$.mensagem").value("Senha do certificado incorreta")
      );
  }

  @Test
  void devolvePdfComReferenciaDasEvidenciasELimpaCopiasDoSegredo()
    throws Exception {
    var servico = mock(ServicoAssinaturaDireta.class);
    byte[] pdf = "%PDF-assinado".getBytes();
    UUID identificador = UUID.randomUUID();
    AtomicReference<char[]> senhaRecebida = new AtomicReference<>();
    AtomicReference<byte[]> certificadoRecebido = new AtomicReference<>();
    when(
      servico.assinar(any(), any(), any(), eq(true), eq("criador"))
    ).thenAnswer(chamada -> {
      senhaRecebida.set(chamada.getArgument(2));
      certificadoRecebido.set(
        (
          (ValidadorPedidoAssinatura.ArquivosValidados) chamada.getArgument(1)
        ).certificado()
      );
      return new ServicoAssinaturaDireta.Assinado(identificador, pdf);
    });
    var mvc = MockMvcBuilders.standaloneSetup(
      new ControleAssinaturaPdf(servico, validador(), true, mock(PaginaAssinaturaEmpresa.class))
    )
      .setControllerAdvice(new TratamentoErrosApi())
      .build();
    mvc
      .perform(pedido())
      .andExpect(status().isOk())
      .andExpect(content().bytes(pdf))
      .andExpect(
        header().string("X-Envelope-Assinatura", identificador.toString())
      )
      .andExpect(header().string("Cache-Control", "no-store"));
    assertArrayEquals(new char["senha-teste".length()], senhaRecebida.get());
    assertArrayEquals(new byte[3], certificadoRecebido.get());
  }

  @Test
  void exigeDeclaracoesDoDocumentoMesmoNoEndpointDireto() throws Exception {
    var servico = mock(ServicoAssinaturaDireta.class);
    var mvc = MockMvcBuilders.standaloneSetup(
      new ControleAssinaturaPdf(servico, validador(), false, mock(PaginaAssinaturaEmpresa.class))
    )
      .setControllerAdvice(new TratamentoErrosApi())
      .build();
    mvc
      .perform(
        pedido().with(requisicao -> {
          requisicao.setParameter("declaracaoPoderes", "");
          return requisicao;
        })
      )
      .andExpect(status().isBadRequest());
    verifyNoInteractions(servico);
  }
}
