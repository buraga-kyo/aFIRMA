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
			assertTrue(new PdfTextExtractor(leitor).getTextFromPage(2).contains("Ana Exemplo"));
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
