package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperacaoAutentica {
  private final ServicoAutentica dados;
  private final BancoAssineai banco;
  private final EspelhoAutentica espelho;
  private final ChatTransporte transporte;
  public OperacaoAutentica(ServicoAutentica dados,BancoAssineai banco,EspelhoAutentica espelho,ChatTransporte transporte){this.dados=dados;this.banco=banco;this.espelho=espelho;this.transporte=transporte;}
  static long numero(Map<String,Object> m,String chave){if(!(m.get(chave) instanceof Number n)||n.longValue()<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione "+chave);return n.longValue();}
  private Map<String,Object> bloquear(long id,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");dados.registro("Atendimento",id,c);return banco.um("SELECT * FROM autentica.\"Atendimento\" WHERE id=:id FOR UPDATE",Map.of("id",id));}
  public Map<String,Object> conversa(long id,ServicoAutentica.Contexto c){
    dados.autorizar(c,"atendimentos","read");var a=new LinkedHashMap<>(dados.registro("Atendimento",id,c));
    a.put("contato",dados.registro("Contato",((Number)a.get("ContatoId")).longValue(),c));
    var canal=new LinkedHashMap<>(dados.registro("Canal",((Number)a.get("CanalId")).longValue(),c));canal.remove("CanalValorCredsWhatsApp");a.put("canal",canal);
    a.put("mensagens",mensagens(id,c,0));
    a.put("acoes",banco.listar("SELECT * FROM autentica.\"AtendimentoAcao\" WHERE \"AtendimentoId\"=:id ORDER BY id DESC LIMIT 100",Map.of("id",id)));
    a.put("tags",banco.listar("SELECT tag.* FROM autentica.\"AtendimentoTag\" atag JOIN autentica.\"Tag\" tag ON tag.id=atag.\"TagId\" WHERE atag.\"AtendimentoId\"=:id",Map.of("id",id)));
    return a;
  }
  public List<Map<String,Object>> mensagens(long id,ServicoAutentica.Contexto c,long antes){
    dados.autorizar(c,"atendimentos","read");dados.registro("Atendimento",id,c);
    return banco.listar("SELECT id,\"MensagemTexto\",\"MensagemTipo\",\"MensagemFromMe\",\"MensagemACK\",\"MensagemDataHoraWhatsApp\",\"MensagemDeletada\",\"MensagemEditada\",\"MensagemRespostaId\",\"MensagemReacao\",\"MensagemInterativa\",\"ArquivoId\",\"createdAt\" FROM autentica.\"Mensagem\" WHERE \"AtendimentoId\"=:id AND (:antes=0 OR (COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\"),id)<(SELECT COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\"),id FROM autentica.\"Mensagem\" WHERE id=:antes AND \"AtendimentoId\"=:id)) ORDER BY COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\") DESC,id DESC LIMIT 100",Map.of("id",id,"antes",antes));
  }
  @Transactional
  public Map<String,Object> nova(Map<String,Object> pedido,ServicoAutentica.Contexto c){
    dados.autorizar(c,"atendimentos","read");long canalId=numero(pedido,"CanalId"),contatoId=numero(pedido,"ContatoId");var canal=dados.registro("Canal",canalId,c);dados.registro("Contato",contatoId,c);
    if(Boolean.FALSE.equals(canal.get("CanalStatusUso")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Canal desativado");
    Long setor=pedido.get("SetorId") instanceof Number n?n.longValue():null;if(setor!=null)conferirSetor(setor,canalId,c);
    banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","atendimento:"+canalId+":"+contatoId));
    var p=new HashMap<String,Object>(Map.of("cliente",c.cliente(),"canal",canalId,"contato",contatoId));p.put("setor",setor);
    var existente=banco.um("SELECT * FROM autentica.\"Atendimento\" WHERE \"ClienteId\"=:cliente AND \"CanalId\"=:canal AND \"ContatoId\"=:contato AND \"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY id DESC LIMIT 1",p);if(existente!=null){dados.registro("Atendimento",((Number)existente.get("id")).longValue(),c);return existente;}
    var a=banco.um("INSERT INTO autentica.\"Atendimento\"(\"ClienteId\",\"CanalId\",\"ContatoId\",\"SetorId\",\"AtendimentoStatus\",\"AtendimentoProtocolo\",\"AtendimentoReceptivo\",\"AtendimentoQtdMensagensNaoLidas\",\"createdAt\",\"updatedAt\") VALUES(:cliente,:canal,:contato,:setor,'pendente',left(replace(gen_random_uuid()::text,'-',''),16),false,0,now(),now()) RETURNING *",p);
    registrar(((Number)a.get("id")).longValue(),"Iniciar","Atendimento iniciado");espelho.salvar("Atendimento",a,c);return a;
  }
  private void conferirSetor(long id,long canal,ServicoAutentica.Contexto c){var s=dados.registro("Setor",id,c);if(Boolean.FALSE.equals(s.get("SetorStatus"))||s.get("CanalId")!=null&&((Number)s.get("CanalId")).longValue()!=canal)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"O setor está inativo ou pertence a outro canal");}
  private void conferirAtendente(long id,Object setor,ServicoAutentica.Contexto c){var a=dados.registro("Atendente",id,c);if(Boolean.FALSE.equals(a.get("AtendenteStatus")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Atendente desativado");if(setor!=null&&banco.um("SELECT id FROM autentica.\"AtendenteSetor\" WHERE \"AtendenteId\"=:a AND \"SetorId\"=:s",Map.of("a",id,"s",setor))==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Atendente não está vinculado ao setor");}
  private void registrar(long id,String tipo,String texto){banco.executar("INSERT INTO autentica.\"AtendimentoAcao\"(\"AtendimentoId\",\"AtendimentoAcaoTipo\",\"AtendimentoAcaoTexto\",\"createdAt\",\"updatedAt\") VALUES(:id,:tipo,:texto,now(),now())",Map.of("id",id,"tipo",tipo,"texto",texto));}
  @Transactional
  public Map<String,Object> acao(long id,Map<String,Object> pedido,ServicoAutentica.Contexto c,String identidade){
    var a=bloquear(id,c);String acao=Objects.toString(pedido.get("acao"),"");var p=new HashMap<String,Object>(Map.of("id",id));
    boolean finalizado="finalizado".equals(a.get("AtendimentoStatus"));
    if(finalizado&&!Set.of("reativar","avaliar","lidas").contains(acao))throw new ResponseStatusException(HttpStatus.CONFLICT,"O atendimento está finalizado");
    switch(acao){
      case "assumir"->{
        long atendente=numero(pedido,"AtendenteId");conferirAtendente(atendente,a.get("SetorId"),c);
        if(a.get("AtendenteId")!=null&&((Number)a.get("AtendenteId")).longValue()!=atendente)throw new ResponseStatusException(HttpStatus.CONFLICT,"O atendimento já foi assumido; use a transferência");
        p.put("a",atendente);registrar(id,"Assumir",identidade+" assumiu o atendimento");
        banco.executar("UPDATE autentica.\"Atendimento\" SET \"AtendenteId\"=:a,\"AtendimentoStatus\"='emandamento',\"AtendimentoBOTExterno\"=false,\"updatedAt\"=now() WHERE id=:id",p);cancelarBot(a);
      }
      case "transferir"->{
        long setor=numero(pedido,"SetorId");conferirSetor(setor,((Number)a.get("CanalId")).longValue(),c);Long atendente=pedido.get("AtendenteId") instanceof Number n&&n.longValue()>0?n.longValue():null;if(atendente!=null)conferirAtendente(atendente,setor,c);
        p.put("s",setor);p.put("a",atendente);p.put("status",atendente==null?"pendente":"emandamento");registrar(id,"Transferir",identidade+" transferiu o atendimento");
        banco.executar("UPDATE autentica.\"Atendimento\" SET \"SetorId\"=:s,\"AtendenteId\"=:a,\"AtendimentoStatus\"=:status,\"AtendimentoQtdMensagensNaoLidas\"=1,\"updatedAt\"=now() WHERE id=:id",p);
      }
      case "finalizar"->{
        var canal=dados.registro("Canal",((Number)a.get("CanalId")).longValue(),c);if(!"API".equals(identidade))enfileirarAviso(a,Objects.toString(canal.get("CanalMsgEncerramentoAtendimento"),""));
        registrar(id,"Finalizar",identidade+" finalizou o atendimento");banco.executar("UPDATE autentica.\"Atendimento\" SET \"AtendimentoStatus\"='finalizado',\"AtendimentoDataFinalizado\"=now(),\"AtendimentoContextoIA\"=NULL,\"AtendimentoQtdMensagensNaoLidas\"=0,\"updatedAt\"=now() WHERE id=:id",p);cancelarBot(a);
      }
      case "reativar"->{
        if(!finalizado)throw new ResponseStatusException(HttpStatus.CONFLICT,"O atendimento já está aberto");
        banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","atendimento:"+a.get("CanalId")+":"+a.get("ContatoId")));
        if(banco.um("SELECT id FROM autentica.\"Atendimento\" WHERE \"CanalId\"=:canal AND \"ContatoId\"=:contato AND \"AtendimentoStatus\" IN ('pendente','emandamento')",Map.of("canal",a.get("CanalId"),"contato",a.get("ContatoId")))!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"O contato já possui atendimento aberto neste canal");
        registrar(id,"Iniciar",identidade+" reativou o atendimento");banco.executar("UPDATE autentica.\"Atendimento\" SET \"AtendimentoStatus\"='pendente',\"AtendimentoDataFinalizado\"=NULL,\"AtendenteId\"=NULL,\"AtendimentoQtdMensagensNaoLidas\"=1,\"updatedAt\"=now() WHERE id=:id",p);
      }
      case "lidas"->banco.executar("UPDATE autentica.\"Atendimento\" SET \"AtendimentoQtdMensagensNaoLidas\"=0 WHERE id=:id",p);
      case "avaliar"->{long nota=numero(pedido,"nota");RegrasAutentica.exigir(nota<=5,"A avaliação deve ser de 1 a 5");p.put("nota",nota);banco.executar("UPDATE autentica.\"Atendimento\" SET \"AtendimentoAvaliacao\"=:nota WHERE id=:id",p);}
      default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ação inválida");
    }
    var resultado=dados.registro("Atendimento",id,c);espelho.salvar("Atendimento",resultado,c);return resultado;
  }
  private void cancelarBot(Map<String,Object> a){Object canal=espelho.id("Canal",a.get("CanalId")),contato=espelho.id("Contato",a.get("ContatoId"));if(canal!=null&&contato!=null)banco.executar("UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=now() WHERE canal_id=CAST(:canal AS uuid) AND contato_id=:contato AND estado='ATIVA'",Map.of("canal",canal.toString(),"contato",Long.parseLong(contato.toString())));}
  void enfileirarAviso(Map<String,Object> a,String texto){if(texto.isBlank())return;var contato=banco.um("SELECT \"ContatoNumero\" FROM autentica.\"Contato\" WHERE id=:id",Map.of("id",a.get("ContatoId")));banco.executar("INSERT INTO firma_autentica_envio(cliente_id,atendimento_id,canal_id,destinatario,texto) VALUES(:c,:a,:ca,:d,:t)",Map.of("c",a.get("ClienteId"),"a",a.get("id"),"ca",a.get("CanalId"),"d",contato.get("ContatoNumero"),"t",texto));}
  @Transactional
  public List<Map<String,Object>> tags(long id,List<Long> ids,ServicoAutentica.Contexto c){var a=bloquear(id,c);RegrasAutentica.exigir(ids.size()<=100,"Muitas tags");for(long tag:ids){var t=dados.registro("Tag",tag,c);RegrasAutentica.exigir(!Boolean.FALSE.equals(t.get("TagStatus"))&&(t.get("SetorId")==null||Objects.equals(t.get("SetorId"),a.get("SetorId"))),"Tag inativa ou de outro setor");}banco.executar("DELETE FROM autentica.\"AtendimentoTag\" WHERE \"AtendimentoId\"=:id",Map.of("id",id));for(long tag:new LinkedHashSet<>(ids))banco.executar("INSERT INTO autentica.\"AtendimentoTag\"(\"AtendimentoId\",\"TagId\",\"createdAt\",\"updatedAt\") VALUES(:a,:t,now(),now())",Map.of("a",id,"t",tag));return conversa(id,c).get("tags") instanceof List<?> l?(List<Map<String,Object>>)(List<?>)l:List.of();}
  public List<Map<String,Object>> historico(long id,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");var a=dados.registro("Atendimento",id,c);return banco.listar("SELECT id,\"AtendimentoProtocolo\",\"AtendimentoStatus\",\"createdAt\",\"AtendimentoDataFinalizado\" FROM autentica.\"Atendimento\" WHERE \"ContatoId\"=:id AND \"ClienteId\"=:c ORDER BY id DESC LIMIT 100",Map.of("id",a.get("ContatoId"),"c",c.cliente()));}
  @Transactional
  public Map<String,Object> enviar(long id,String texto,ServicoAutentica.Contexto c,String identidade){return enviar(id,Map.of("texto",texto),c,identidade);}
  @Transactional
  public Map<String,Object> enviar(long id,Map<String,Object> pedido,ServicoAutentica.Contexto c,String identidade){
    String texto=Objects.toString(pedido.get("texto"),"");RegrasAutentica.exigir(!texto.isBlank()&&texto.length()<=4096,"Escreva uma mensagem de até 4.096 caracteres");var a=bloquear(id,c);
    if("finalizado".equals(a.get("AtendimentoStatus")))throw new ResponseStatusException(HttpStatus.CONFLICT,"O atendimento está finalizado");
    var canal=dados.registro("Canal",((Number)a.get("CanalId")).longValue(),c);if(Boolean.FALSE.equals(canal.get("CanalStatusUso")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Canal desativado");var contato=dados.registro("Contato",((Number)a.get("ContatoId")).longValue(),c);
    Long resposta=pedido.get("MensagemRespostaId") instanceof Number n?n.longValue():null;String externoResposta=null;
    if(resposta!=null){var m=dados.registro("Mensagem",resposta,c);RegrasAutentica.exigir(Objects.equals(m.get("AtendimentoId"),a.get("id")),"A mensagem respondida pertence a outro atendimento");externoResposta=Objects.toString(m.get("MensagemIdWhatsApp"),null);}
    String retorno=transporte.enviar(UUID.fromString(canal.get("CanalGUID").toString()),Objects.toString(contato.get("ContatoNumero"),""),texto,externoResposta);
    var p=new HashMap<String,Object>();p.put("id",id);p.put("canal",canal.get("id"));p.put("texto",texto);p.put("retorno",retorno);p.put("atendente",a.get("AtendenteId"));p.put("resposta",resposta);
    var m=banco.um("INSERT INTO autentica.\"Mensagem\"(\"AtendimentoId\",\"CanalId\",\"AtendenteId\",\"MensagemTexto\",\"MensagemTipo\",\"MensagemFromMe\",\"MensagemEnviadaPeloAtendente\",\"MensagemIdWhatsApp\",\"MensagemRespostaId\",\"MensagemDataHoraWhatsApp\",\"createdAt\",\"updatedAt\") VALUES(:id,:canal,:atendente,:texto,'Texto',true,true,:retorno,:resposta,now(),now(),now()) ON CONFLICT (\"MensagemIdWhatsApp\") DO UPDATE SET \"AtendimentoId\"=EXCLUDED.\"AtendimentoId\",\"MensagemEnviadaPeloAtendente\"=true,\"MensagemEnviadaForaZapio\"=false RETURNING *",p);
    espelho.salvar("Mensagem",m,c);banco.executar("UPDATE autentica.\"Atendimento\" SET \"MensagemId\"=:m,\"updatedAt\"=now() WHERE id=:id",Map.of("m",m.get("id"),"id",id));return Map.of("id",m.get("id"),"enviada",true);
  }
}
