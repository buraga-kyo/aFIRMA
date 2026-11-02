package com.afirma.assina;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

/** Operações específicas da origem que não podem ser reduzidas a CRUD. */
@Service
public class AutenticaOperacoes {
 private final BancoAssineai banco;private final ServicoAutentica dados;private final EspelhoAutentica espelho;private final ChatTransporte transporte;private final ServicoCanalOficial oficial;private final ServicoBots bots;private final ServicoInteligenciaConversacional ia;private final ServicoArquivosChat arquivos;private final AutenticaPersistencia persistencia;private final TemplatesMetaAutentica templates;
 private final HistoricoWhatsAppAutentica historico;
 public AutenticaOperacoes(BancoAssineai banco,ServicoAutentica dados,EspelhoAutentica espelho,ChatTransporte transporte,ServicoCanalOficial oficial,ServicoBots bots,ServicoInteligenciaConversacional ia,ServicoArquivosChat arquivos,AutenticaPersistencia persistencia,TemplatesMetaAutentica templates,HistoricoWhatsAppAutentica historico){this.historico=historico;this.templates=templates;this.banco=banco;this.dados=dados;this.espelho=espelho;this.transporte=transporte;this.oficial=oficial;this.bots=bots;this.ia=ia;this.arquivos=arquivos;this.persistencia=persistencia;}
 long legado(String tabela,long id){Object v=espelho.id(tabela,id);if(v==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Registro sem vínculo operacional");return Long.parseLong(v.toString());}
 UUID uuid(String tabela,long id){Object v=espelho.id(tabela,id);if(v==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Registro sem vínculo operacional");return UUID.fromString(v.toString());}
 Authentication executor(ServicoAutentica.Contexto c){
  long empresa=legado("Cliente",c.cliente());
  // Credencial interna, nunca enviada ao navegador; só depois de validar contexto e operação.
  var token=Jwt.withTokenValue("execucao-interna").header("alg","none").subject("autentica:"+c.usuario()).claim("empresa_id",empresa).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(30)).build();
  return new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("SCOPE_chat.manage")));
 }
 public Object canal(long id,String acao,ServicoAutentica.Contexto c){dados.autorizar(c,"canais",Set.of("estado","conexao","historico-status").contains(acao)?"read":"update");var canal=dados.registro("Canal",id,c);RegrasAutentica.exigir(!Boolean.FALSE.equals(canal.get("CanalStatusUso")),"Canal desativado");UUID guid=UUID.fromString(canal.get("CanalGUID").toString());
  if(acao.equals("estado"))return Map.of("estado","Meta".equals(canal.get("CanalProvedorAPI"))?Objects.toString(canal.get("CanalStatus"),"OFFLINE"):transporte.estado(guid));
  RegrasAutentica.exigir(!"Meta".equals(canal.get("CanalProvedorAPI")),"O canal Meta usa as credenciais da empresa e os identificadores do canal");
  if(acao.equals("historico-status")){var resumo=new LinkedHashMap<String,Object>(historico.resumo(id));resumo.put("ponte",transporte.historico(guid,false));return resumo;}
  if(acao.equals("importar"))return transporte.historico(guid,true);
  if(acao.equals("conexao"))return transporte.conexao(guid);
  return switch(acao){case "conectar"->{transporte.conectar(guid);yield Map.of("estado","CONECTANDO");}case "desconectar"->{transporte.desconectar(guid);yield Map.of("estado","DESCONECTADO");}case "qr"->Map.of("qr",transporte.qr(guid));default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ação de canal inválida");};
 }
 public Object modelo(long id,String acao,ServicoAutentica.Contexto c){dados.autorizar(c,"templates","update");var m=dados.registro("MensagemTemplate",id,c);long canal=OperacaoAutentica.numero(m,"CanalId");dados.registro("Canal",canal,c);return templates.operar(id,acao,c.cliente());}
 public String sugerir(long atendimento,long agente,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");var a=dados.registro("Atendimento",atendimento,c);var config=dados.registro("InteligenciaArtificial",agente,c);RegrasAutentica.exigir((config.get("CanalId")==null||Objects.equals(config.get("CanalId"),a.get("CanalId")))&&(config.get("SetorId")==null||Objects.equals(config.get("SetorId"),a.get("SetorId"))),"A IA pertence a outro canal ou setor");return ia.responder(legado("Cliente",c.cliente()),legado("InteligenciaArtificial",agente),uuid("Atendimento",atendimento));}
 public Object sessoesBot(long bot,ServicoAutentica.Contexto c){dados.autorizar(c,"bots","read");dados.registro("ChatbotDefinicao",bot,c);return bots.sessoes(executor(c),legado("ChatbotDefinicao",bot));}
 public void cancelarBot(long bot,UUID sessao,ServicoAutentica.Contexto c){dados.autorizar(c,"bots","update");dados.registro("ChatbotDefinicao",bot,c);RegrasAutentica.exigir(banco.um("SELECT id FROM chat_bot_sessao WHERE id=:id AND definicao_id=:bot AND empresa_id=:e",Map.of("id",sessao,"bot",legado("ChatbotDefinicao",bot),"e",legado("Cliente",c.cliente())))!=null,"Sessão não encontrada neste bot");bots.cancelarSessao(executor(c),sessao);}
 public Object enviarArquivo(long id,MultipartFile arquivo,String legenda,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");var a=dados.registro("Atendimento",id,c);RegrasAutentica.exigir(!"finalizado".equals(a.get("AtendimentoStatus")),"Atendimento finalizado");return arquivos.enviar(uuid("Atendimento",id),arquivo,legenda,executor(c));}
 @Transactional public Object mensagem(long atendimento,long mensagem,String acao,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");var a=dados.registro("Atendimento",atendimento,c);var m=dados.registro("Mensagem",mensagem,c);RegrasAutentica.exigir(Objects.equals(m.get("AtendimentoId"),a.get("id")),"Mensagem de outro atendimento");RegrasAutentica.exigir(Set.of("lida","remover").contains(acao),"Ação inválida");if(acao.equals("remover"))RegrasAutentica.exigir(Boolean.TRUE.equals(m.get("MensagemFromMe")),"Somente mensagens enviadas podem ser apagadas");var contato=dados.registro("Contato",((Number)a.get("ContatoId")).longValue(),c);transporte.mensagem(uuid("Canal",((Number)a.get("CanalId")).longValue()),Objects.toString(contato.get("ContatoNumero"),""),Objects.toString(m.get("MensagemIdWhatsApp"),""),Boolean.TRUE.equals(m.get("MensagemFromMe")),acao);var r=persistencia.gravar("Mensagem",mensagem,acao.equals("remover")?Map.of("MensagemDeletada",true):Map.of("MensagemACK",3));espelho.salvar("Mensagem",r,c);return Map.of("sucesso",true);}
 @Transactional public void revogarSessao(long id,ServicoAutentica.Contexto c){dados.autorizar(c,"sessoes","read");if(!dados.pode(c,"all","manage")&&!dados.pode(c,"usuariodetail","update"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);dados.registro("Sessao",id,c);persistencia.gravar("Sessao",id,Map.of("SessaoStatus","F"));}
 @Transactional public Map<String,Object> remover(String slug,long id,ServicoAutentica.Contexto c){dados.autorizar(c,slug,"delete");String t=dados.modulo(slug).get("tabela").toString();RegrasAutentica.exigir(!Set.of("Cliente","Atendimento","DisparoMassa","Sessao").contains(t),"Use a operação específica deste módulo");var r=dados.registro(t,id,c);
  String status=switch(t){case "Canal"->"CanalStatusUso";case "Atendente","Setor","Tag","ListaContato","ChatbotDefinicao","InteligenciaArtificial","Integracao","ChaveAPI"->t+"Status";case "MensagemTemplate"->"MensagemTemplateStatusUso";case "Usuario"->"UsuarioBloqueado";default->null;};
  if(status!=null){var novo=persistencia.gravar(t,id,Map.of(status,t.equals("Usuario")));espelho.salvar(t,novo,c);
   banco.executar("INSERT INTO firma_autentica_exclusao(cliente_id,tabela,registro_id,usuario_id) VALUES(:c,:t,:id,:u) ON CONFLICT DO NOTHING",Map.of("c",c.cliente(),"t",t,"id",id,"u",c.usuario()));
   return Map.of("excluido",true,"id",id);
  }
  banco.um("SELECT set_config('firma.autentica_direcao','canonico',true)",Map.of());
  if(t.equals("AtendenteSetor"))banco.executar("DELETE FROM chat_atendente_setor WHERE atendente_id=:a AND setor_id=:s",Map.of("a",legado("Atendente",((Number)r.get("AtendenteId")).longValue()),"s",legado("Setor",((Number)r.get("SetorId")).longValue())));
  if(t.equals("Webhook")){Object antigo=espelho.id(t,id);if(antigo!=null)banco.executar("UPDATE chat_webhook SET ativo=false WHERE id=CAST(:id AS uuid)",Map.of("id",antigo.toString()));}
  if(t.equals("MensagemRapida")){Object antigo=espelho.id(t,id);if(antigo!=null)banco.executar("UPDATE chat_mensagem_rapida SET ativa=false WHERE id=:id",Map.of("id",Long.parseLong(antigo.toString())));}
  String schema=Set.of("Perfil","Permissao","PerfilPermissao","UsuarioPermissao").contains(t)?"autentica_identidade":"autentica";
  banco.executar("DELETE FROM "+schema+".\""+t+"\" WHERE id=:id",Map.of("id",id));banco.executar("DELETE FROM firma_autentica_vinculo WHERE destino=:t AND destino_id=:id",Map.of("t",t,"id",id));
  return Map.of("excluido",true,"id",id);
 }
}
