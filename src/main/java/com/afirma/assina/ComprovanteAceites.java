package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;

public class ComprovanteAceites {
	public byte[] acrescentar(byte[] pdf, EnvelopeAssinatura envelope,
			List<SignatarioEnvelope> signatarios) throws Exception {
		DateTimeFormatter formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")
				.withZone(ZoneId.of("America/Sao_Paulo"));
		try (PdfReader leitor = new PdfReader(pdf); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PdfStamper editor = new PdfStamper(leitor, saida, null, true);
			// As evidências entram antes da assinatura da empresa para ficarem protegidas pelo mesmo selo.
			for (SignatarioEnvelope signatario : signatarios) {
				int numeroPagina = leitor.getNumberOfPages() + 1;
				editor.insertPage(numeroPagina, new Rectangle(595, 842));
				ColumnText texto = new ColumnText(editor.getOverContent(numeroPagina));
				texto.setSimpleColumn(40, 40, 555, 802);
				texto.addElement(new Paragraph("REGISTRO DE ACEITE DO DOCUMENTO"));
				texto.addElement(new Paragraph("Empresa: " + limpar(envelope.getNomeEmpresa())));
				texto.addElement(new Paragraph("Envelope: " + envelope.getId()));
				texto.addElement(new Paragraph("Nome informado: " + limpar(signatario.getNome())));
				texto.addElement(new Paragraph("E-mail: " + limpar(signatario.getEmail())));
                texto.addElement(new Paragraph("WhatsApp: " + limpar(signatario.getWhatsapp())));
                texto.addElement(new Paragraph("CPF: " + limpar(signatario.getCpf())));
                texto.addElement(new Paragraph("RG: " + limpar(signatario.getRg())));
                texto.addElement(new Paragraph("IP: " + limpar(signatario.getEnderecoIp())));
                texto.addElement(new Paragraph("Origem do IP: " + ColetaDadosAssinatura.descreverOrigem(signatario.getOrigemIp())));
                texto.addElement(new Paragraph("Localização: " + ColetaDadosAssinatura.descreverLocalizacao(signatario.getGeolocalizacao())));
				texto.addElement(new Paragraph("Aceito em: " + formatoData.format(signatario.getAssinadoEm())));
				texto.addElement(new Paragraph("Autenticação: " + (signatario.getAutenticacaoId() == null
						? "Registro legado sem autenticação por código" : "Código enviado ao e-mail cadastrado")));
				texto.addElement(new Paragraph("Atuação: " + ("REPRESENTANTE".equals(signatario.getPapel())
						? "Representante de " + limpar(signatario.getRepresentado()) : "Em nome próprio")));
				texto.addElement(new Paragraph("SHA-256 da versão aceita:"));
				texto.addElement(new Phrase(signatario.getResumoDocumentoAceito() == null
						? "Não registrado nesta versão do sistema" : signatario.getResumoDocumentoAceito()));
				texto.addElement(new Paragraph("Declaração de aceite:"));
				texto.addElement(new Paragraph(signatario.getTextoAceite() == null
						? "Registro anterior à declaração versionada de aceite" : signatario.getTextoAceite()));
				texto.addElement(new Paragraph("As marcas dos convidados são imagens. O código confirma acesso ao e-mail cadastrado, "
						+ "sem verificar automaticamente a identidade civil ou os poderes do participante. A assinatura criptográfica final "
						+ "pertence ao titular do certificado usado pela empresa."));
				texto.addElement(new Paragraph("As datas de aceite são registradas pelo servidor. "
						+ "Um eventual carimbo do tempo atesta a assinatura final, não o horário de cada aceite."));
				while (ColumnText.hasMoreText(texto.go())) {
                    numeroPagina++;
                    editor.insertPage(numeroPagina, new Rectangle(595, 842));
                    texto.setCanvas(editor.getOverContent(numeroPagina));
                    texto.setSimpleColumn(40, 40, 555, 802);
                }
			}
			editor.close();
			return saida.toByteArray();
		}
	}

	public byte[] acrescentarVinculoEvidencias(byte[] pdf, EnvelopeAssinatura envelope,
			String resumoManifesto, String resumoSelo) throws Exception {
		try (PdfReader leitor = new PdfReader(pdf); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PdfStamper editor = new PdfStamper(leitor, saida, null, true);
			int pagina = leitor.getNumberOfPages() + 1;
			editor.insertPage(pagina, new Rectangle(595, 842));
			ColumnText texto = new ColumnText(editor.getOverContent(pagina));
			texto.setSimpleColumn(40, 40, 555, 802);
			texto.addElement(new Paragraph("PRESERVAÇÃO DAS EVIDÊNCIAS"));
			texto.addElement(new Paragraph("Envelope: " + envelope.getId()));
			texto.addElement(new Paragraph("Tipo declarado: " + envelope.getTipoDocumento()));
			texto.addElement(new Paragraph("SHA-256 do manifesto assinado:"));
			texto.addElement(new Paragraph(resumoManifesto));
			texto.addElement(new Paragraph("SHA-256 do CMS destacado do manifesto:"));
			texto.addElement(new Paragraph(resumoSelo));
			texto.addElement(new Paragraph("O manifesto relaciona as versões preservadas e o resumo do histórico até a finalização. "
					+ "Seu selo e este PDF são assinados pelo certificado utilizado pela empresa. "
					+ "As evidências completas podem ser exportadas pelo responsável autorizado."));
			texto.addElement(new Paragraph("A assinatura não verifica automaticamente a capacidade das partes, os poderes de representação "
					+ "ou o atendimento a formalidades próprias do ato. O enquadramento do documento foi declarado pelo responsável."));
			while (ColumnText.hasMoreText(texto.go())) {
                    pagina++;
                    editor.insertPage(pagina, new Rectangle(595, 842));
                    texto.setCanvas(editor.getOverContent(pagina));
                    texto.setSimpleColumn(40, 40, 555, 802);
                }
			editor.close();
			return saida.toByteArray();
		}
	}

	private String limpar(String texto) {
		return texto == null ? "Não informado" : texto.replaceAll("[\\p{Cntrl}]", " ");
	}
}
