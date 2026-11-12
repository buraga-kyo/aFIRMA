package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class ServicoCamposPdfTest {
	private final ServicoCamposPdf servico = new ServicoCamposPdf();

	@Test
	void aplicaImagemNaPaginaEscolhidaEPrevisualiza() throws Exception {
		byte[] pdf = documento();
		assertEquals(2, servico.paginas(pdf));
		byte[] previa = servico.previsualizar(pdf, 2);
		assertEquals((byte) 0x89, previa[0]);
		SignatarioEnvelope signatario = new SignatarioEnvelope(UUID.randomUUID(),
				UUID.randomUUID(), "Ana Exemplo", "ana@exemplo.com", "0".repeat(64));
		CampoAssinatura campo = new CampoAssinatura(UUID.randomUUID(), signatario.getId(),
				CampoAssinatura.Tipo.RUBRICA, 2, .45, .65, .25, .1);
		ByteArrayOutputStream imagem = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB), "png", imagem);
		byte[] marcado = servico.aplicar(pdf,
				List.of(new ServicoCamposPdf.Marca(campo, signatario, imagem.toByteArray())));
		try (PdfReader leitor = new PdfReader(marcado)) {
			assertEquals(2, leitor.getNumberOfPages());
			assertFalse(new PdfTextExtractor(leitor).getTextFromPage(1).contains("Ana Exemplo"));
			assertTrue(leitor.getPageN(2).getAsDict(org.openpdf.text.pdf.PdfName.RESOURCES)
                .getAsDict(org.openpdf.text.pdf.PdfName.XOBJECT).size() > 0);
		}
	}

	@Test
	void rejeitaCampoForaDaPagina() {
		CampoAssinatura campo = new CampoAssinatura(UUID.randomUUID(), UUID.randomUUID(),
				CampoAssinatura.Tipo.SELFIE, 1, .9, .9, .2, .2);
		assertThrows(IllegalArgumentException.class, () -> servico.validar(campo, 1));
	}

	@Test
	void posicionaEmPaginaGirada() throws Exception {
		byte[] pdf;
		try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PDPage pagina = new PDPage(new PDRectangle(200, 400));
			pagina.setRotation(90);
			documento.addPage(pagina);
			documento.save(saida);
			pdf = saida.toByteArray();
		}
		SignatarioEnvelope signatario = new SignatarioEnvelope(UUID.randomUUID(),
				UUID.randomUUID(), "Ana", "ana@exemplo.com", "0".repeat(64));
		CampoAssinatura campo = new CampoAssinatura(UUID.randomUUID(), signatario.getId(),
				CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1, .2, .2);
		ByteArrayOutputStream imagem = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB), "png", imagem);
		byte[] marcado = servico.aplicar(pdf,
				List.of(new ServicoCamposPdf.Marca(campo, signatario, imagem.toByteArray())));
		BufferedImage previa = ImageIO.read(new java.io.ByteArrayInputStream(servico.previsualizar(marcado, 1)));
		int pixel = previa.getRGB((int) (previa.getWidth() * .2), (int) (previa.getHeight() * .2));
		assertTrue((pixel & 0xffffff) < 0x888888);
	}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 90, 180, 270})
    void respeitaCaixaRotacaoETransparencia(int rotacao) throws Exception {
        byte[] pdf;
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            var pagina = new PDPage(new PDRectangle(200, 300));
            documento.addPage(pagina);
            try (var conteudo = new org.apache.pdfbox.pdmodel.PDPageContentStream(documento, pagina)) {
                conteudo.setNonStrokingColor(java.awt.Color.CYAN);
                conteudo.addRect(0, 0, 200, 300);
                conteudo.fill();
            }
            documento.save(saida);
            pdf = saida.toByteArray();
        }
        var signatario = new SignatarioEnvelope(UUID.randomUUID(), UUID.randomUUID(),
            "Ana", "ana@exemplo.com", "0".repeat(64));
        var campo = new CampoAssinatura(UUID.randomUUID(), signatario.getId(),
            CampoAssinatura.Tipo.ASSINATURA, 1, .2, .4, .4, .2);
        campo.setRotacao(rotacao);
        var png = new BufferedImage(100, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 50; x++) png.setRGB(x, y, 0xffff0000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(png, "png", bytes);
        byte[] marcado = servico.aplicar(pdf, List.of(new ServicoCamposPdf.Marca(campo, signatario, bytes.toByteArray())));
        var previa = ImageIO.read(new java.io.ByteArrayInputStream(servico.previsualizar(marcado, 1)));
        double[][] pontos = switch (rotacao) {
            case 90 -> new double[][] {{.4, .45}, {.4, .55}};
            case 180 -> new double[][] {{.5, .5}, {.3, .5}};
            case 270 -> new double[][] {{.4, .55}, {.4, .45}};
            default -> new double[][] {{.3, .5}, {.5, .5}};
        };
        assertEquals(0xff0000, pixel(previa, pontos[0][0], pontos[0][1]));
        assertEquals(0x00ffff, pixel(previa, pontos[1][0], pontos[1][1]));
        assertEquals(0x00ffff, pixel(previa, .19, .5));
        assertEquals(0x00ffff, pixel(previa, .61, .5));
        // A imagem preenche a caixa até as bordas, sem a redução para legenda.
        double[][] bordas = switch (rotacao) {
            case 90 -> new double[][] {{.205, .405}, {.595, .495}};
            case 180 -> new double[][] {{.405, .405}, {.595, .595}};
            case 270 -> new double[][] {{.205, .505}, {.595, .595}};
            default -> new double[][] {{.205, .405}, {.395, .595}};
        };
        for (var ponto : bordas) assertEquals(0xff0000, pixel(previa, ponto[0], ponto[1]));
    }

    private int pixel(BufferedImage imagem, double x, double y) {
        return imagem.getRGB((int) (imagem.getWidth() * x), (int) (imagem.getHeight() * y)) & 0xffffff;
    }

    @Test void rejeitaAnguloInvalido() {
        var campo = new CampoAssinatura(UUID.randomUUID(), UUID.randomUUID(),
            CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1, .3, .1);
        campo.setRotacao(45);
        assertThrows(IllegalArgumentException.class, () -> servico.validar(campo, 1));
    }

	private byte[] documento() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter.getInstance(documento, saida);
		documento.open();
		documento.add(new Paragraph("Primeira página"));
		documento.newPage();
		documento.add(new Paragraph("Segunda página"));
		documento.close();
		return saida.toByteArray();
	}
}
