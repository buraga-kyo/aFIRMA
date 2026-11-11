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
    var servico = mock(ServicoAssinaturaDireta.class);
    var validador = mock(ValidadorPedidoAssinatura.class);
    var paginas = mock(PaginaAssinaturaEmpresa.class);
    var modelo = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow();
    when(paginas.daRequisicao(any())).thenReturn(new PaginaAssinaturaEmpresa.Configuracao("Firma", modelo));
    when(validador.validar(any())).thenReturn(new ValidadorPedidoAssinatura.ArquivosValidados(new byte[]{1}, new byte[]{2}));
    when(servico.assinar(any(), any(), any(), anyBoolean(), anyString()))
        .thenReturn(new ServicoAssinaturaDireta.Assinado(java.util.UUID.randomUUID(), new byte[]{9}));
    when(validador.validar(any())).thenReturn(new ValidadorPedidoAssinatura.ArquivosValidados(new byte[]{1},new byte[]{2}));
    var pedido = new PedidoAssinaturaPdf();
    pedido.setSenha("senha");
    var requisicao = new org.springframework.mock.web.MockHttpServletRequest();
    new ControleAssinaturaPdf(servico, validador, false, paginas).assinar(pedido, requisicao, () -> "criador");
    org.junit.jupiter.api.Assertions.assertTrue(pedido.isPaginaPersonalizada());
    org.junit.jupiter.api.Assertions.assertEquals("Firma", pedido.getNomeEmpresa());

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
