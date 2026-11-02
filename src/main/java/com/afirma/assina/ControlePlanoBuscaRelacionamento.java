package com.afirma.assina;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/autentica")
public class ControlePlanoBuscaRelacionamento {
 private final IdentidadeAssineai identidade;
 private final ServicoAutentica dados;
 private final ServicoPlanoBuscaAssineAi planos;
 public ControlePlanoBuscaRelacionamento(IdentidadeAssineai identidade,ServicoAutentica dados,ServicoPlanoBuscaAssineAi planos){this.identidade=identidade;this.dados=dados;this.planos=planos;}
 @PostMapping("/plano-busca")
 public Object plano(@RequestBody Map<String,String> pedido,HttpServletRequest request){
  var contexto=dados.contexto(identidade.autenticar(request));dados.autorizar(contexto,"atendimentos","read");
  return planos.interpretarConversas(pedido.get("consulta"));
 }
}
