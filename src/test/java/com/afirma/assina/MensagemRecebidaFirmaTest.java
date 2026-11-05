package com.afirma.assina;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class MensagemRecebidaFirmaTest {
  private final ObjectMapper json=new ObjectMapper();
  @Test void respostaDeListaChegaComConteudoUtilAoBot() throws Exception {
    var mensagem=MensagemRecebidaFirma.interpretar(json.readTree("""
        {"type":"interactive","interactive":{"type":"list_reply","list_reply":
          {"id":"financeiro","title":"Financeiro","description":"Segunda via"}},"context":{"id":"pergunta-1"}}
        """));
    assertEquals("Financeiro\nSegunda via",mensagem.texto());
    assertEquals("pergunta-1",mensagem.resposta());
  }
  @Test void recebeBotaoEContatoComTelefones() throws Exception {
    assertEquals("Aceito",MensagemRecebidaFirma.interpretar(json.readTree("""
        {"type":"button","button":{"text":"Aceito","payload":"sim"}}
        """)).texto());
    assertEquals("Maria\n+5511999990000\nmaria@example.test",MensagemRecebidaFirma.interpretar(json.readTree("""
        {"type":"contacts","contacts":[{"name":{"formatted_name":"Maria"},
          "phones":[{"phone":"+5511999990000"}],"emails":[{"email":"maria@example.test"}]}]}
        """)).texto());
  }
  @Test void localizacaoPreservaCoordenadasERecusaValoresImpossiveis() throws Exception {
    assertTrue(MensagemRecebidaFirma.interpretar(json.readTree("""
        {"type":"location","location":{"latitude":-23.55,"longitude":-46.63,"name":"Escritório"}}
        """)).texto().contains("https://maps.google.com/?q=-23.55,-46.63"));
    var invalida=json.readTree("{\"type\":\"location\",\"location\":{\"latitude\":91,\"longitude\":0}}");
    assertThrows(IllegalArgumentException.class,()->MensagemRecebidaFirma.interpretar(invalida));
  }
  @Test void documentoPreservaLegendaNomeETipo() throws Exception {
    var mensagem=MensagemRecebidaFirma.interpretar(json.readTree("""
        {"type":"document","document":{"id":"12345","filename":"contrato.pdf",
          "mime_type":"application/pdf","caption":"Contrato para revisão"}}
        """));
    assertEquals("Contrato para revisão",mensagem.texto());
    assertEquals("12345",mensagem.midia());
    assertEquals("contrato.pdf",mensagem.nome());
    assertEquals("application/pdf",mensagem.mime());
  }
}
