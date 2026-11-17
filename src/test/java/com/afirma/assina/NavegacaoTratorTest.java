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
    for (var tela:Map.of("inventor","inventor","inventor-configuracao","conexoes","documentos-assinatura","documentos").entrySet()) {
      var contexto=new Context();contexto.setVariable("telaTrator",tela.getValue());
      String html=motor.process("documentos-assinatura",contexto);
      assertTrue(html.contains("id=\"tabela-montagem\""));
      assertTrue(html.contains("id=\"lista-token\""));
      assertTrue(html.contains("href=\"/chat\""));assertTrue(html.contains("href=\"/advai\""));
      assertFalse(html.contains("trator-assineai-menu"));
      assertFalse(html.contains("<iframe"));
      if(!tela.getValue().equals("documentos")) {
        assertTrue(html.contains("id=\"painel-inventor-trator\""));
        assertTrue(html.contains(tela.getValue().equals("inventor")?"id=\"nostr-pdf\"":"id=\"conexao-nostr\""));
      } else assertFalse(html.contains("id=\"painel-inventor-trator\""));
      Files.writeString(pasta.resolve(tela.getKey()+".html"),html);
    }
  }
}
