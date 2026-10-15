package com.afirma.assina;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfDate;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfPKCS7;
import org.openpdf.text.pdf.PdfSignature;
import org.openpdf.text.pdf.PdfSignatureAppearance;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfString;
import org.springframework.stereotype.Service;

@Service
public class ServicoAssinaturaPdf {

	public byte[] assinar(byte[] pdf, byte[] certificado, char[] senha) throws Exception {
		if (pdf == null || pdf.length == 0 || certificado == null || certificado.length == 0) {
			throw new IllegalArgumentException("PDF e certificado são obrigatórios");
		}

		KeyStore armazenamento = KeyStore.getInstance("PKCS12");
		armazenamento.load(new ByteArrayInputStream(certificado), senha);

		String apelido = null;
		Enumeration<String> apelidos = armazenamento.aliases();
		while (apelidos.hasMoreElements()) {
			String candidato = apelidos.nextElement();
			if (armazenamento.isKeyEntry(candidato)) {
				apelido = candidato;
				break;
			}
		}
		if (apelido == null) {
			throw new IllegalArgumentException("Certificado sem chave privada");
		}

		PrivateKey chave = (PrivateKey) armazenamento.getKey(apelido, senha);
		Certificate[] cadeia = armazenamento.getCertificateChain(apelido);
		if (chave == null || cadeia == null || cadeia.length == 0) {
			throw new IllegalArgumentException("Certificado sem chave privada ou cadeia de certificados");
		}
		if (!(cadeia[0] instanceof X509Certificate folha)) {
			throw new IllegalArgumentException("Certificado X.509 inválido");
		}
		folha.checkValidity();

		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		PdfReader leitor = new PdfReader(pdf);
		try {
			PdfStamper assinador = PdfStamper.createSignature(leitor, saida, null, null, true);
			PdfSignatureAppearance aparencia = assinador.getSignatureAppearance();
			aparencia.setCrypto(chave, cadeia, null, PdfSignatureAppearance.WINCER_SIGNED);
			PdfSignature dicionario = new PdfSignature(PdfName.ADOBE_PPKLITE, new PdfName("adbe.pkcs7.detached"));
			dicionario.setDate(new PdfDate(aparencia.getSignDateNullSafe()));
			aparencia.setCryptoDictionary(dicionario);

			int tamanhoReservado = 16384;
			Map<PdfName, Integer> espaco = new HashMap<>();
			espaco.put(PdfName.CONTENTS, tamanhoReservado * 2 + 2);
			aparencia.preClose(espaco);

			PdfPKCS7 assinatura = new PdfPKCS7(chave, cadeia, null, "SHA256", null, false);
			MessageDigest digestor = MessageDigest.getInstance("SHA-256");
			try (var dados = aparencia.getRangeStream()) {
				byte[] bloco = new byte[8192];
				int lidos;
				while ((lidos = dados.read(bloco)) != -1) {
					digestor.update(bloco, 0, lidos);
				}
			}
			byte[] resumo = digestor.digest();
			Calendar data = Calendar.getInstance();
			byte[] atributos = assinatura.getAuthenticatedAttributeBytes(resumo, data, null);
			assinatura.update(atributos, 0, atributos.length);
			byte[] conteudo = assinatura.getEncodedPKCS7(resumo, data, null, null);
			if (conteudo.length > tamanhoReservado) {
				throw new IllegalStateException("Assinatura maior que espaço reservado no PDF");
			}
			byte[] conteudoReservado = new byte[tamanhoReservado];
			System.arraycopy(conteudo, 0, conteudoReservado, 0, conteudo.length);
			PdfDictionary fechamento = new PdfDictionary();
			fechamento.put(PdfName.CONTENTS, new PdfString(conteudoReservado).setHexWriting(true));
			aparencia.close(fechamento);
			return saida.toByteArray();
		} finally {
			leitor.close();
		}
	}
}
