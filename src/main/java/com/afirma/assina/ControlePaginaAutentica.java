package com.afirma.assina;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
public class ControlePaginaAutentica {
 private final ServicoAutentica servico;
 public ControlePaginaAutentica(ServicoAutentica servico){this.servico=servico;}
 @GetMapping({"/sistema","/talkative/trator","/zapiou/trator"}) public String cadastro(@RequestParam(defaultValue="canais")String modulo,@RequestParam(defaultValue="false") boolean incorporado,Model model){if(modulo.equals("indicadores"))return "autentica-indicadores";servico.modulo(modulo);model.addAttribute("moduloAutentica",modulo);model.addAttribute("incorporado",incorporado);return "autentica";}
}
