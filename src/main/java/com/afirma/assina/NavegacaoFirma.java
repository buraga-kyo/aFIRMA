package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class NavegacaoFirma {
  private final ServicoAutentica servico;
  public NavegacaoFirma(ServicoAutentica servico){this.servico=servico;}
  @ModelAttribute("menuFirma")
  public List<Map<String,Object>> menu(){var itens=new ArrayList<Map<String,Object>>();itens.add(Map.of("titulo","Módulos da FIRMA","grupo","aFIRMA","href","/","icone","↖"));
    itens.add(Map.of("titulo","Indicadores","grupo","Operação","href","/sistema?modulo=indicadores","icone","▥"));
    for(var m:servico.catalogo()){var n=new LinkedHashMap<String,Object>();n.put("titulo",m.get("titulo"));n.put("grupo",m.get("grupo"));n.put("href",m.get("slug").equals("atendimentos")?"/chat":"/sistema?modulo="+m.get("slug"));n.put("icone",switch(m.get("grupo").toString()){case "Operação"->"◫";case "Cadastros"->"▦";case "Comunicação"->"↗";case "Automação"->"◇";case "Conta"->"◉";default->"⚙";});itens.add(n);}return itens;}
  @ModelAttribute("rotaFirma") public String rota(HttpServletRequest r){return r.getRequestURI()+(r.getQueryString()==null?"":"?"+r.getQueryString());}
}
