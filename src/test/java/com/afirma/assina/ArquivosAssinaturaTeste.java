package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

final class ArquivosAssinaturaTeste {
	static byte[] pdf() throws Exception {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		Document documento = new Document();
		PdfWriter escritor = PdfWriter.getInstance(documento, saida);
		escritor.setCompressionLevel(0);
		documento.open();
		documento.add(new Paragraph("Conteudo original para conferir adulteracao"));
		documento.close();
		return saida.toByteArray();
	}

	static Certificado certificado(boolean permiteAssinatura) throws Exception {
		return certificado(permiteAssinatura, "RSA");
	}

	static Certificado certificado(boolean permiteAssinatura, String algoritmoTitular) throws Exception {
		KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
		gerador.initialize(2048);
		KeyPair chavesAutoridade = gerador.generateKeyPair();
		KeyPair chavesTitular;
		if ("EC".equals(algoritmoTitular)) {
			var geradorEc = KeyPairGenerator.getInstance("EC");
			geradorEc.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
			chavesTitular = geradorEc.generateKeyPair();
		} else chavesTitular = gerador.generateKeyPair();
		X500Name nomeAutoridade = new X500Name("CN=Autoridade de testes aFIRMA");
		X500Name nomeTitular = new X500Name("CN=Empresa de testes aFIRMA");
		Date inicio = Date.from(Instant.now().minusSeconds(60));
		Date fim = Date.from(Instant.now().plusSeconds(86400));
		var construtorRaiz = new JcaX509v3CertificateBuilder(nomeAutoridade, BigInteger.ONE,
				inicio, fim, nomeAutoridade, chavesAutoridade.getPublic());
		construtorRaiz.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
		construtorRaiz.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
		var assinadorRaiz = new JcaContentSignerBuilder("SHA256withRSA").build(chavesAutoridade.getPrivate());
		var conversor = new JcaX509CertificateConverter();
		X509Certificate raiz = conversor.getCertificate(construtorRaiz.build(assinadorRaiz));
		var construtorTitular = new JcaX509v3CertificateBuilder(nomeAutoridade, BigInteger.TWO,
				inicio, fim, nomeTitular, chavesTitular.getPublic());
		construtorTitular.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
		construtorTitular.addExtension(Extension.keyUsage, true, new KeyUsage(
				permiteAssinatura ? KeyUsage.digitalSignature : KeyUsage.keyEncipherment));
		X509Certificate titular = conversor.getCertificate(construtorTitular.build(assinadorRaiz));
		KeyStore armazenamento = KeyStore.getInstance("PKCS12");
		armazenamento.load(null, null);
		armazenamento.setKeyEntry("empresa", chavesTitular.getPrivate(), "senha-teste".toCharArray(),
				new Certificate[] {titular, raiz});
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		armazenamento.store(saida, "senha-teste".toCharArray());
		return new Certificado(saida.toByteArray(), titular, raiz, chavesTitular, chavesAutoridade);
	}

	record Certificado(byte[] arquivo, X509Certificate titular, X509Certificate raiz,
			KeyPair chavesTitular, KeyPair chavesAutoridade) { }
}
