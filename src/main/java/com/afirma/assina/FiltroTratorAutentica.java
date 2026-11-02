package com.afirma.assina;

import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Traduz apenas colunas públicas do manifesto; valores são sempre parâmetros SQL. */
final class FiltroTratorAutentica {
 record Consulta(String where,String ordem) {}
 static Consulta preparar(ObjectMapper json,Map<String,String> filtros,List<String> colunas,List<Map<String,Object>> campos,Map<String,Object> params){
  try{
   String ordem="t.id DESC",where="";
   var o=json.readTree(filtros.getOrDefault("ordem","null"));
   if(o!=null&&!o.isNull()){
    String id=o.path("id").asText(),dir=o.path("dir").asText();
    if(!colunas.contains(id)||!Set.of("asc","desc").contains(dir))throw new IllegalArgumentException();
    ordem="t.\""+id+"\" "+dir+" NULLS LAST"+(id.equals("id")?"":",t.id DESC");
   }
   String bruto=filtros.getOrDefault("filtros","{}");if(bruto.length()>8000)throw new IllegalArgumentException();
   var f=json.readTree(bruto);if(!f.isObject()||f.size()>20)throw new IllegalArgumentException();int i=0;
   for(var e:f.properties()){
    String id=e.getKey();if(!colunas.contains(id))throw new IllegalArgumentException();String campo="t.\""+id+"\"",p="trator"+i++;var v=e.getValue();
    if(v.isArray()){
     if(v.size()>100)throw new IllegalArgumentException();var valores=new ArrayList<String>();v.forEach(n->valores.add(n.asText()));
     if(!valores.isEmpty()){params.put(p,valores);where+=" AND CAST("+campo+" AS text) IN (:"+p+")";}
    }else if(v.isObject()){
     String tipo=campos.stream().filter(c->id.equals(c.get("nome"))).map(c->c.get("tipo").toString()).findFirst().orElse("INTEGER");
     boolean data=tipo.contains("TIMESTAMP");boolean numero=tipo.contains("INTEGER")||tipo.contains("DECIMAL")||tipo.equals("BIGINT")||tipo.contains("NUMERIC")||tipo.contains("FLOAT")||tipo.contains("DOUBLE");
     if(!data&&!numero)throw new IllegalArgumentException();
     for(String limite:List.of("from","to")){String valor=v.path(limite).asText("");if(valor.isBlank())continue;String k=p+limite;
      if(data){var dia=java.time.LocalDate.parse(valor);params.put(k,(limite.equals("to")?dia.plusDays(1):dia).atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toOffsetDateTime());}
      else params.put(k,new java.math.BigDecimal(valor));
      where+=" AND "+campo+(limite.equals("from")?">=":data?"<":"<=")+":"+k;
     }
    }else if(v.isTextual()){
     params.put(p,"%"+v.asText().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");where+=" AND CAST("+campo+" AS text) ILIKE :"+p;
    }else throw new IllegalArgumentException();
   }
   return new Consulta(where,ordem);
  }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filtro ou ordenação do Trator inválido");}
 }
}
