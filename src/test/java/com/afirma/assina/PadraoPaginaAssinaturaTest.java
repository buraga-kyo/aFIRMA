package com.afirma.assina;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PadraoPaginaAssinaturaTest {
  @Test void assinaturaIndividualAplicaPadraoMesmoQuandoClienteTentaDesligarOuTrocar() throws Exception {
    var servico = mock(ServicoAssinaturaPdf.class);
    var validador = mock(ValidadorPedidoAssinatura.class);
    var paginas = mock(PaginaAssinaturaEmpresa.class);
    var modelo = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow();
    when(paginas.daRequisicao(any())).thenReturn(new PaginaAssinaturaEmpresa.Configuracao("Firma", modelo));
    when(validador.validar(any())).thenReturn(new ValidadorPedidoAssinatura.ArquivosValidados(new byte[]{1}, new byte[]{2}));
    when(servico.assinar(any(byte[].class), any(byte[].class), any(char[].class), eq(false), eq(true), eq("Firma"), isNull(), eq(modelo)))
        .thenReturn(new byte[]{3});
    var mvc = MockMvcBuilders.standaloneSetup(new ControleAssinaturaPdf(servico, validador, false, paginas)).build();
    mvc.perform(multipart("/api/assineai/pdf/assinar")
        .file(new MockMultipartFile("pdf", "teste.pdf", "application/pdf", new byte[]{1}))
        .file(new MockMultipartFile("certificado", "teste.pfx", "application/octet-stream", new byte[]{2}))
        .param("senha", "senha").param("paginaPersonalizada", "false").param("templatePagina", "modelo avulso"))
        .andExpect(status().isOk());
    verify(servico).assinar(any(byte[].class), any(byte[].class), any(char[].class), eq(false), eq(true), eq("Firma"), isNull(), eq(modelo));
  }

  @Test void carteiraTambemUsaPadraoDaEmpresa() throws Exception {
    var servico = mock(ServicoAssinaturaBlockchain.class);
    var paginas = mock(PaginaAssinaturaEmpresa.class);
    var modelo = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow();
    var pedido = new org.springframework.mock.web.MockHttpServletRequest();
    pedido.setRequestURI("/api/assineai/blockchain/preparar");
    when(paginas.daRequisicao(pedido)).thenReturn(new PaginaAssinaturaEmpresa.Configuracao("Firma", modelo));
    var pdf = new MockMultipartFile("pdf", new byte[]{1});
    new ControleAssinaturaBlockchain(servico, paginas).preparar(pdf, "carteira", false, "outro nome", null, "avulso", pedido);
    verify(servico).preparar(pdf, "carteira", true, "Firma", null, modelo);
  }
}
