package com.afirma.assina;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/firma")
public class ControlePlanoBuscaRelacionamento {
 private final IdentidadeAssineai identidade;
 private final ServicoFirma dados;
 private final ServicoPlanoBuscaAssineAi planos;
 public ControlePlanoBuscaRelacionamento(IdentidadeAssineai identidade,ServicoFirma dados,ServicoPlanoBuscaAssineAi planos){this.identidade=identidade;this.dados=dados;this.planos=planos;}
 @PostMapping("/plano-busca")
 public Object plano(@RequestBody Map<String,String> pedido,HttpServletRequest requisicao){
  var contexto=dados.contexto(identidade.autenticar(requisicao));dados.autorizar(contexto,"atendimentos","read");
  return planos.interpretarConversas(pedido.get("consulta"));
 }
}
