package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class NavegacaoTratorTest {
  @Test void inventorEConexoesSaoConteudoDoTratorComNavegacaoOriginal() throws Exception {
    var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
    var motor=new SpringTemplateEngine();motor.setTemplateResolver(resolver);
    var pasta=Path.of("target/telas-trator-test");Files.createDirectories(pasta);
    for (var tela:Map.of("inventor","inventor","inventor-configuracao","conexoes","documentos-assinatura","documentos","modelos-email-assinatura","emails").entrySet()) {
      var contexto=new Context();contexto.setVariable("telaTrator",tela.getValue());
      if (tela.getValue().equals("emails")) {
        contexto.setVariable("variaveisEmail", VariaveisEmailAssinatura.catalogo());
        contexto.setVariable("momentosEmail", MomentoEmailAssinatura.values());
      }
      String html=motor.process("documentos-assinatura",contexto);
      assertTrue(html.contains("id=\"tabela-montagem\""));
      assertTrue(html.contains("id=\"lista-token\""));
      assertTrue(html.contains("href=\"/chat\""));assertTrue(html.contains("href=\"/advai\""));
      assertFalse(html.contains("trator-assineai-menu"));
      assertEquals(tela.getValue().equals("emails"), html.contains("<iframe"));
      assertTrue(html.contains("href=\"/assineai/modelos-email\""));
      if (tela.getValue().equals("emails")) {
        assertTrue(html.contains("id=\"modelos-email\" hidden"));
        assertTrue(html.contains("id=\"cartas-novo\""));
        assertTrue(html.indexOf("id=\"email-modelo-modo\"") < html.indexOf("id=\"email-modelo-momento\""));
        assertTrue(html.indexOf("id=\"email-modelo-maily\"") < html.indexOf("id=\"email-modelo-painel-previa\""));
        assertTrue(html.contains("id=\"alternar-largura\""));
        for (var momento : MomentoEmailAssinatura.values())
          assertTrue(html.contains("value=\"" + momento.id() + "\""), momento.id());
        for (var variavel : VariaveisEmailAssinatura.catalogo())
          assertTrue(html.contains("value=\"" + variavel.nome() + "\""), variavel.nome());
        assertTrue(html.indexOf("href=\"/advai\"") < html.indexOf("href=\"/assineai/modelos-email\""));
        assertFalse(html.contains("src=\"/js/documentos-assinatura.js\""));
        assertTrue(html.contains("aria-current=\"page\">Cartas"));
      }
      if(tela.getValue().equals("inventor") || tela.getValue().equals("conexoes")) {
        assertTrue(html.contains("id=\"painel-inventor-trator\""));
        switch (tela.getValue()) {
          case "inventor" -> assertTrue(html.contains("id=\"nostr-pdf\""));
          case "conexoes" -> assertTrue(html.contains("id=\"conexao-nostr\""));
          default -> fail("Tela desconhecida");
        }
      } else assertFalse(html.contains("id=\"painel-inventor-trator\""));
      Files.writeString(pasta.resolve(tela.getKey()+".html"),html);
    }
  }
}
