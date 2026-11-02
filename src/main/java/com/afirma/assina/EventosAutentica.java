package com.afirma.assina;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** ACK, protocolos de edição/exclusão, reações e mensagens enviadas por outro dispositivo. */
@Service
public class EventosAutentica {
 private final BancoAssineai banco;private final AutenticaPersistencia persistencia;private final ObjectMapper json;
 public EventosAutentica(BancoAssineai banco,AutenticaPersistencia persistencia,ObjectMapper json){this.banco=banco;this.persistencia=persistencia;this.json=json;}
 @Transactional public void atualizar(ChatControle.Evento e){RegrasAutentica.exigir(e.identificador()!=null&&!e.identificador().isBlank()&&e.identificador().length()<=100,"Mensagem inválida");var m=banco.um("SELECT m.* FROM autentica.\"Mensagem\" m JOIN autentica.\"Canal\" c ON c.id=m.\"CanalId\" WHERE c.\"CanalGUID\"=:c AND m.\"MensagemIdWhatsApp\"=:id FOR UPDATE OF m",Map.of("c",e.canalId(),"id",e.identificador()));if(m==null)return;var valores=new HashMap<String,Object>();
  if(e.ack()!=null){RegrasAutentica.exigir(e.ack()>=-1&&e.ack()<=4,"Confirmação inválida");int antiga=m.get("MensagemACK") instanceof Number n?n.intValue():-1;valores.put("MensagemACK",Math.max(antiga,e.ack()));}
  if(Boolean.TRUE.equals(e.deletada()))valores.put("MensagemDeletada",true);
  if(Boolean.TRUE.equals(e.editada())&&e.texto()!=null){RegrasAutentica.exigir(e.texto().length()<=10000,"Texto editado muito grande");valores.put("MensagemEditada",true);valores.put("MensagemTexto",e.texto());}
  if(e.reacao()!=null&&e.reacao().isObject()){var reacoes=new ArrayList<Object>();String numero=e.reacao().path("Numero").asText(),reacao=e.reacao().path("Reacao").asText();RegrasAutentica.exigir(numero.length()<=30&&reacao.length()<=100,"Reação inválida");if(m.get("MensagemReacao") instanceof List<?> l)for(var r:l)if(!(r instanceof Map<?,?> mapa)||!Objects.equals(mapa.get("Numero"),numero))reacoes.add(r);if(!reacao.isBlank())reacoes.add(Map.of("Numero",numero,"Reacao",reacao));valores.put("MensagemReacao",reacoes);}
  if(!valores.isEmpty())persistencia.gravar("Mensagem",((Number)m.get("id")).longValue(),valores);
  var p=new HashMap<String,Object>();p.put("canal",e.canalId());p.put("id",e.identificador());p.put("ack",valores.get("MensagemACK"));p.put("deletada",Boolean.TRUE.equals(e.deletada()));p.put("editada",Boolean.TRUE.equals(e.editada()));p.put("texto",e.texto());p.put("reacoes",json.writeValueAsString(valores.get("MensagemReacao")));
  banco.um("SELECT set_config('firma.autentica_direcao','canonico',true)",Map.of());banco.executar("UPDATE chat_mensagem SET confirmacao=COALESCE(:ack,confirmacao),excluida=excluida OR :deletada,editada=editada OR :editada,texto=CASE WHEN :editada THEN :texto ELSE texto END,reacoes=COALESCE(NULLIF(CAST(:reacoes AS jsonb),'null'::jsonb),reacoes) WHERE canal_id=:canal AND identificador_externo=:id",p);
 }
 @Transactional public void metadados(ChatControle.Evento e){var canal=banco.um("SELECT id,\"ClienteId\",\"CanalNumeroCelular\" FROM autentica.\"Canal\" WHERE \"CanalGUID\"=:c",Map.of("c",e.canalId()));if(canal==null)return;
  var m=banco.um("SELECT * FROM autentica.\"Mensagem\" WHERE \"CanalId\"=:c AND \"MensagemIdWhatsApp\"=:id",Map.of("c",canal.get("id"),"id",e.identificador()));var valores=new HashMap<String,Object>();
  if(m==null&&Boolean.TRUE.equals(e.fromMe())){var atendimento=banco.um("SELECT a.id FROM autentica.\"Atendimento\" a JOIN autentica.\"Contato\" co ON co.id=a.\"ContatoId\" WHERE a.\"CanalId\"=:c AND co.\"ContatoNumero\"=:n AND a.\"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY a.id DESC LIMIT 1",Map.of("c",canal.get("id"),"n",e.telefone()));valores.put("CanalId",canal.get("id"));valores.put("AtendimentoId",atendimento==null?null:atendimento.get("id"));valores.put("MensagemIdWhatsApp",e.identificador());valores.put("MensagemTexto",e.texto());valores.put("MensagemTipo","Texto");valores.put("MensagemFromMe",true);valores.put("MensagemEnviadaForaZapio",true);valores.put("MensagemDataHoraWhatsApp",java.time.OffsetDateTime.now());}
  if(e.raw()!=null){String raw=json.writeValueAsString(e.raw());RegrasAutentica.exigir(raw.length()<=200000,"Metadados muito grandes");valores.put("MensagemRAW",raw);if(e.raw().has("key"))valores.put("MensagemKeyWhatsApp",json.writeValueAsString(e.raw().path("key")));}
  if(e.respostaId()!=null){var resposta=banco.um("SELECT id FROM autentica.\"Mensagem\" WHERE \"CanalId\"=:c AND \"MensagemIdWhatsApp\"=:id",Map.of("c",canal.get("id"),"id",e.respostaId()));if(resposta!=null)valores.put("MensagemRespostaId",resposta.get("id"));}
  if((m!=null||Boolean.TRUE.equals(e.fromMe()))&&!valores.isEmpty())persistencia.gravar("Mensagem",m==null?null:((Number)m.get("id")).longValue(),valores);
 }
}
