package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class InventorPaginaTest {
  private MockHttpServletRequest pedido() {
    var pedido = new MockHttpServletRequest("GET", "/assineai/inventor");
    pedido.setServletPath("/assineai/inventor");
    return pedido;
  }

  @Test
  void abreBancadaPropria() {
    var modelo = new org.springframework.ui.ExtendedModelMap();
    assertEquals("documentos-assinatura", new ControlePaginaAssinatura().inventor(modelo));
    assertEquals("inventor", modelo.get("telaTrator"));
    assertEquals("documentos-assinatura", new ControlePaginaAssinatura().configurarInventor(modelo));
    assertEquals("conexoes", modelo.get("telaTrator"));
    assertEquals("assinatura", new ControlePaginaAssinatura().assinatura());
  }

  @Test
  void exigeAConexaoCompartilhadaDoTrator() throws Exception {
    var acessos = mock(ServicoAcessoNavegador.class);
    var filtro = new FiltroPaginaComAcesso(acessos);
    var pedido = pedido();
    var resposta = new MockHttpServletResponse();
    var cadeia = mock(FilterChain.class);
    filtro.doFilter(pedido, resposta, cadeia);
    verify(acessos).exigir(pedido);
    verify(cadeia).doFilter(pedido, resposta);
  }

  @Test
  void redirecionaQuandoNaoHaAcesso() throws Exception {
    var acessos = mock(ServicoAcessoNavegador.class);
    when(acessos.exigir(any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    var resposta = new MockHttpServletResponse();
    var cadeia = mock(FilterChain.class);
    new FiltroPaginaComAcesso(acessos).doFilter(pedido(), resposta, cadeia);
    assertEquals("/assineai/trator?acesso=necessario", resposta.getRedirectedUrl());
    verifyNoInteractions(cadeia);
  }

  @Test
  void aplicaCabecalhosDePrivacidadeNaBancada() throws Exception {
    var resposta = new MockHttpServletResponse();
    new FiltroCabecalhosAssinatura().doFilter(pedido(), resposta, mock(FilterChain.class));
    assertEquals("no-store", resposta.getHeader("Cache-Control"));
    assertEquals("no-referrer", resposta.getHeader("Referrer-Policy"));
    assertTrue(resposta.getHeader("Content-Security-Policy").contains("script-src 'self'"));
  }

  @Test
  void ethereumContinuaDesativadoPorPadrao() {
    var servico = mock(ServicoAssinaturaBlockchain.class);
    var controle = new ControleAssinaturaBlockchain(servico, mock(PaginaAssinaturaEmpresa.class));
    var erro =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controle.preparar(
                    new org.springframework.mock.web.MockMultipartFile("pdf", new byte[] {1}),
                    "carteira",
                    true,
                    "Firma",
                    null,
                    null,
                    pedido()));
    assertEquals(422, erro.getStatusCode().value());
    verifyNoInteractions(servico);
  }

  @Test
  void configuracaoExigeAcessoETemCabecalhosPrivados() throws Exception {
    var pedido = new MockHttpServletRequest("GET", "/assineai/inventor/configuracao");
    pedido.setServletPath("/assineai/inventor/configuracao");
    var acessos = mock(ServicoAcessoNavegador.class);
    var resposta = new MockHttpServletResponse();
    new FiltroPaginaComAcesso(acessos).doFilter(pedido, resposta, mock(FilterChain.class));
    verify(acessos).exigir(pedido);
    new FiltroCabecalhosAssinatura().doFilter(pedido, resposta, mock(FilterChain.class));
    assertEquals("no-store", resposta.getHeader("Cache-Control"));
    assertEquals("DENY", resposta.getHeader("X-Frame-Options"));
  }
}
