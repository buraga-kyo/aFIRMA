package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class NavegacaoFirma {
  private final ServicoFirma servico;

  public NavegacaoFirma(ServicoFirma servico) {
    this.servico = servico;
  }

  @ModelAttribute("menuFirma")
  public List<Map<String, Object>> menu() {
    var itens = new ArrayList<Map<String, Object>>();
    itens.add(Map.of("titulo", "Módulos da FIRMA", "grupo", "aFIRMA", "href", "/", "icone", "↖"));
    for (var registro : servico.catalogo()) {
      var dados = new LinkedHashMap<String, Object>();
      dados.put("titulo", registro.get("titulo"));
      dados.put("grupo", registro.get("grupo"));
      dados.put(
          "href",
          registro.get("slug").equals("atendimentos")
              ? "/chat"
              : "/sistema?modulo=" + registro.get("slug"));
      dados.put(
          "icone",
          switch (registro.get("grupo").toString()) {
            case "Operação" -> "◫";
            case "Cadastros" -> "▦";
            case "Comunicação" -> "↗";
            case "Automação" -> "◇";
            case "Conta" -> "◉";
            default -> "⚙";
          });
      itens.add(dados);
    }
    return itens;
  }

  @ModelAttribute("rotaFirma")
  public String rota(HttpServletRequest requisicao) {
    return requisicao.getRequestURI()
        + (requisicao.getQueryString() == null ? "" : "?" + requisicao.getQueryString());
  }
}
