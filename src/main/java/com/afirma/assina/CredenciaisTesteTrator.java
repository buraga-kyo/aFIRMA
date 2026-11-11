package com.afirma.assina;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {ControlePaginaAssinatura.class, ControlePaginaFirma.class, ControlePaginaChat.class})
public class CredenciaisTesteTrator {
    private final ConfiguracaoAcessoCompartilhado acesso;
    public CredenciaisTesteTrator(ConfiguracaoAcessoCompartilhado acesso) { this.acesso = acesso; }
    @ModelAttribute public void preparar(Model modeloPagina) {
        modeloPagina.addAttribute("tokenTeste", acesso.testeLocal() ? acesso.emitirTokenTeste() : "");
        modeloPagina.addAttribute("chaveTeste", acesso.testeLocal() ? ConfiguracaoAcessoCompartilhado.CHAVE_TESTE : "");
    }
}
