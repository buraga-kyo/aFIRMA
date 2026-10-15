package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;

class ServicoAssinaturaPdfTest {

	private final ServicoAssinaturaPdf servico = new ServicoAssinaturaPdf();

	@Test
	void assinaPdfEVerificaIntegridade() throws Exception {
		byte[] assinado = servico.assinar(criarPdf(), criarCertificado(false), "senha-teste".toCharArray());
		PdfReader leitor = new PdfReader(assinado);
		try {
			var campos = leitor.getAcroFields();
			assertEquals(1, campos.getSignedFieldNames().size());
			assertTrue(campos.verifySignature(campos.getSignedFieldNames().getFirst()).verify());
		} finally {
			leitor.close();
		}
	}

	@Test
	void rejeitaCertificadoVencido() throws Exception {
		byte[] certificado = criarCertificado(true);
		assertThrows(CertificateExpiredException.class,
				() -> servico.assinar(criarPdf(), certificado, "senha-teste".toCharArray()));
	}

	private byte[] criarPdf() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter.getInstance(documento, saida);
		documento.open();
		documento.add(new Paragraph("Documento de teste"));
		documento.close();
		return saida.toByteArray();
	}

	private byte[] criarCertificado(boolean vencido) throws Exception {
		var gerador = KeyPairGenerator.getInstance("RSA");
		gerador.initialize(2048);
		var par = gerador.generateKeyPair();
		Instant agora = Instant.now();
		Date inicio = Date.from(agora.minusSeconds(vencido ? 172800 : 60));
		Date fim = Date.from(agora.plusSeconds(vencido ? -86400 : 86400));
		X500Name nome = new X500Name("CN=Teste aFirma");
		var construtor = new JcaX509v3CertificateBuilder(nome, BigInteger.ONE, inicio, fim, nome, par.getPublic());
		var assinador = new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate());
		Certificate certificado = new JcaX509CertificateConverter().getCertificate(construtor.build(assinador));
		KeyStore armazenamento = KeyStore.getInstance("PKCS12");
		armazenamento.load(null, null);
		armazenamento.setKeyEntry("teste", par.getPrivate(), "senha-teste".toCharArray(),
				new Certificate[] {certificado});
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		armazenamento.store(saida, "senha-teste".toCharArray());
		return saida.toByteArray();
	}
}
