package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class ServicoPropostasTest {
	@Test
	void geraPdfEDocxComServicosSemLimiteFixo() throws Exception {
		ServicoPropostas servico = new ServicoPropostas();
		List<ServicoPropostas.Servico> servicos = IntStream.rangeClosed(1, 6)
				.mapToObj(numero -> new ServicoPropostas.Servico("Serviço " + numero, "Entrega " + numero))
				.toList();
		ServicoPropostas.Dados dados = new ServicoPropostas.Dados("Empresa Exemplo", "Cliente Exemplo",
				"cliente@example.org", "Proposta teste", "Apresentação", "Escopo", "Condições",
				"Prazo", new BigDecimal("1000.00"), new BigDecimal("10"), servicos);
		byte[] pdf = servico.pdf(dados);
		try (PdfReader leitor = new PdfReader(pdf)) {
			String texto = new PdfTextExtractor(leitor).getTextFromPage(1);
			assertTrue(texto.contains("Empresa Exemplo"));
			assertTrue(texto.contains("Serviço 6"));
			assertTrue(texto.contains("900,00"));
		}
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(servico.docx(dados)))) {
			String documento = null;
			for (var entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
				if (entrada.getName().equals("word/document.xml")) documento = new String(zip.readAllBytes());
			}
			assertTrue(documento.contains("Serviço 6"));
			assertTrue(documento.contains("Empresa Exemplo"));
			assertEquals(1, documento.split("Serviço 6", -1).length - 1);
		}
	}
}
