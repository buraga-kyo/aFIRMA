package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import tools.jackson.databind.ObjectMapper;

class ServicoPdfBlockchainTest {
	private final ServicoPdfBlockchain servico = new ServicoPdfBlockchain(new ObjectMapper());

	@Test
	void provaPermaneceVinculadaAosBytesDoPdf() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter.getInstance(documento, saida);
		documento.open();
		documento.add(new Paragraph("Contrato de teste"));
		documento.close();
		var preparado = servico.preparar(saida.toByteArray());
		var prova = new ProvaBlockchain(1, "0x" + "1".repeat(40), preparado.resumo(),
				UUID.randomUUID().toString(), 2_000_000_000L, "0x" + "2".repeat(130),
				"0x" + "3".repeat(64), "0x" + "4".repeat(64), 1_900_000_000L);
		byte[] pdf = servico.concluir(preparado.bytes(), prova);
		assertEquals(preparado.resumo(), servico.ler(pdf).resumoCalculado());
		pdf[100] ^= 1;
		assertThrows(RuntimeException.class, () -> {
			var leitura = servico.ler(pdf);
			if (!leitura.resumoCalculado().equals(preparado.resumo()))
				throw new IllegalArgumentException("PDF alterado");
		});
	}

	@Test
	void paginaDaEmpresaIntegraProvaEletronica() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter.getInstance(documento, saida);
		documento.open();
		documento.add(new Paragraph("Contrato"));
		documento.close();
		byte[] personalizado = new PaginaComprovante().acrescentar(saida.toByteArray(),
				"Empresa Exemplo", null, "Assinatura eletrônica", "0x" + "1".repeat(40));
		var preparado = servico.preparar(personalizado);
		var prova = new ProvaBlockchain(1, "0x" + "1".repeat(40), preparado.resumo(),
				UUID.randomUUID().toString(), 2_000_000_000L, "0x" + "2".repeat(130),
				"0x" + "3".repeat(64), "0x" + "4".repeat(64), 1_900_000_000L);
		byte[] finalizado = servico.concluir(preparado.bytes(), prova);
		try (PdfReader leitor = new PdfReader(finalizado)) {
			assertEquals(2, leitor.getNumberOfPages());
			assertEquals(true, new PdfTextExtractor(leitor).getTextFromPage(2).contains("Empresa Exemplo"));
		}
		assertEquals(preparado.resumo(), servico.ler(finalizado).resumoCalculado());
	}
}
