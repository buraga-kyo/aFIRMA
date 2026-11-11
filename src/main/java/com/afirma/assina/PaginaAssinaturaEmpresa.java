package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Resolve o padrão da empresa no servidor antes de gerar a folha de assinatura. */
@Service
public class PaginaAssinaturaEmpresa {
  private final IdentidadeAssineai identidade;

  public PaginaAssinaturaEmpresa(IdentidadeAssineai identidade) {
    this.identidade = identidade;
  }

  public Configuracao daRequisicao(HttpServletRequest requisicao) {
    return daEmpresa(identidade.empresa(identidade.autenticar(requisicao)));
  }

  public Configuracao doUsuarioAtual() {
    var acesso = SecurityContextHolder.getContext().getAuthentication();
    if (acesso == null || !(acesso.getPrincipal() instanceof Jwt jwt)) return null;
    return daEmpresa(identidade.empresa("jwt:" + jwt.getIssuer() + ":" + jwt.getSubject()));
  }

  public Configuracao daIdentidade(String criador) {
    if (criador == null || !(criador.startsWith("jwt:") || criador.startsWith("firma:") || criador.startsWith("empresa:"))) return null;
    return daEmpresa(identidade.empresa(criador));
  }

  private Configuracao daEmpresa(Map<String, Object> empresa) {
    return new Configuracao(Objects.toString(empresa.get("EmpresaNome"), "Sua empresa"),
        TemplatesEmpresa.ativo(empresa.get("EmpresaTemplates")),
        ((Number) empresa.get("EmpresaId")).longValue(), Boolean.TRUE.equals(empresa.get("EmpresaAssinaturaAutomatica")));
  }

  public record Configuracao(String nomeEmpresa, Map<String, Object> modelo, long empresaId, boolean assinaturaAutomatica) {
    public Configuracao(String nomeEmpresa, Map<String, Object> modelo) { this(nomeEmpresa, modelo, 0, false); }
  }
}
