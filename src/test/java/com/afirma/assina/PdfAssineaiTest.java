package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class PdfAssineaiTest {

  private final PdfAssineai servico = new PdfAssineai(new ServicoCamposPdf());

  private byte[] pdf() throws Exception {
    try (var doc = new PDDocument(); var saida = new ByteArrayOutputStream()) {
      doc.addPage(new PDPage());
      doc.save(saida);
      return saida.toByteArray();
    }
  }

  @Test
  void provaCompativelComChavesSpkiEAssinaturaRsaSha256() throws Exception {
    byte[] original = pdf();
    var documento = new LinkedHashMap<String, Object>();
    documento.put("DocumentoBuffer", original);
    documento.putAll(servico.prova(original));
    assertTrue(servico.verificar(documento));
    byte[] adulterado = original.clone();
    adulterado[20] ^= 1;
    documento.put("DocumentoBuffer", adulterado);
    assertFalse(servico.verificar(documento));
  }

  @Test
  void converteCoordenadasLegadasSemAlterarIdentificadorDoSignatario()
    throws Exception {
    var campo = servico.normalizar(
      Map.of("GUID", "guid-preservado", "x", 60, "y", 80),
      pdf()
    );
    assertEquals("guid-preservado", campo.get("GUID"));
    assertEquals(1, campo.get("pagina"));
    assertTrue(((Number) campo.get("posicaoX")).doubleValue() > 0);
    assertEquals("ASSINATURA", campo.get("tipo"));
  }

  @Test
  void impedeCampoNormalizadoForaDaPagina() throws Exception {
    byte[] pdf = pdf();
    assertThrows(IllegalArgumentException.class, () ->
      servico.normalizar(
        Map.of(
          "GUID",
          "preservado",
          "pagina",
          2,
          "posicaoX",
          .1,
          "posicaoY",
          .1
        ),
        pdf
      )
    );
  }

  @Test
  void relatorioPertenceAoDocumentoIndividual() throws Exception {
    var documento = new LinkedHashMap<String, Object>();
    documento.put("DocumentoBuffer", pdf());
    documento.put("DocumentoTitulo", "Contrato individual");
    documento.put("DocumentoToken", "token-do-original");
    documento.put(
      "DocumentoLinkAutenticacao",
      "https://firma.example/web-panels/autenticidade-documento/token-do-original"
    );
    documento.putAll(servico.prova((byte[]) documento.get("DocumentoBuffer")));
    var p = new LinkedHashMap<String, Object>();
    p.put("SignatarioAssinou", true);
    p.put("SignatarioId", 1);
    p.put("SignatarioNome", "Ana");
    p.put("SignatarioDataAssinatura", "2026-10-29T00:00:00Z");
    p.put("SignatarioAutenticadoVia", "email");
    byte[] marcado = servico.aplicar(
      documento,
      List.of(p),
      List.of(),
      Map.of()
    );
    try (var leitor = new PdfReader(marcado)) {
      assertEquals(2, leitor.getNumberOfPages());
      assertTrue(
        new PdfTextExtractor(leitor)
          .getTextFromPage(2)
          .contains("token-do-original")
      );
      assertTrue(
        new PdfTextExtractor(leitor).getTextFromPage(2).contains("Ana")
      );
    }
  }

  @Test
  void evidenciaAdicionadaAoLimiteOriginalContinuaLegivel() throws Exception {
    byte[] original;
    try (var pdf = new PDDocument(); var saida = new ByteArrayOutputStream()) {
      for (int i = 0; i < 100; i++) pdf.addPage(new PDPage());
      pdf.save(saida);
      original = saida.toByteArray();
    }
    assertEquals(100, new ServicoCamposPdf().paginas(original));
    var documento = new LinkedHashMap<String, Object>();
    documento.put("DocumentoBuffer", original);
    documento.put("DocumentoTitulo", "Documento com 100 páginas");
    byte[] marcado = servico.aplicar(documento, List.of(), List.of(), Map.of());
    assertEquals(101, servico.paginasDocumento(marcado));
  }
}
