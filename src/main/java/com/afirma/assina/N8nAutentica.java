package com.afirma.assina;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/n8n")
public class N8nAutentica {
 private final ApiAutenticaV1 api;private final ServicoAutentica dados;private final ServicoBots bots;private final AutenticaOperacoes operacoes;
 public N8nAutentica(ApiAutenticaV1 api,ServicoAutentica dados,ServicoBots bots,AutenticaOperacoes operacoes){this.api=api;this.dados=dados;this.bots=bots;this.operacoes=operacoes;}
 private UUID sessao(Map<String,Object> p,ServicoAutentica.Contexto c){if(p.get("ChatbotMetadataId") instanceof Number n){dados.registro("ChatbotMetadata",n.longValue(),c);return operacoes.uuid("ChatbotMetadata",n.longValue());}String id=Objects.toString(p.getOrDefault("ChatbotMetadataId",p.get("sessao")),"");return UUID.fromString(id);}
 @PostMapping("/mensagem/enviar")public Object mensagem(@RequestBody Map<String,Object> p,HttpServletRequest r){var c=api.acesso(r);if(p.get("ChatbotMetadataId")==null)return api.mensagem(p,r);var m=p.get("Mensagem") instanceof Map<?,?> mapa?mapa:Map.of();bots.enviarMensagemExterna(operacoes.executor(c),sessao(p,c),Objects.toString(m.get("MensagemTexto"),Objects.toString(m.get("Texto"),Objects.toString(p.get("texto"),""))));return Map.of("Sucesso",true);}
 @PostMapping("/chatbot/contexto")public void contexto(@RequestBody Map<String,Object> p,HttpServletRequest r){var c=api.acesso(r);bots.definirContinuacao(operacoes.executor(c),sessao(p,c),Objects.toString(p.get("URLWebhook"),""));}
 @PostMapping("/chatbot/finalizar")public void finalizar(@RequestBody Map<String,Object> p,HttpServletRequest r){var c=api.acesso(r);bots.finalizarExterno(operacoes.executor(c),sessao(p,c));}
 @PostMapping("/atendimento/insert")public Object atendimento(@RequestBody Map<String,Object> p,HttpServletRequest r){var c=api.acesso(r);Long setor=p.get("SetorId") instanceof Number n?n.longValue():null;if(setor!=null){dados.registro("Setor",setor,c);setor=operacoes.legado("Setor",setor);}return Map.of("AtendimentoId",bots.gerarAtendimentoExterno(operacoes.executor(c),sessao(p,c),setor));}
}
