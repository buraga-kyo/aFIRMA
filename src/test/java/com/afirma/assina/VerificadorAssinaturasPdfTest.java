package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfSignatureAppearance;
import org.openpdf.text.pdf.ColumnText;

class VerificadorAssinaturasPdfTest {
	private static ArquivosAssinaturaTeste.Certificado certificado;
	private final ServicoAssinaturaPdf assinador = new ServicoAssinaturaPdf();
	private final VerificadorAssinaturasPdf verificador = new VerificadorAssinaturasPdf(
			new PoliticaCertificados("", ""), new CarimboTempo("", ""));
	@TempDir Path diretorio;

	@BeforeAll static void prepararCertificado() throws Exception { certificado = ArquivosAssinaturaTeste.certificado(true); }

	@Test void confirmaCoberturaSemConfundirIntegridadeComConfianca() throws Exception {
		byte[] assinado = assinar(ArquivosAssinaturaTeste.pdf());
		var resultado = verificador.verificar(assinado);
		assertTrue(resultado.integridadeConfirmada());
		assertEquals("NAO_VERIFICADA", resultado.assinaturas().getFirst().confianca());
		assertEquals("NAO_CONSULTADA", resultado.assinaturas().getFirst().revogacao());
		assertEquals("AUSENTE", resultado.assinaturas().getFirst().carimboTempo());
		assertTrue(verificador.verificar(assinar(assinado)).integridadeConfirmada());
	}

	@Test void detectaAlteracaoDosBytesAssinadosERecusaNovaAssinatura() throws Exception {
		byte[] assinado = assinar(ArquivosAssinaturaTeste.pdf());
		String conteudo = new String(assinado, StandardCharsets.ISO_8859_1);
		assertTrue(conteudo.contains("Conteudo original"));
		byte[] adulterado = conteudo.replace("Conteudo original", "Conteudo alterado").getBytes(StandardCharsets.ISO_8859_1);
		assertFalse(verificador.verificar(adulterado).integridadeConfirmada());
		assertThrows(IllegalArgumentException.class, () -> assinar(adulterado));
	}

	@Test void identificaRevisaoAcrescentadaDepoisDaAssinatura() throws Exception {
		byte[] assinado = assinar(ArquivosAssinaturaTeste.pdf());
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		try (PdfReader leitor = new PdfReader(assinado)) {
			PdfStamper editor = new PdfStamper(leitor, saida, null, true);
			ColumnText texto = new ColumnText(editor.getOverContent(1));
			texto.setSimpleColumn(40, 40, 400, 400);
			texto.addElement(new Paragraph("Acrescimo posterior"));
			texto.go();
			editor.close();
		}
		var resultado = verificador.verificar(saida.toByteArray());
		assertTrue(resultado.assinaturas().getFirst().integra());
		assertFalse(resultado.integridadeConfirmada());
	}

	@Test void validaCadeiaSomenteContraAutoridadesConfiguradas() throws Exception {
		Path raizes = diretorio.resolve("raizes.pem");
		Files.writeString(raizes, "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[] {'\n'})
				.encodeToString(certificado.raiz().getEncoded()) + "\n-----END CERTIFICATE-----\n");
		PoliticaCertificados politica = new PoliticaCertificados(raizes.toString(), "");
		assertEquals("CONFIAVEL", politica.avaliar(new Certificate[] {certificado.titular(), certificado.raiz()}).confianca());
		var desconhecido = ArquivosAssinaturaTeste.certificado(true);
		assertEquals("NAO_CONFIAVEL", politica.avaliar(new Certificate[] {desconhecido.titular(), desconhecido.raiz()}).confianca());
		var assinadorEstrito = new ServicoAssinaturaPdf(politica, new CarimboTempo("", ""));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> assinadorEstrito.assinar(ArquivosAssinaturaTeste.pdf(), desconhecido.arquivo(), "senha-teste".toCharArray(), false));
	}

	@Test void recusaCertificadoSemUsoDeAssinatura() throws Exception {
		var inadequado = ArquivosAssinaturaTeste.certificado(false);
		assertThrows(IllegalArgumentException.class,
				() -> assinador.assinar(ArquivosAssinaturaTeste.pdf(), inadequado.arquivo(), "senha-teste".toCharArray(), false));
	}

	@Test void recusaAlterarPdfComRestricoesDeCertificacao() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		try (PdfReader leitor = new PdfReader(ArquivosAssinaturaTeste.pdf())) {
			PdfStamper editor = PdfStamper.createSignature(leitor, saida, null);
			var aparencia = editor.getSignatureAppearance();
			aparencia.setCrypto(certificado.chavesTitular().getPrivate(), new Certificate[] {certificado.titular(), certificado.raiz()},
					null, PdfSignatureAppearance.WINCER_SIGNED);
			aparencia.setCertificationLevel(PdfSignatureAppearance.CERTIFIED_NO_CHANGES_ALLOWED);
			editor.close();
		}
		assertThrows(IllegalArgumentException.class, () -> assinar(saida.toByteArray()));
	}

	@Test void naoPermiteConfiguracaoParcialDeServicosExternos() {
		assertThrows(IllegalArgumentException.class, () -> new CarimboTempo("https://tsa.exemplo.com", ""));
		assertThrows(IllegalArgumentException.class, () -> new PoliticaCertificados("", "https://ocsp.exemplo.com"));
	}

	@Test void assinaComCertificadoEcEReferenciaCertificadoNoCms() throws Exception {
		var materialEc = ArquivosAssinaturaTeste.certificado(true, "EC");
		byte[] pdf = assinador.assinar(ArquivosAssinaturaTeste.pdf(), materialEc.arquivo(), "senha-teste".toCharArray(), false);
		assertTrue(verificador.verificar(pdf).integridadeConfirmada());
		try (PdfReader leitor = new PdfReader(pdf)) {
			var campos = leitor.getAcroFields();
			var dicionario = campos.getSignatureDictionary(campos.getSignedFieldNames().getFirst());
			assertEquals(new org.openpdf.text.pdf.PdfName("ETSI.CAdES.detached"), dicionario.getAsName(org.openpdf.text.pdf.PdfName.SUBFILTER));
		}
	}

	@Test void recusaPdfComJavascriptOuArquivoAnexado() throws Exception {
		for (boolean script : new boolean[] {true, false}) {
			ByteArrayOutputStream saida = new ByteArrayOutputStream();
			var documento = new org.openpdf.text.Document();
			var escritor = org.openpdf.text.pdf.PdfWriter.getInstance(documento, saida);
			documento.open(); documento.add(new Paragraph("Documento para testar recursos ativos"));
			if (script) escritor.addJavaScript("app.alert('teste');");
			else escritor.addFileAttachment("Arquivo anexado", new byte[] {1,2,3}, "anexo.txt", "anexo.txt");
			documento.close();
			assertThrows(ErroAssinatura.class, () -> assinar(saida.toByteArray()));
		}
	}

	@Test void certificadoNaoPodeDeclararTitularComOutraChavePrivada() throws Exception {
		var outro = ArquivosAssinaturaTeste.certificado(true);
		var armazenamento = java.security.KeyStore.getInstance("PKCS12"); armazenamento.load(null, null);
		armazenamento.setKeyEntry("empresa", certificado.chavesTitular().getPrivate(), "senha-teste".toCharArray(),
				new Certificate[] {outro.titular(), outro.raiz()});
		ByteArrayOutputStream saida = new ByteArrayOutputStream(); armazenamento.store(saida, "senha-teste".toCharArray());
		var erro = assertThrows(ErroAssinatura.class, () -> assinador.consultarCertificado(saida.toByteArray(), "senha-teste".toCharArray()));
		assertEquals("Chave privada não corresponde ao certificado", erro.getMessage());
	}

	private byte[] assinar(byte[] pdf) throws Exception {
		return assinador.assinar(pdf, certificado.arquivo(), "senha-teste".toCharArray(), false);
	}
}
