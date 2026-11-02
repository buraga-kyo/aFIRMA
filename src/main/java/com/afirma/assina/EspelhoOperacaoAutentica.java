package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Adapta cadastros canônicos aos executores Java existentes; não duplica seus envios. */
@Component
public class EspelhoOperacaoAutentica {
  private final BancoAssineai banco;
  private final ObjectMapper json;
  public EspelhoOperacaoAutentica(BancoAssineai banco,ObjectMapper json){this.banco=banco;this.json=json;}
  private Object legado(String tabela,Object id){if(id==null)return null;var r=banco.um("SELECT origem_id FROM firma_autentica_vinculo WHERE destino=:t AND destino_id=:id",Map.of("t",tabela,"id",id));return r==null?null:r.get("origem_id");}
  private Long longo(String t,Object id){Object v=legado(t,id);return v==null?null:Long.valueOf(v.toString());}
  private UUID uuid(String t,Object id){Object v=legado(t,id);return v==null?null:UUID.fromString(v.toString());}
  private Object valor(Map<String,Object> r,String n,Object padrao){return r.get(n)==null?padrao:r.get(n);}
  public void salvar(String tabela,Map<String,Object> r,ServicoAutentica.Contexto c,long empresa){
    if(!Set.of("AtendenteSetor","Tag","MensagemRapida","ListaContato","MensagemTemplate","ChatbotDefinicao","InteligenciaArtificial","Integracao").contains(tabela))return;
    banco.um("SELECT set_config('firma.autentica_direcao','canonico',true)",Map.of());
    var v=new LinkedHashMap<String,Object>();v.put("empresa_id",empresa);var camposJson=new HashSet<String>();String origem;
    switch(tabela){
      case "AtendenteSetor"->{return;}
      case "Tag"->{origem="chat_etiqueta";v.put("nome",r.get("TagNome"));v.put("ativa",valor(r,"TagStatus",true));}
      case "MensagemRapida"->{origem="chat_mensagem_rapida";v.put("atalho","autentica-"+r.get("id"));v.put("titulo","Resposta "+r.get("id"));v.put("texto",r.get("MensagemRapidaTexto"));}
      case "ListaContato"->{origem="chat_lista_contatos";v.put("nome",r.get("ListaContatoNome"));v.put("ativa",valor(r,"ListaContatoStatus",true));}
      case "MensagemTemplate"->{
        origem="chat_modelo_mensagem";v.put("nome",r.get("MensagemTemplateNome"));v.put("corpo",r.get("MensagemTemplateCorpo"));v.put("rodape",r.get("MensagemTemplateRodape"));
        v.put("categoria",switch(Objects.toString(r.get("MensagemTemplateCategoria"),"UTILITY")){case "AUTHENTICATION"->"AUTENTICACAO";case "MARKETING"->"MARKETING";default->"UTILIDADE";});
        v.put("estado",switch(Objects.toString(r.get("MensagemTemplateStatusMeta"),"")){case "APPROVED"->"APROVADO";case "REJECTED"->"REJEITADO";case "PENDING"->"PENDENTE";default->"RASCUNHO";});
        v.put("identificador_meta",r.get("MensagemTemplateMetaId"));v.put("status_uso",valor(r,"MensagemTemplateStatusUso",true));v.put("metodo_envio",valor(r,"MensagemTemplateMetodoEnvio","OFICIAL"));v.put("cabecalho_tipo",r.get("MensagemTemplateCabecalhoTipo"));v.put("cabecalho_variaveis",valor(r,"MensagemTemplateCabecalhoVariaveis",List.of()));v.put("corpo_variaveis",valor(r,"MensagemTemplateCorpoVariaveis",List.of()));camposJson.addAll(Set.of("cabecalho_variaveis","corpo_variaveis"));v.put("botoes",valor(r,"MensagemTemplateBotoes",List.of()));camposJson.add("botoes");
      }
      case "ChatbotDefinicao"->{
        origem="chat_bot_definicao";v.put("nome",r.get("ChatbotDefinicaoNome"));v.put("canal_id",uuid("Canal",r.get("CanalId")));v.put("ativa",valor(r,"ChatbotDefinicaoStatus",false));
        v.put("provedor","n8n".equalsIgnoreCase(Objects.toString(r.get("ChatbotDefinicaoProvedor"),""))?"N8N":"INTERNO");v.put("endereco_externo",r.get("ChatbotDefinicaoN8nURL"));
        v.put("funcionamento",valor(r,"ChatbotDefinicaoFuncionamento",Map.of("Integral",true)));Object fluxo=valor(r,"ChatbotDefinicaoFlow",Map.of("nodes",List.of(),"edges",List.of()));var arvore=json.valueToTree(fluxo);for(var no:arvore.path("nodes")){var setor=no.path("data").path("setor");if(setor instanceof tools.jackson.databind.node.ObjectNode o&&setor.path("id").asLong(0)>0){Long sid=longo("Setor",setor.path("id").asLong());if(sid!=null)o.put("id",sid);}}v.put("fluxo",arvore);camposJson.addAll(Set.of("funcionamento","fluxo"));
      }
      case "InteligenciaArtificial"->{
        origem="chat_agente_ia";v.put("nome",r.get("InteligenciaArtificialNome"));v.put("provedor",Objects.toString(r.get("InteligenciaArtificialProvedor"),"").toLowerCase(Locale.ROOT).contains("deepseek")?"DEEPSEEK":"OPENAI");
        v.put("modelo",r.get("InteligenciaArtificialModelo"));v.put("instrucoes",r.get("InteligenciaArtificialTextoPrompt"));v.put("ativa",valor(r,"InteligenciaArtificialStatus",true));v.put("ativador",valor(r,"InteligenciaArtificialAtivador","manual-chat"));v.put("referencia_segredo","AUTENTICA:"+r.get("id"));
      }
      case "Integracao"->{origem="chat_integracao";v.put("nome",r.get("IntegracaoNome"));v.put("tipo",Objects.toString(r.get("IntegracaoNome"),"REST").toUpperCase(Locale.ROOT));v.put("configuracao",valor(r,"IntegracaoDados",Map.of()));camposJson.add("configuracao");v.put("ativa",valor(r,"IntegracaoStatus",true));}
      default->{return;}
    }
    Object existente=legado(tabela,r.get("id"));long id=existente==null?((Number)banco.um("SELECT nextval(pg_get_serial_sequence('"+origem+"','id')) id",Map.of()).get("id")).longValue():Long.parseLong(existente.toString());
    if(existente==null)banco.executar("INSERT INTO firma_autentica_vinculo VALUES(:o,:oid,:t,:id,:c,'{}')",Map.of("o",origem,"oid",Long.toString(id),"t",tabela,"id",r.get("id"),"c",c.cliente()));
    if(tabela.equals("ChatbotDefinicao")&&existente!=null){var antes=banco.um("SELECT * FROM chat_bot_definicao WHERE id=:id",Map.of("id",id));if(antes!=null&&(!Boolean.TRUE.equals(v.get("ativa"))||!Objects.equals(antes.get("canal_id"),v.get("canal_id"))||!Objects.equals(antes.get("provedor"),v.get("provedor"))||!Objects.equals(json.valueToTree(antes.get("fluxo")),json.valueToTree(v.get("fluxo")))))banco.executar("UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=now() WHERE definicao_id=:id AND estado='ATIVA'",Map.of("id",id));}
    v.put("id",id);var p=new HashMap<String,Object>();var exp=new ArrayList<String>();int i=0;
    for(var e:v.entrySet()){String k="p"+i++;p.put(k,camposJson.contains(e.getKey())?json.writeValueAsString(e.getValue()):e.getValue());exp.add(camposJson.contains(e.getKey())?"CAST(:"+k+" AS jsonb)":":"+k);}
    banco.executar("INSERT INTO "+origem+" ("+String.join(",",v.keySet())+") VALUES("+String.join(",",exp)+") ON CONFLICT(id) DO UPDATE SET "+String.join(",",v.keySet().stream().filter(k->!k.equals("id")).map(k->k+"=EXCLUDED."+k).toList()),p);
  }
}
