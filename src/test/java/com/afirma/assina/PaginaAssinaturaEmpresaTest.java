package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PaginaAssinaturaEmpresaTest {
  @Test void consultaPadraoAtualDaEmpresaAutenticadaSemMisturarContas() {
    var identidade = mock(IdentidadeAssineai.class);
    var paginas = new PaginaAssinaturaEmpresa(identidade);
    var primeira = new MockHttpServletRequest();
    var segunda = new MockHttpServletRequest();
    var noir = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow();
    var linho = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("linho")).findFirst().orElseThrow();
    when(identidade.autenticar(primeira)).thenReturn("empresa:primeira");
    when(identidade.autenticar(segunda)).thenReturn("empresa:segunda");
    when(identidade.empresa("empresa:primeira")).thenReturn(Map.of("EmpresaNome", "Primeira", "EmpresaTemplates",
        Map.of("versao", 2, "ativo", noir.get("id"), "modelos", List.of(noir))));
    when(identidade.empresa("empresa:segunda")).thenReturn(Map.of("EmpresaNome", "Segunda", "EmpresaTemplates",
        Map.of("versao", 2, "ativo", linho.get("id"), "modelos", List.of(linho))));
    assertEquals("noir", paginas.daRequisicao(primeira).modelo().get("design"));
    assertEquals("linho", paginas.daRequisicao(segunda).modelo().get("design"));
    when(identidade.empresa("empresa:primeira")).thenReturn(Map.of("EmpresaNome", "Primeira", "EmpresaTemplates",
        Map.of("versao", 2, "ativo", linho.get("id"), "modelos", List.of(linho))));
    assertEquals("linho", paginas.daRequisicao(primeira).modelo().get("design"));
  }

  @Test void empresaSemPersonalizacaoUsaPadraoInicial() {
    var identidade = mock(IdentidadeAssineai.class);
    var pedido = new MockHttpServletRequest();
    when(identidade.autenticar(pedido)).thenReturn("empresa:nova");
    when(identidade.empresa("empresa:nova")).thenReturn(Map.of("EmpresaNome", "Nova"));
    assertEquals("classico", new PaginaAssinaturaEmpresa(identidade).daRequisicao(pedido).modelo().get("design"));
  }
  @Test void finalizacaoDeConvitesUsaEmpresaDoUsuarioAutenticado() {
    var identidade = mock(IdentidadeAssineai.class);
    var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("teste")
        .header("alg", "RS256").issuer("https://firma.test").subject("responsavel").build();
    var modelo = TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow();
    when(identidade.empresa("jwt:https://firma.test:responsavel")).thenReturn(Map.of("EmpresaNome", "Firma", "EmpresaTemplates",
        Map.of("versao", 2, "ativo", modelo.get("id"), "modelos", List.of(modelo))));
    try {
      org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
          new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
      assertEquals("noir", new PaginaAssinaturaEmpresa(identidade).doUsuarioAtual().modelo().get("design"));
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }
}
