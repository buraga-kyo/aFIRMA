package com.afirma.assina;
import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Escritas internas tipadas; tabelas e colunas só existem no manifesto verificado. */
@Component
public class AutenticaPersistencia {
 private final BancoAssineai banco;private final ObjectMapper json;private final Map<String,Map<String,Object>> tabelas=new HashMap<>();
 @SuppressWarnings("unchecked") public AutenticaPersistencia(BancoAssineai banco,ObjectMapper json)throws Exception{this.banco=banco;this.json=json;var e=json.readValue(new ClassPathResource("autentica/esquema.json").getInputStream(),Map.class);for(var t:(List<Map<String,Object>>)e.get("tabelas"))tabelas.put(t.get("nome").toString(),t);}
 public Map<String,Object> gravar(String tabela,Long id,Map<String,Object> valores){return gravar(tabela,id,valores,false);}
 public Map<String,Object> inserirHistorico(Map<String,Object> valores){return gravar("Mensagem",null,valores,true);}
 @SuppressWarnings("unchecked") private Map<String,Object> gravar(String tabela,Long id,Map<String,Object> valores,boolean apenasInserir){var t=tabelas.get(tabela);if(t==null)throw new IllegalArgumentException();var tipos=new HashMap<String,String>();for(var c:(List<Map<String,Object>>)t.get("colunas"))tipos.put(c.get("nome").toString(),c.get("tipo").toString());var v=new LinkedHashMap<>(valores);v.put("updatedAt",OffsetDateTime.now());if(id==null)v.put("createdAt",OffsetDateTime.now());var p=new HashMap<String,Object>();var ex=new LinkedHashMap<String,String>();int i=0;
  for(var e:v.entrySet()){String tipo=tipos.get(e.getKey());if(tipo==null)throw new IllegalArgumentException("Coluna inválida: "+e.getKey());String k="p"+i++;boolean js=tipo.equals("JSON")||tipo.equals("JSONB");p.put(k,js&&e.getValue()!=null?json.writeValueAsString(e.getValue()):e.getValue());ex.put("\""+e.getKey()+"\"",js?"CAST(:"+k+" AS "+tipo+")":tipo.equals("TIMESTAMP WITH TIME ZONE")?"CAST(:"+k+" AS timestamptz)":tipo.equals("UUID")?"CAST(:"+k+" AS uuid)":":"+k);}
  String nome="\""+t.get("schema")+"\".\""+tabela+"\"";String conflito=tabela.equals("Mensagem")&&v.get("MensagemIdWhatsApp")!=null?" ON CONFLICT (\"MensagemIdWhatsApp\") DO UPDATE SET "+String.join(",",ex.keySet().stream().filter(k->!k.equals("\"createdAt\"")).map(k->k+"=EXCLUDED."+k).toList())+" WHERE "+nome+".\"CanalId\"=EXCLUDED.\"CanalId\"":"";if(apenasInserir)conflito=" ON CONFLICT (\"MensagemIdWhatsApp\") DO NOTHING";if(id==null)return banco.um("INSERT INTO "+nome+" ("+String.join(",",ex.keySet())+") VALUES("+String.join(",",ex.values())+")"+conflito+" RETURNING *",p);
  p.put("id",id);return banco.um("UPDATE "+nome+" SET "+String.join(",",ex.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+" WHERE id=:id RETURNING *",p);
 }
}
