package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class IntegracaoDocusealTest {
  private Map<String,Object> campo() {return new HashMap<>(Map.of("name","Assinatura","role","Cliente","type","signature","required",true,"page",2,"x",.1,"y",.2,"w",.3,"h",.1));}
  @Test void preservaPaginaPapelECoordenadasNormalizadas() {
    var campos=IntegracaoDocuseal.validarCampos(List.of(campo()),2);
    assertEquals("Cliente",campos.getFirst().get("role"));
    assertEquals(List.of(Map.of("page",2,"x",.1,"y",.2,"w",.3,"h",.1)),campos.getFirst().get("areas"));
  }
  @Test void rejeitaPaginaInexistenteLimitesNaoFinitosEDuplicatas() {
    assertThrows(ResponseStatusException.class,()->IntegracaoDocuseal.validarCampos(List.of(campo()),1));
    var fora=campo(); fora.put("x",.9);
    assertThrows(ResponseStatusException.class,()->IntegracaoDocuseal.validarCampos(List.of(fora),2));
    fora.put("x",Double.NaN);
    assertThrows(ResponseStatusException.class,()->IntegracaoDocuseal.validarCampos(List.of(fora),2));
    assertThrows(ResponseStatusException.class,()->IntegracaoDocuseal.validarCampos(List.of(campo(),campo()),2));
    assertThrows(ResponseStatusException.class,()->IntegracaoDocuseal.validarCampos(List.of(),2));
  }
  @Test void enviaCredencialSoAoServidorSemCompartilharModelo() throws Exception {
    var json=new ObjectMapper(); var corpo=new java.util.concurrent.atomic.AtomicReference<Map>();
    var chave=new java.util.concurrent.atomic.AtomicReference<String>();
    var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    servidor.createContext("/templates/pdf", troca -> {
      chave.set(troca.getRequestHeaders().getFirst("X-Auth-Token"));
      corpo.set(json.readValue(troca.getRequestBody().readAllBytes(),Map.class));
      byte[] resposta="{\"id\":42,\"slug\":\"privado\"}".getBytes(); troca.sendResponseHeaders(200,resposta.length); troca.getResponseBody().write(resposta); troca.close();
    });
    servidor.start();
    try {
      var integracao=new IntegracaoDocuseal("http://127.0.0.1:"+servidor.getAddress().getPort(),"chave-teste",json);
      assertEquals(Map.of("id",42),integracao.criar(new byte[]{1,2,3},"Contrato",IntegracaoDocuseal.validarCampos(List.of(campo()),2)));
      assertEquals("chave-teste",chave.get()); assertEquals(false,corpo.get().get("shared_link"));
      assertEquals("AQID",((Map)((List)corpo.get().get("documents")).getFirst()).get("file"));
    } finally {servidor.stop(0);}
  }
  @Test void informaConfiguracaoAusente() {
    var erro=assertThrows(ResponseStatusException.class,()->new IntegracaoDocuseal("","",new ObjectMapper()).criar(new byte[0],"PDF",List.of()));
    assertEquals(503,erro.getStatusCode().value());
  }
}
