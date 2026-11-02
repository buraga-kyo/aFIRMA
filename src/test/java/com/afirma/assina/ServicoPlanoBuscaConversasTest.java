package com.afirma.assina;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class ServicoPlanoBuscaConversasTest {
 private ServicoPlanoBuscaAssineAi servico(String resposta){
  var builder=mock(ChatClient.Builder.class);var cliente=mock(ChatClient.class,RETURNS_DEEP_STUBS);
  when(builder.build()).thenReturn(cliente);
  when(cliente.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(resposta);
  return new ServicoPlanoBuscaAssineAi(new ObjectMapper(),builder);
 }
 @Test void interpretaAsColunasDaConversa(){
  var plano=servico("{\"steps\":[{\"op\":\"column-filter\",\"column\":\"status\",\"type\":\"equals\",\"value\":\"Pendente\"},{\"op\":\"sort\",\"column\":\"updatedAt\",\"dir\":\"desc\"}],\"summary\":\"Pendentes recentes\"}").interpretarConversas("Conversas pendentes mais recentes");
  assertEquals(2,plano.steps().size());assertEquals("updatedAt",plano.steps().get(1).column());
 }
 @Test void recusaCamposDeOutroTrator(){
  assertThrows(ResponseStatusException.class,()->servico("{\"steps\":[{\"op\":\"sort\",\"column\":\"titulo\",\"dir\":\"asc\"}]}").interpretarConversas("Ordenar conversas"));
 }
 @Test void assinaturasContinuamAceitandoSeusCampos(){
  assertEquals("titulo",servico("{\"steps\":[{\"op\":\"sort\",\"column\":\"titulo\",\"dir\":\"asc\"}]}").interpretar("Ordenar documentos").steps().getFirst().column());
 }
}
