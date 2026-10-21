package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoPropostas {
	public record Servico(String titulo, String descricao) { }
	public record Area(String titulo, List<Servico> servicos) { }
	public record Dados(String empresa, String cliente, String email, String titulo,
			String introducao, String escopo, String condicoes, String prazo,
			BigDecimal valor, BigDecimal desconto, List<Servico> servicos) { }

	public List<Area> catalogo() {
		return List.of(new Area("Direito das Famílias", List.of(
				new Servico("Divórcio", "Assessoria em divórcio consensual ou litigioso, conforme o escopo acordado."),
				new Servico("Guarda compartilhada", "Atuação na regulamentação de guarda e convivência."),
				new Servico("Alimentos", "Atuação em pedido, revisão ou exoneração de alimentos."))));
	}

	public byte[] pdf(Dados dados) throws Exception {
		validar(dados);
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			Document documento = new Document();
			PdfWriter.getInstance(documento, saida);
			documento.open();
			Font destaque = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(14, 33, 23));
			Font subtitulo = new Font(Font.HELVETICA, 12, Font.BOLD, new java.awt.Color(14, 33, 23));
			Font normal = new Font(Font.HELVETICA, 10);
			documento.add(new Paragraph(dados.empresa(), destaque));
			documento.add(new Paragraph("PROPOSTA DE SERVIÇOS JURÍDICOS", subtitulo));
			adicionar(documento, "Data: " + data(), normal);
			adicionar(documento, "Cliente: " + dados.cliente() + " · " + dados.email(), normal);
			adicionar(documento, dados.titulo(), destaque);
			adicionar(documento, dados.introducao(), normal);
			adicionar(documento, "SERVIÇOS", subtitulo);
			for (Servico servico : dados.servicos()) {
				adicionar(documento, servico.titulo(), subtitulo);
				adicionar(documento, servico.descricao(), normal);
			}
			adicionar(documento, "ESCOPO E ENTREGAS", subtitulo);
			adicionar(documento, dados.escopo(), normal);
			adicionar(documento, "HONORÁRIOS", subtitulo);
			adicionar(documento, "Valor: " + moeda(dados.valor()), normal);
			if (dados.desconto().signum() > 0) {
				adicionar(documento, "À vista: " + moeda(valorComDesconto(dados))
						+ " (desconto de " + dados.desconto().stripTrailingZeros().toPlainString() + "%).", normal);
			}
			adicionar(documento, "PRAZO", subtitulo);
			adicionar(documento, dados.prazo(), normal);
			adicionar(documento, "CONDIÇÕES", subtitulo);
			adicionar(documento, dados.condicoes(), normal);
			documento.close();
			return saida.toByteArray();
		}
	}

	public byte[] docx(Dados dados) throws Exception {
		validar(dados);
		StringBuilder corpo = new StringBuilder();
		linha(corpo, dados.empresa());
		linha(corpo, "PROPOSTA DE SERVIÇOS JURÍDICOS");
		linha(corpo, "Data: " + data());
		linha(corpo, "Cliente: " + dados.cliente() + " · " + dados.email());
		linha(corpo, dados.titulo());
		linha(corpo, dados.introducao());
		linha(corpo, "SERVIÇOS");
		for (Servico servico : dados.servicos()) {
			linha(corpo, servico.titulo());
			linha(corpo, servico.descricao());
		}
		linha(corpo, "ESCOPO E ENTREGAS");
		linha(corpo, dados.escopo());
		linha(corpo, "HONORÁRIOS");
		linha(corpo, "Valor: " + moeda(dados.valor()));
		if (dados.desconto().signum() > 0) linha(corpo, "À vista: " + moeda(valorComDesconto(dados))
				+ " (desconto de " + dados.desconto().stripTrailingZeros().toPlainString() + "%).");
		linha(corpo, "PRAZO");
		linha(corpo, dados.prazo());
		linha(corpo, "CONDIÇÕES");
		linha(corpo, dados.condicoes());
		String documento = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
				+ corpo + "<w:sectPr/></w:body></w:document>";
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(saida)) {
			entrada(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
					+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
					+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
					+ "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
					+ "</Types>");
			entrada(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
					+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
					+ "</Relationships>");
			entrada(zip, "word/document.xml", documento);
			zip.finish();
			return saida.toByteArray();
		}
	}

	private void validar(Dados dados) {
		if (dados == null || !texto(dados.empresa(), 120) || !texto(dados.cliente(), 120)
				|| !texto(dados.email(), 254) || !dados.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
				|| !texto(dados.titulo(), 160) || !texto(dados.introducao(), 4000)
				|| !texto(dados.escopo(), 8000) || !texto(dados.condicoes(), 4000)
				|| !texto(dados.prazo(), 1000) || dados.valor() == null
				|| dados.valor().signum() <= 0 || dados.valor().compareTo(new BigDecimal("100000000")) > 0
				|| dados.desconto() == null || dados.desconto().signum() < 0
				|| dados.desconto().compareTo(new BigDecimal("100")) > 0
				|| dados.servicos() == null || dados.servicos().isEmpty() || dados.servicos().size() > 30
				|| dados.servicos().stream().anyMatch(servico -> servico == null
						|| !texto(servico.titulo(), 160) || !texto(servico.descricao(), 2000))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revise os dados da proposta");
		}
	}

	private boolean texto(String valor, int limite) {
		return valor != null && !valor.isBlank() && valor.length() <= limite;
	}

	private BigDecimal valorComDesconto(Dados dados) {
		return dados.valor().multiply(BigDecimal.ONE.subtract(dados.desconto().movePointLeft(2)))
				.setScale(2, RoundingMode.HALF_UP);
	}

	private String moeda(BigDecimal valor) {
		return java.text.NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
	}

	private String data() {
		return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}

	private void adicionar(Document documento, String valor, Font fonte) throws Exception {
		for (String trecho : valor.split("\\R", -1)) documento.add(new Paragraph(trecho.isBlank() ? " " : trecho, fonte));
	}

	private void linha(StringBuilder corpo, String valor) {
		for (String trecho : valor.split("\\R", -1)) {
			corpo.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xml(trecho))
					.append("</w:t></w:r></w:p>");
		}
	}

	private String xml(String valor) {
		return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&apos;");
	}

	private void entrada(ZipOutputStream zip, String nome, String conteudo) throws Exception {
		zip.putNextEntry(new ZipEntry(nome));
		zip.write(conteudo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		zip.closeEntry();
	}
}
