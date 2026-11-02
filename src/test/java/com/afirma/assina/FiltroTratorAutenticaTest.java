package com.afirma.assina;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class FiltroTratorAutenticaTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void ordenaEFiltraSomenteColunasPublicasComParametros(){var params=new HashMap<String,Object>();var r=FiltroTratorAutentica.preparar(json,Map.of("ordem","{\"id\":\"ContatoNome\",\"dir\":\"asc\"}","filtros","{\"ContatoNome\":\"a' OR 1=1 --%\"}"),List.of("id","ContatoNome"),List.of(),params);assertEquals("t.\"ContatoNome\" asc NULLS LAST,t.id DESC",r.ordem());assertFalse(r.where().contains("1=1"));assertEquals("%a' OR 1=1 --\\%%",params.get("trator0"));}
 @Test void recusaColunasSecretasEDirecaoArbitraria(){assertThrows(ResponseStatusException.class,()->FiltroTratorAutentica.preparar(json,Map.of("filtros","{\"UsuarioSenha\":\"a\"}"),List.of("id"),List.of(),new HashMap<>()));assertThrows(ResponseStatusException.class,()->FiltroTratorAutentica.preparar(json,Map.of("ordem","{\"id\":\"id\",\"dir\":\"desc;drop table empresa\"}"),List.of("id"),List.of(),new HashMap<>()));}
 @Test void preservaZeroNosFiltrosNumericos(){var params=new HashMap<String,Object>();var r=FiltroTratorAutentica.preparar(json,Map.of("filtros","{\"id\":{\"from\":0,\"to\":5}}"),List.of("id"),List.of(),params);assertTrue(r.where().contains(">=:trator0from"));assertEquals(new java.math.BigDecimal("0"),params.get("trator0from"));}
}
