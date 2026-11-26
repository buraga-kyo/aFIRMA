package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModelosEmailAssinaturaTest {
  @Test
  void previaAceitaTemplateHtmlSemDocumentoMaily() {
    var entrada = new tools.jackson.databind.ObjectMapper().readValue("""
        {"assunto":"Acesso","html":"<p>Código: {{codigo_verificacao}}</p>","documento":null}
        """, ModelosEmailAssinatura.Modelo.class);
    var modelo = ModelosEmailAssinatura.limpar(entrada, false);
    var previa = ModelosEmailAssinatura.renderizar(modelo, "Contrato", "Empresa", "12345678");
    assertNull(modelo.documento());
    assertEquals("<p>Código: 12345678</p>", previa.html());
  }

  @Test
  void usaDadosDoSignatarioNoAssuntoENoCorpo() {
    var modelo = ModelosEmailAssinatura.limpar(new ModelosEmailAssinatura.Modelo(
        "{{nome_assinatura}} – {{nome_signatario}}",
        "<p>{{nome_signatario}} • {{cpf_signatario}} • {{representado}} • {{codigo_verificacao}}</p>"), true);
    var mensagem = ModelosEmailAssinatura.renderizar(modelo, java.util.Map.of(
        "nome_assinatura", "Contrato", "nome_signatario", "Ana <Silva>",
        "cpf_signatario", VariaveisEmailAssinatura.mascarar("12345678942", 2),
        "codigo_verificacao", "12345678"));
    assertEquals("Contrato – Ana <Silva>", mensagem.assunto());
    assertEquals("<p>Ana &lt;Silva&gt; • &bull;&bull;&bull;&bull;42 •  • 12345678</p>", mensagem.html());
    assertFalse(mensagem.html().contains("12345678942"));
  }

  @Test
  void publicaAvisoSemCodigoEImpedeVariaveisDeOutraEtapa() {
    var etapa = MomentoEmailAssinatura.CONCLUSAO;
    var aviso = new ModelosEmailAssinatura.Modelo("Concluído", "<p>{{nome_assinatura}}</p>");
    assertEquals(aviso, ModelosEmailAssinatura.limpar(aviso, etapa, true));
    assertThrows(ResponseStatusException.class, () -> ModelosEmailAssinatura.limpar(
        new ModelosEmailAssinatura.Modelo("Concluído", "<p>{{codigo_verificacao}}</p>"), etapa, true));
  }

  @Test
  void codigoPrecisaEstarNoCorpoVisivel() {
    assertThrows(ResponseStatusException.class, () -> ModelosEmailAssinatura.limpar(
        new ModelosEmailAssinatura.Modelo("Verificação", "<img src='{{codigo_verificacao}}'>"), true));
    assertThrows(ResponseStatusException.class, () -> ModelosEmailAssinatura.limpar(
        new ModelosEmailAssinatura.Modelo("{{codigo_verificacao}}", "<p>{{codigo_verificacao}}</p>"), true));
  }

  @Test
  void preservaFormatacaoDoEditorRico() {
    String html = """
        <h2>Acesse o documento</h2><blockquote>Use o código na página do convite.</blockquote>
        <ol><li><s>Descartar código anterior</s></li><li><u>{{codigo_verificacao}}</u></li></ol><hr>
        """;
    var modelo = ModelosEmailAssinatura.limpar(new ModelosEmailAssinatura.Modelo("Acesso", html), true);
    var mensagem = ModelosEmailAssinatura.renderizar(modelo, "Contrato", "Empresa", "12345678");
    assertEquals(html.replace("{{codigo_verificacao}}", "12345678"), mensagem.html());
  }

  @Test
  void preservaEstilosEBlocosSegurosRenderizadosPeloMaily() {
    var documento = new tools.jackson.databind.ObjectMapper().readTree("""
        {"type":"doc","content":[{"type":"paragraph","content":[
          {"type":"variable","attrs":{"id":"codigo_verificacao"}}
        ]}]}
        """);
    var entrada = new ModelosEmailAssinatura.Modelo("Acesso", """
        <table class="tab-row-full classe-extra" style="width:100%;background-image:url(https://a.test)"><tbody><tr>
        <td style="padding:8px;color:#123456"><p>{{codigo_verificacao}}</p>
        <a href="https://a.test/ajuda" style="background-color:#CCFF33">Ajuda</a>
        <img src="https://a.test/marca.png" alt="Marca"><a href="https://a.test/{{codigo_verificacao}}">Outro</a>
        </td></tr></tbody></table>
        """, documento);
    var modelo = ModelosEmailAssinatura.limpar(entrada, true);
    var mensagem = ModelosEmailAssinatura.renderizar(modelo, "Contrato", "Empresa", "12345678");
    assertEquals(documento, modelo.documento());
    assertTrue(mensagem.html().contains("<p>12345678</p>"));
    assertTrue(mensagem.html().contains("background-color:#CCFF33"));
    assertTrue(mensagem.html().contains("href=\"https://a.test/ajuda\""));
    assertTrue(mensagem.html().contains("src=\"https://a.test/marca.png\""));
    assertTrue(mensagem.html().contains("@media"));
    assertFalse(mensagem.html().contains("classe-extra"));
    assertFalse(mensagem.html().contains("background-image"));
    assertFalse(mensagem.html().contains("https://a.test/12345678"));
    assertThrows(ResponseStatusException.class, () -> ModelosEmailAssinatura.limpar(
        new ModelosEmailAssinatura.Modelo("Acesso", "<img alt='{{codigo_verificacao}}'>", documento), true));
  }

  @Test
  void removeConteudoAtivoEEscapaAsVariaveis() {
    var modelo = ModelosEmailAssinatura.limpar(new ModelosEmailAssinatura.Modelo(
        "Acesso a {{nome_documento}}", """
        <script>alert(1)</script><p onclick="alert(2)">{{nome_documento}}</p>
        <p><strong>{{codigo_verificacao}}</strong></p>
        """), true);
    var mensagem = ModelosEmailAssinatura.renderizar(modelo, "<img>\n R$ 1 \\ {{codigo_verificacao}}",
        "Empresa", "12345678");
    assertFalse(mensagem.html().contains("<img>"));
    assertFalse(mensagem.html().contains("alert"));
    assertTrue(mensagem.html().contains("&lt;img&gt;"));
    assertTrue(mensagem.html().contains("<strong>12345678</strong>"));
    assertTrue(mensagem.texto().contains("\n12345678"));
    assertTrue(mensagem.assunto().endsWith("{{codigo_verificacao}}"));
    assertFalse(mensagem.assunto().contains("\n"));
  }
}
