package com.afirma.assina;
import org.springframework.core.env.Environment;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes={ControlePaginaAssinatura.class,ControlePaginaFirma.class,ControlePaginaChat.class})
public class CredenciaisTesteTrator {
	private static final String TOKEN_TESTE = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIn0.n1fXvSU8qCLktT4SwVGcerEsfzXi8e7KVvTaT4XRSDU";
	private static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";

 private final Environment ambiente;
 public CredenciaisTesteTrator(Environment ambiente){this.ambiente=ambiente;}
 @ModelAttribute public void preparar(Model modeloPagina){
  modeloPagina.addAttribute("tokenTeste",ambiente.matchesProfiles("local-test")?TOKEN_TESTE:"");
  modeloPagina.addAttribute("chaveTeste",ambiente.matchesProfiles("local-test")?CHAVE_TESTE:"");
 }
}
