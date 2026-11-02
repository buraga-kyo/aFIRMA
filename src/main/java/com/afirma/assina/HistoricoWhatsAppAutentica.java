package com.afirma.assina;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Importa lotes históricos confirmados pelo WhatsApp sem executar automações de entrada. */
@Service
public class HistoricoWhatsAppAutentica {
 private final BancoAssineai banco;private final AutenticaPersistencia persistencia;private final EspelhoAutentica espelho;private final ObjectMapper json;
 public HistoricoWhatsAppAutentica(BancoAssineai banco,AutenticaPersistencia persistencia,EspelhoAutentica espelho,ObjectMapper json){this.banco=banco;this.persistencia=persistencia;this.espelho=espelho;this.json=json;}
 public Map<String,Object> resumo(long canal){return banco.um("SELECT (SELECT count(*) FROM firma_autentica_historico_conversa WHERE canal_id=:c) conversas,COALESCE(sum(mensagens),0) mensagens,COALESCE(sum(duplicadas),0) duplicadas,COALESCE(sum(ignoradas),0) ignoradas,max(recebida_em) atualizada_em FROM firma_autentica_historico_lote WHERE canal_id=:c",Map.of("c",canal));}
 @Transactional public void receber(UUID guid,JsonNode lote){
  RegrasAutentica.exigir(lote!=null&&lote.isObject()&&lote.path("mensagens").isArray()&&lote.path("mensagens").size()<=50&&lote.path("conversas").isArray()&&lote.path("conversas").size()<=100,"Lote de histórico inválido");
  UUID id;try{id=UUID.fromString(lote.path("lote").asText());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lote inválido");}
  var canal=banco.um("SELECT * FROM autentica.\"Canal\" WHERE \"CanalGUID\"=:g AND \"CanalStatusUso\" AND \"CanalProvedorAPI\"='Baileys'",Map.of("g",guid));
  if(canal==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Canal indisponível");long canalId=((Number)canal.get("id")).longValue(),cliente=((Number)canal.get("ClienteId")).longValue();
  banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","historico:"+canalId));var params=Map.of("c",canalId,"l",id);
  if(banco.um("SELECT lote FROM firma_autentica_historico_lote WHERE canal_id=:c AND lote=:l",params)!=null)return;
  banco.um("SELECT set_config('firma.importando_historico','true',true)",Map.of());
  var contexto=new ServicoAutentica.Contexto(0,cliente,0,0);var atendimentos=new HashMap<String,Map<String,Object>>();int novas=0,duplicadas=0,ignoradas=Math.max(0,Math.min(100000,lote.path("ignoradas").asInt(0)));
  for(var conversa:lote.path("conversas")){String telefone=conversa.path("telefone").asText();if(!telefone.matches("[0-9]{8,15}")){ignoradas++;continue;}atendimentos.put(telefone,conversa(canal,telefone,conversa.path("nome").asText(""),contexto));}
  var afetados=new HashSet<Long>();
  for(var m:lote.path("mensagens")){
   String telefone=m.path("telefone").asText(),externo=m.path("id").asText();long timestamp=m.path("timestamp").asLong();
   if(!telefone.matches("[0-9]{8,15}")||externo.isBlank()||externo.length()>100||timestamp<946684800L||timestamp>Instant.now().plusSeconds(86400).getEpochSecond()||m.path("texto").asText().length()>10000){ignoradas++;continue;}
   var existe=banco.um("SELECT id,\"CanalId\" FROM autentica.\"Mensagem\" WHERE \"MensagemIdWhatsApp\"=:id",Map.of("id",externo));if(existe!=null){duplicadas++;continue;}
   var a=atendimentos.computeIfAbsent(telefone,n->conversa(canal,n,m.path("nome").asText(""),contexto));long atendimento=((Number)a.get("id")).longValue();
   var v=new LinkedHashMap<String,Object>();v.put("CanalId",canalId);v.put("AtendimentoId",atendimento);v.put("MensagemIdWhatsApp",externo);v.put("MensagemTexto",m.path("texto").asText("[mensagem sem texto]"));v.put("MensagemTipo",m.path("tipo").asText("Texto"));v.put("MensagemFromMe",m.path("fromMe").asBoolean());v.put("MensagemACK",m.path("ack").asInt(2));v.put("MensagemEnviadaForaZapio",m.path("fromMe").asBoolean());v.put("MensagemDataHoraWhatsApp",Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC));v.put("MensagemRAW",json.writeValueAsString(Map.of("historicoImportado",true,"tipo",m.path("tipo").asText("Texto"))));
   if(m.path("key").isObject())v.put("MensagemKeyWhatsApp",json.writeValueAsString(m.path("key")));
   var nova=persistencia.inserirHistorico(v);if(nova==null){duplicadas++;continue;}espelho.salvar("Mensagem",nova,contexto);novas++;afetados.add(atendimento);
   // O espelho conserva a data de origem, inclusive para consumidores antigos.
   banco.executar("UPDATE chat_mensagem SET criado_em=:d WHERE id=CAST(:id AS bigint)",Map.of("id",espelho.id("Mensagem",nova.get("id")).toString(),"d",v.get("MensagemDataHoraWhatsApp")));
  }
  for(long atendimento:afetados){
   banco.executar("UPDATE autentica.\"Atendimento\" a SET \"MensagemId\"=(SELECT id FROM autentica.\"Mensagem\" WHERE \"AtendimentoId\"=a.id ORDER BY COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\") DESC,id DESC LIMIT 1),\"AtendimentoDataUltimaMensagemCliente\"=(SELECT max(COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\")) FROM autentica.\"Mensagem\" WHERE \"AtendimentoId\"=a.id AND NOT COALESCE(\"MensagemFromMe\",false)) WHERE a.id=:id",Map.of("id",atendimento));
  }
  var resultado=new HashMap<String,Object>(params);resultado.put("m",novas);resultado.put("d",duplicadas);resultado.put("i",ignoradas);
  banco.executar("INSERT INTO firma_autentica_historico_lote(canal_id,lote,mensagens,duplicadas,ignoradas) VALUES(:c,:l,:m,:d,:i)",resultado);
 }
 private Map<String,Object> conversa(Map<String,Object> canal,String numero,String nome,ServicoAutentica.Contexto c){
  long canalId=((Number)canal.get("id")).longValue();
  banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","Contato:"+c.cliente()+":"+numero));
  var contato=banco.um("SELECT * FROM autentica.\"Contato\" WHERE \"ClienteId\"=:c AND \"ContatoNumero\"=:n ORDER BY id LIMIT 1",Map.of("c",c.cliente(),"n",numero));
  if(contato==null){contato=persistencia.gravar("Contato",null,Map.of("ClienteId",c.cliente(),"ContatoNome",nome.isBlank()?numero:nome.substring(0,Math.min(70,nome.length())),"ContatoNumero",numero,"ContatoJidWhatsApp",numero+"@s.whatsapp.net"));espelho.salvar("Contato",contato,c);}
  long contatoId=((Number)contato.get("id")).longValue();var p=Map.of("c",canalId,"co",contatoId);
  var importada=banco.um("SELECT a.* FROM firma_autentica_historico_conversa h JOIN autentica.\"Atendimento\" a ON a.id=h.atendimento_id WHERE h.canal_id=:c AND h.contato_id=:co",p);if(importada!=null)return importada;
  banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","atendimento:"+canalId+":"+contatoId));
  var a=banco.um("SELECT * FROM autentica.\"Atendimento\" WHERE \"CanalId\"=:c AND \"ContatoId\"=:co AND \"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY id DESC LIMIT 1",p);
  if(a==null){var setor=banco.um("SELECT id FROM autentica.\"Setor\" WHERE \"SetorStatus\" AND \"CanalId\"=:canal ORDER BY id LIMIT 1",Map.of("c",c.cliente(),"canal",canalId));var v=new LinkedHashMap<String,Object>();v.put("ClienteId",c.cliente());v.put("CanalId",canalId);v.put("ContatoId",contatoId);v.put("SetorId",setor==null?null:setor.get("id"));v.put("AtendimentoStatus","pendente");v.put("AtendimentoProtocolo",UUID.randomUUID().toString().replace("-","").substring(0,16));v.put("AtendimentoQtdMensagensNaoLidas",0);v.put("AtendimentoBOTExterno",false);a=persistencia.gravar("Atendimento",null,v);espelho.salvar("Atendimento",a,c);}
  banco.executar("INSERT INTO firma_autentica_historico_conversa VALUES(:c,:co,:a)",Map.of("c",canalId,"co",contatoId,"a",a.get("id")));return a;
 }
}
