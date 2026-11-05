package com.afirma.assina;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ControlePaginaFirma {
  private final ServicoFirma servico;

  public ControlePaginaFirma(ServicoFirma servico) {
    this.servico = servico;
  }

  @GetMapping({"/sistema", "/talkative/trator", "/firma-chat/trator"})
  public String cadastro(
      @RequestParam(defaultValue = "canais") String modulo,
      @RequestParam(defaultValue = "false") boolean incorporado,
      Model modeloPagina) {
    if (modulo.equals("indicadores")) return "relacionamento-indicadores";
    servico.modulo(modulo);
    modeloPagina.addAttribute("moduloFirma", modulo);
    modeloPagina.addAttribute("incorporado", incorporado);
    return "relacionamento";
  }
}
