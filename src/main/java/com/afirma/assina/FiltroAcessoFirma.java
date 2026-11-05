package com.afirma.assina;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/** Confere o token do usuário ou a chave da integração antes de deixar a chamada chegar à API. */
final class FiltroAcessoFirma extends OncePerRequestFilter {
  private static final Set<String> PUBLICAS =
      Set.of("/api/firma/login", "/api/firma/recuperar-senha", "/api/firma/redefinir-senha");
  private final IdentidadeAssineai identidade;
  private final ChavesIntegracaoFirma chaves;

  FiltroAcessoFirma(IdentidadeAssineai identidade, ChavesIntegracaoFirma chaves) {
    this.identidade = identidade;
    this.chaves = chaves;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest pedido) {
    String caminho = pedido.getRequestURI().substring(pedido.getContextPath().length());
    return !caminho.startsWith("/api/firma/")
        || "POST".equals(pedido.getMethod()) && PUBLICAS.contains(caminho);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    try {
      String caminho = pedido.getRequestURI().substring(pedido.getContextPath().length());
      if (caminho.startsWith("/api/firma/v1/")) chaves.autenticar(pedido);
      else identidade.autenticar(pedido);
    } catch (ResponseStatusException erro) {
      resposta.sendError(erro.getStatusCode().value(), "Acesso daFIRMA inválido ou sem permissão");
      return;
    }
    cadeia.doFilter(pedido, resposta);
  }
}
