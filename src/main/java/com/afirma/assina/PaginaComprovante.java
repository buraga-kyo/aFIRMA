package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.springframework.stereotype.Service;

@Service
public class PaginaComprovante {
	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX");
	private static final java.awt.Color FUNDO = new java.awt.Color(14, 33, 23);
	private static final java.awt.Color PAINEL = new java.awt.Color(23, 50, 37);
	private static final java.awt.Color VERDE = new java.awt.Color(201, 255, 61);
	private static final java.awt.Color TEXTO = new java.awt.Color(242, 247, 236);
	private static final java.awt.Color SUAVE = new java.awt.Color(169, 194, 172);

	public byte[] acrescentar(byte[] pdf, String nomeEmpresa, byte[] imagemEmpresa,
			String tipo, String identificacao) throws Exception {
		if (nomeEmpresa == null || nomeEmpresa.isBlank() || nomeEmpresa.length() > 120) {
			throw new IllegalArgumentException("Informe o nome da empresa (até 120 caracteres)");
		}
		if (imagemEmpresa != null && imagemEmpresa.length > 1024 * 1024) {
			throw new IllegalArgumentException("A imagem da empresa deve ter até 1 MB");
		}
		Image imagem = null;
		if (imagemEmpresa != null && imagemEmpresa.length > 0) {
			try {
				imagem = Image.getInstance(imagemEmpresa);
				if (imagem.getOriginalType() != Image.ORIGINAL_PNG && imagem.getOriginalType() != Image.ORIGINAL_JPEG) {
					throw new IllegalArgumentException("Use imagem PNG ou JPEG da empresa");
				}
			} catch (IllegalArgumentException erro) {
				throw erro;
			} catch (Exception erro) {
				throw new IllegalArgumentException("Imagem da empresa inválida", erro);
			}
		}
		String resumo = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
		String identificador = UUID.randomUUID().toString();
		try (PdfReader leitor = new PdfReader(pdf); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PdfStamper editor = new PdfStamper(leitor, saida, null, true);
			int pagina = leitor.getNumberOfPages() + 1;
			editor.insertPage(pagina, new Rectangle(595, 842));
			PdfContentByte conteudo = editor.getOverContent(pagina);
			BaseFont fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			BaseFont destaque = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			caixa(conteudo, 0, 0, 595, 842, FUNDO);
			caixa(conteudo, 35, 694, 525, 112, PAINEL);
			//caixa(conteudo, 35, 694, 5, 112, VERDE);
			if (imagem != null) {
				imagem.scaleToFit(90, 65);
				imagem.setAbsolutePosition(450, 721);
				conteudo.addImage(imagem);
			}
			texto(conteudo, destaque, 10, VERDE, 54, 778, "COMPROVANTE DE ASSINATURA");
			nome(conteudo, destaque, nomeEmpresa, imagem == null ? 470 : 370);
			//texto(conteudo, fonte, 10, SUAVE, 54, 700, "Documento vinculado à identidade da empresa acima.");
			texto(conteudo, destaque, 15, TEXTO, 35, 644, "DADOS DO DOCUMENTO");
			linha(conteudo, 35, 631, 560, VERDE);
			campo(conteudo, fonte, destaque, 35, 603, "Identificador", identificador);
			campo(conteudo, fonte, destaque, 35, 565, "SHA-256 do PDF original", resumo);
			campo(conteudo, fonte, destaque, 35, 527, "Emitido em", DATA.format(OffsetDateTime.now(FUSO)));
			texto(conteudo, destaque, 15, TEXTO, 35, 456, "DADOS DA ASSINATURA");
			linha(conteudo, 35, 443, 560, VERDE);
			campo(conteudo, fonte, destaque, 35, 415, "Modalidade", tipo);
			campo(conteudo, fonte, destaque, 35, 377, "Identificação do signatário", identificacao);
			caixa(conteudo, 35, 245, 525, 82, PAINEL);
			texto(conteudo, destaque, 11, VERDE, 52, 301, "INTEGRIDADE DO DOCUMENTO");
			texto(conteudo, fonte, 10, TEXTO, 52, 278, "Este comprovante integra o PDF antes da assinatura.");
			texto(conteudo, fonte, 10, SUAVE, 52, 260, "Confira a assinatura criptográfica no seu leitor de PDF.");
			linha(conteudo, 35, 76, 560, VERDE);
			texto(conteudo, destaque, 9, SUAVE, 35, 57, "COMPROVANTE DE ASSINATURA");
			editor.close();
			return saida.toByteArray();
		}
	}

	private void caixa(PdfContentByte conteudo, float x, float y, float largura, float altura, java.awt.Color cor) {
		conteudo.setColorFill(cor);
		conteudo.rectangle(x, y, largura, altura);
		conteudo.fill();
	}

	private void linha(PdfContentByte conteudo, float x1, float y, float x2, java.awt.Color cor) {
		conteudo.setColorStroke(cor);
		conteudo.setLineWidth(1.5f);
		conteudo.moveTo(x1, y);
		conteudo.lineTo(x2, y);
		conteudo.stroke();
	}

	private void texto(PdfContentByte conteudo, BaseFont fonte, int tamanho, java.awt.Color cor,
			float x, float y, String valor) {
		conteudo.beginText();
		conteudo.setFontAndSize(fonte, tamanho);
		conteudo.setColorFill(cor);
		conteudo.setTextMatrix(x, y);
		conteudo.showText(valor);
		conteudo.endText();
	}

	private void textoLimitado(PdfContentByte conteudo, BaseFont fonte, int tamanho, java.awt.Color cor,
			float x, float y, String valor, float largura) {
		String exibido = valor.strip().replaceAll("[\\p{Cntrl}]", " ");
		while (fonte.getWidthPoint(exibido, tamanho) > largura && exibido.length() > 1) {
			exibido = exibido.substring(0, exibido.length() - 1);
		}
		texto(conteudo, fonte, tamanho, cor, x, y, exibido);
	}

	private void nome(PdfContentByte conteudo, BaseFont fonte, String valor, float largura) {
		String exibido = valor.strip().replaceAll("[\\p{Cntrl}]", " ");
		for (int tamanho = 20; tamanho >= 10; tamanho--) {
			java.util.List<String> linhas = new java.util.ArrayList<>();
			String restante = exibido;
			while (!restante.isEmpty()) {
				int fim = restante.length();
				while (fim > 1 && fonte.getWidthPoint(restante.substring(0, fim), tamanho) > largura) fim--;
				if (fim < restante.length()) {
					int espaco = restante.lastIndexOf(' ', fim);
					if (espaco > 0) fim = espaco;
				}
				linhas.add(restante.substring(0, fim).strip());
				restante = restante.substring(fim).stripLeading();
			}
			if (linhas.size() <= 3) {
				for (int indice = 0; indice < linhas.size(); indice++) {
					texto(conteudo, fonte, tamanho, TEXTO, 54, 752 - indice * 18, linhas.get(indice));
				}
				return;
			}
		}
		throw new IllegalArgumentException("Nome da empresa muito longo para a página");
	}

	private void campo(PdfContentByte conteudo, BaseFont fonte, BaseFont destaque, float x, float y,
			String rotulo, String valor) {
		texto(conteudo, destaque, 9, VERDE, x, y, rotulo.toUpperCase(java.util.Locale.ROOT));
		textoLimitado(conteudo, fonte, 10, TEXTO, x, y - 16, valor, 525);
	}
}
