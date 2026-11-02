package com.afirma.assina;

import java.util.*;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Regras dos serviços Tabela* da origem, além do contrato estrutural Sequelize. */
@Component
public class RegrasAutentica {
  private final BancoAssineai banco;
  private final MotorFluxoBot motor;
  private final ObjectMapper json;
  public RegrasAutentica(BancoAssineai banco,MotorFluxoBot motor,ObjectMapper json){this.banco=banco;this.motor=motor;this.json=json;}
  static void exigir(boolean ok,String mensagem){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,mensagem);}
  static String texto(Map<String,Object> m,String n){return Objects.toString(m.get(n),"");}
  static long numero(Map<String,Object> m,String n){return m.get(n) instanceof Number v?v.longValue():0;}
  static void opcao(Map<String,Object> m,String nome,String... opcoes){if(m.get(nome)!=null)exigir(Set.of(opcoes).contains(m.get(nome)),"Valor inválido para "+nome);}
  static void url(String valor){try{var u=URI.create(valor);exigir("https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null,"Informe uma URL HTTPS válida");}catch(IllegalArgumentException e){exigir(false,"URL inválida");}}
  public void aplicar(String tabela,Long id,Map<String,Object> valores,Map<String,Object> anterior,ServicoAutentica.Contexto c){
    var r=new HashMap<>(anterior);r.putAll(valores);
    if(id==null){
      String status=switch(tabela){case "Canal"->"CanalStatusUso";case "MensagemTemplate"->"MensagemTemplateStatusUso";default->tabela+"Status";};
      if(Set.of("Canal","Atendente","Setor","Tag","ListaContato","MensagemTemplate","ChatbotDefinicao","Integracao","InteligenciaArtificial","ChaveAPI").contains(tabela)&&!r.containsKey(status))valores.put(status,true);
    }
    switch(tabela){
      case "Contato"->{
        if(valores.containsKey("ContatoNumero")){String telefone=texto(r,"ContatoNumero").replaceAll("[^0-9]","");exigir(telefone.length()>=10&&telefone.length()<=15,"Telefone inválido");if(telefone.length()==10||telefone.length()==11)telefone="55"+telefone;valores.put("ContatoNumero",telefone);unico(tabela,"ContatoNumero",telefone,id,c);}
        if(id==null)exigir(!texto(r,"ContatoNome").isBlank()&&valores.containsKey("ContatoNumero"),"Informe nome e telefone do contato");
      }
      case "Canal"->{
        String telefone=texto(r,"CanalNumeroCelular").replaceAll("[^0-9]","");
        if(!telefone.isBlank()){
          exigir(telefone.length()>=10&&telefone.length()<=15,"Número do canal inválido");
          if(telefone.length()==10||telefone.length()==11)telefone="55"+telefone;
          if(valores.containsKey("CanalNumeroCelular"))valores.put("CanalNumeroCelular",telefone);
          banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","CanalNumero:"+c.cliente()+":"+telefone));
          exigir(banco.um("SELECT ca.id FROM autentica.\"Canal\" ca WHERE ca.\"ClienteId\"=:c AND ca.id<>:id AND CASE WHEN length(regexp_replace(COALESCE(ca.\"CanalNumeroCelular\",''),'[^0-9]','','g')) IN (10,11) THEN '55'||regexp_replace(ca.\"CanalNumeroCelular\",'[^0-9]','','g') ELSE regexp_replace(ca.\"CanalNumeroCelular\",'[^0-9]','','g') END=:n AND NOT EXISTS(SELECT 1 FROM firma_autentica_exclusao x WHERE x.cliente_id=:c AND x.tabela='Canal' AND x.registro_id=ca.id) LIMIT 1",Map.of("c",c.cliente(),"id",id==null?0:id,"n",telefone))==null,"Já existe um canal não excluído com este número");
        }
        exigir(!texto(r,"CanalNome").isBlank(),"Informe o nome do canal");
        if(id==null){valores.putIfAbsent("CanalProvedorAPI","Baileys");valores.putIfAbsent("CanalStatus","OFFLINE");}
        opcao(r,"CanalProvedorAPI","Baileys","WWebJS","Meta");
        if(Boolean.TRUE.equals(r.get("CanalFinalizaAtendimentoPorTempoInatividade")))exigir(numero(r,"CanalAtendimentoTempoInatividade")>0,"Informe o tempo de inatividade em minutos");
      }
      case "Setor"->exigir(!texto(r,"SetorNome").isBlank(),"Informe o nome do setor");
      case "Atendente"->{exigir(!texto(r,"AtendenteNome").isBlank(),"Informe o nome do atendente");if(id==null)valores.putIfAbsent("AtendenteStatusChat","DISPONIVEL");if(r.get("AtendenteAutenticaAIUsuarioId")!=null)unico(tabela,"AtendenteAutenticaAIUsuarioId",r.get("AtendenteAutenticaAIUsuarioId"),id,c);}
      case "AtendenteSetor"->unicoVinculo(tabela,"AtendenteId","SetorId",r,id,c);
      case "PerfilPermissao"->unicoVinculo(tabela,"PerfilId","PermissaoId",r,id,c);
      case "UsuarioPermissao"->unicoVinculo(tabela,"UsuarioId","PermissaoId",r,id,c);
      case "Tag"->exigir(!texto(r,"TagNome").isBlank(),"Informe o nome da tag");
      case "MensagemRapida"->exigir(!texto(r,"MensagemRapidaTexto").isBlank()||r.get("ArquivoId")!=null,"Informe o texto ou arquivo da resposta");
      case "Usuario"->{
        String email=texto(r,"UsuarioEmail").strip().toLowerCase(Locale.ROOT);exigir(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"E-mail inválido");valores.put("UsuarioEmail",email);unico(tabela,"UsuarioEmail",email,id,c);
        exigir(!texto(r,"UsuarioPrimeiroNome").isBlank(),"Informe o nome do usuário");
        if(id==null){exigir(valores.get("UsuarioSenha")!=null,"Defina uma senha para o usuário");valores.putIfAbsent("UsuarioBloqueado",false);}
      }
      case "ListaContato"->{exigir(!texto(r,"ListaContatoNome").isBlank(),"Informe o nome da lista");if(r.get("ListaContatoContatos") instanceof List<?> lista){exigir(lista.size()<=100000,"Lista muito grande");exigir(lista.stream().allMatch(v->v instanceof Map<?,?>),"Cada destinatário deve ser um objeto com suas colunas");}}
      case "MensagemTemplate"->{
        exigir(texto(r,"MensagemTemplateNome").matches("[a-z0-9_]{1,100}"),"Use letras minúsculas, números e sublinhado no nome do template");
        exigir(!texto(r,"MensagemTemplateCorpo").isBlank(),"Informe o corpo do template");opcao(r,"MensagemTemplateCategoria","AUTHENTICATION","MARKETING","UTILITY");
        if(id==null){valores.putIfAbsent("MensagemTemplateStatusMeta","RASCUNHO");valores.putIfAbsent("MensagemTemplateCategoria","UTILITY");}
      }
      case "ChatbotDefinicao"->{
        exigir(numero(r,"CanalId")>0&&!texto(r,"ChatbotDefinicaoNome").isBlank(),"Informe nome e canal do bot");
        String provedor=texto(r,"ChatbotDefinicaoProvedor");opcao(r,"ChatbotDefinicaoProvedor","zapio","n8n","INTERNO","N8N");
        if(provedor.equalsIgnoreCase("n8n"))url(texto(r,"ChatbotDefinicaoN8nURL"));
        else {Object fluxo=r.get("ChatbotDefinicaoFlow");exigir(fluxo instanceof Map<?,?>,"Informe o fluxo do bot");motor.validar(json.valueToTree(fluxo));var compilado=CompiladorBotAutentica.compilar((Map<String,Object>)fluxo);valores.put("ChatbotDefinicaoJSON",json.writeValueAsString(compilado.get("Definicao")));valores.put("ChatbotDefinicaoMetadata",json.writeValueAsString(compilado.get("Metadata")));}
        if(id==null){valores.putIfAbsent("ChatbotDefinicaoProvedor","zapio");valores.putIfAbsent("ChatbotDefinicaoFuncionamento",Map.of("Integral",true));}
      }
      case "InteligenciaArtificial"->{
        exigir(!texto(r,"InteligenciaArtificialNome").isBlank()&&!texto(r,"InteligenciaArtificialModelo").isBlank()&&!texto(r,"InteligenciaArtificialTextoPrompt").isBlank(),"Informe nome, modelo e instruções da IA");
        opcao(r,"InteligenciaArtificialProvedor","chat-gpt","deepseek","OPENAI","DEEPSEEK","ChatGPT","OpenAI","DeepSeek","ChatGPT_CompletionsAPI");opcao(r,"InteligenciaArtificialAtivador","manual-chat","todas-mensagens");
        if(id==null)valores.putIfAbsent("InteligenciaArtificialAtivador","manual-chat");
        if(!Boolean.FALSE.equals(r.get("InteligenciaArtificialStatus"))){
          banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k","ia:"+c.cliente()+":"+r.get("CanalId")+":"+r.get("SetorId")));
          var p=new HashMap<String,Object>();p.put("c",c.cliente());p.put("canal",r.get("CanalId"));p.put("setor",r.get("SetorId"));p.put("a",r.getOrDefault("InteligenciaArtificialAtivador","manual-chat"));p.put("id",id==null?0:id);
          exigir(banco.um("SELECT id FROM autentica.\"InteligenciaArtificial\" WHERE \"ClienteId\"=:c AND \"CanalId\" IS NOT DISTINCT FROM CAST(:canal AS integer) AND (\"SetorId\" IS NOT DISTINCT FROM CAST(:setor AS integer) OR \"SetorId\" IS NULL) AND \"InteligenciaArtificialAtivador\"=:a AND \"InteligenciaArtificialStatus\" AND id<>:id LIMIT 1",p)==null,"Já existe IA ativa neste canal, setor e ativador");
        }
      }
      case "Webhook"->{url(texto(r,"WebhookURL"));opcao(r,"WebhookEvento","mensagem_recebida","status_canal","chatbot_externo_assumido");exigir(!texto(r,"WebhookEvento").isBlank(),"Selecione o evento do webhook");if(id==null)valores.putIfAbsent("WebhookTodosCanais",r.get("CanalId")==null);exigir(Boolean.TRUE.equals(valores.getOrDefault("WebhookTodosCanais",r.get("WebhookTodosCanais")))||r.get("CanalId")!=null,"Selecione o canal do webhook");}
      case "Integracao"->{exigir(!texto(r,"IntegracaoNome").isBlank()&&(r.get("IntegracaoDados") instanceof Map<?,?>||r.get("IntegracaoDados") instanceof List<?>),"Informe o nome e os dados da integração");}
      case "ChaveAPI"->{exigir(!texto(r,"ChaveAPIDescricao").isBlank(),"Informe a descrição da chave");if(id==null){valores.put("ChaveAPIValor",UUID.randomUUID());valores.put("ChaveAPIResponsavelCriacao",c.usuario()==0?"Empresa":"Usuário "+c.usuario());}}
      default->{}
    }
  }
  private void unico(String t,String campo,Object valor,Long id,ServicoAutentica.Contexto c){
    banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k",t+":"+c.cliente()+":"+valor));
    String schema=t.equals("Usuario")?"autentica_identidade":"autentica",empresa=t.equals("Usuario")?"UsuarioClienteId":"ClienteId";
    var p=new HashMap<String,Object>(Map.of("v",valor,"c",c.cliente(),"id",id==null?0:id));
    exigir(banco.um("SELECT id FROM "+schema+".\""+t+"\" WHERE \""+campo+"\"=:v AND \""+empresa+"\"=:c AND id<>:id LIMIT 1",p)==null,"Já existe um registro com este valor de "+campo);
  }
  private void unicoVinculo(String t,String a,String b,Map<String,Object> r,Long id,ServicoAutentica.Contexto c){
    exigir(r.get(a) instanceof Number&&r.get(b) instanceof Number,"Selecione os dois registros do vínculo");
    banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",Map.of("k",t+":"+r.get(a)+":"+r.get(b)));
    String schema=t.equals("AtendenteSetor")?"autentica":"autentica_identidade";
    exigir(banco.um("SELECT id FROM "+schema+".\""+t+"\" WHERE \""+a+"\"=:a AND \""+b+"\"=:b AND id<>:id",Map.of("a",r.get(a),"b",r.get(b),"id",id==null?0:id))==null,"Este vínculo já existe");
  }
}
