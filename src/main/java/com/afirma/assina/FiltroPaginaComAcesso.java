package com.afirma.assina;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class FiltroPaginaComAcesso extends OncePerRequestFilter {
    private static final Set<String> PAGINAS = Set.of("/preparar-assinaturas", "/assinatura", "/assineai/pagina-assinatura", "/advai");
    private final ServicoAcessoNavegador acessos;

    public FiltroPaginaComAcesso(ServicoAcessoNavegador acessos) { this.acessos = acessos; }

    @Override protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return !PAGINAS.contains(requisicao.getServletPath());
    }

    @Override protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        resposta.setHeader("Cache-Control", "no-store");
        try { acessos.exigir(requisicao); }
        catch (ResponseStatusException erro) {
            resposta.sendRedirect("/assineai/trator?acesso=necessario");
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }
}
