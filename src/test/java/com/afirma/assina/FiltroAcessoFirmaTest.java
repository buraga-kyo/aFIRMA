package com.afirma.assina;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.FilterChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiltroAcessoFirmaTest {
  @Test void endpointNovoTambemExigeAutenticacaoSemDependerDoControlador() throws Exception {
    var identidade=mock(IdentidadeAssineai.class);
    var filtro=new FiltroAcessoFirma(identidade,mock(ChavesIntegracaoFirma.class));
    var pedido=new MockHttpServletRequest("GET","/api/firma/futuro-recurso");
    var resposta=new MockHttpServletResponse();
    var cadeia=mock(FilterChain.class);
    when(identidade.autenticar(pedido)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    filtro.doFilter(pedido,resposta,cadeia);
    assertEquals(401,resposta.getStatus());
    verifyNoInteractions(cadeia);
  }
  @Test void sessaoValidaSegueParaAutorizacaoDoServico() throws Exception {
    var identidade=mock(IdentidadeAssineai.class);
    var pedido=new MockHttpServletRequest("POST","/api/firma/conversas");
    var resposta=new MockHttpServletResponse();
    var cadeia=mock(FilterChain.class);
    when(identidade.autenticar(pedido)).thenReturn("firma:42");
    new FiltroAcessoFirma(identidade,mock(ChavesIntegracaoFirma.class)).doFilter(pedido,resposta,cadeia);
    verify(identidade).autenticar(pedido);
    verify(cadeia).doFilter(pedido,resposta);
  }
  @Test void somentePostDosTresFluxosDeEntradaEPublico() throws Exception {
    var identidade=mock(IdentidadeAssineai.class);
    var filtro=new FiltroAcessoFirma(identidade,mock(ChavesIntegracaoFirma.class));
    for(String caminho:new String[]{"login","recuperar-senha","redefinir-senha"}) {
      assertTrue(filtro.shouldNotFilter(new MockHttpServletRequest("POST","/api/firma/"+caminho)));
      assertFalse(filtro.shouldNotFilter(new MockHttpServletRequest("GET","/api/firma/"+caminho)));
    }
    assertFalse(filtro.shouldNotFilter(new MockHttpServletRequest("GET","/api/firma/firebase/config")));
  }
  @Test void apiDeIntegracaoValidaChavePropriaSemConfundirComJwt() throws Exception {
    var identidade=mock(IdentidadeAssineai.class);var chaves=mock(ChavesIntegracaoFirma.class);
    var pedido=new MockHttpServletRequest("GET","/api/firma/v1/canais");
    var resposta=new MockHttpServletResponse();var cadeia=mock(FilterChain.class);
    new FiltroAcessoFirma(identidade,chaves).doFilter(pedido,resposta,cadeia);
    verify(chaves).autenticar(pedido);verifyNoInteractions(identidade);verify(cadeia).doFilter(pedido,resposta);
  }
  @Test void chaveRevogadaNaoChegaAoControladorDeIntegracao() throws Exception {
    var identidade=mock(IdentidadeAssineai.class);var chaves=mock(ChavesIntegracaoFirma.class);
    var pedido=new MockHttpServletRequest("GET","/api/firma/v1/canais");
    var resposta=new MockHttpServletResponse();var cadeia=mock(FilterChain.class);
    when(chaves.autenticar(pedido)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    new FiltroAcessoFirma(identidade,chaves).doFilter(pedido,resposta,cadeia);
    assertEquals(401,resposta.getStatus());verifyNoInteractions(cadeia,identidade);
  }

}
