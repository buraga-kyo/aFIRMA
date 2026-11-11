package com.afirma.assina;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.openpdf.text.pdf.PdfReader;
import org.springframework.stereotype.Service;

@Service
public class VerificadorAssinaturasPdf {
	private final PoliticaCertificados politica;
	private final CarimboTempo carimboTempo;

	public VerificadorAssinaturasPdf(PoliticaCertificados politica, CarimboTempo carimboTempo) {
		this.politica = politica;
		this.carimboTempo = carimboTempo;
	}

	public Resultado verificar(byte[] pdf) throws Exception {
		List<Assinatura> assinaturas = new ArrayList<>();
		try (PdfReader leitor = new PdfReader(pdf)) {
			var campos = leitor.getAcroFields();
			for (String nomeCampo : campos.getSignedFieldNames()) {
				try {
					var assinatura = campos.verifySignature(nomeCampo);
					boolean integra = assinatura.verify();
					var dicionario = campos.getSignatureDictionary(nomeCampo);
					boolean cades = new org.openpdf.text.pdf.PdfName("ETSI.CAdES.detached").equals(
							dicionario.getAsName(org.openpdf.text.pdf.PdfName.SUBFILTER));
					if (cades && !conferirReferenciaCertificado(dicionario, assinatura.getSigningCertificate())) integra = false;
					var certificado = assinatura.getSigningCertificate();
					var confianca = politica.avaliar(assinatura.getSignCertificateChain());
					String estadoCarimbo = "AUSENTE";
					Instant horarioCarimbo = null;
					if (assinatura.getTimeStampToken() != null) {
						horarioCarimbo = assinatura.getTimeStampToken().getTimeStampInfo().getGenTime().toInstant();
						if (!assinatura.verifyTimestampImprint()) estadoCarimbo = "INVALIDO";
						else {
							try {
								carimboTempo.validar(assinatura.getTimeStampToken());
								estadoCarimbo = "CONFIAVEL";
							} catch (Exception erro) { estadoCarimbo = "CONFIANCA_NAO_CONFIRMADA"; }
						}
					}
					assinaturas.add(new Assinatura(nomeCampo, certificado.getSubjectX500Principal().getName(),
							certificado.getIssuerX500Principal().getName(), integra,
							campos.signatureCoversWholeDocument(nomeCampo), campos.getRevision(nomeCampo),
							certificado.getNotAfter().toInstant(), confianca.confianca(), confianca.revogacao(),
							estadoCarimbo, horarioCarimbo));
				} catch (Exception erro) {
					assinaturas.add(new Assinatura(nomeCampo, "Não identificado", "Não identificado", false,
							false, campos.getRevision(nomeCampo), null, "NAO_VERIFICADA", "NAO_CONSULTADA", "NAO_VERIFICADO", null));
				}
			}
		}
		boolean integridadeConfirmada = !assinaturas.isEmpty() && assinaturas.stream().allMatch(Assinatura::integra)
				&& assinaturas.stream().anyMatch(Assinatura::cobreDocumentoInteiro);
		assinaturas.sort(java.util.Comparator.comparingInt(Assinatura::revisao));
		return new Resultado(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)),
				integridadeConfirmada, assinaturas);
	}

	public void validarAntesDeAlterar(byte[] pdf) throws Exception {
		try (PdfReader leitor = new PdfReader(pdf)) {
			validarRecursosDoDocumento(leitor);
			if (leitor.getCertificationLevel() > 0) {
				throw new ErroAssinatura("PDF certificado possui restrições de alteração; use um documento sem certificação prévia");
			}
			for (String nomeCampo : leitor.getAcroFields().getSignedFieldNames()) {
				try {
					if (leitor.getAcroFields().verifySignature(nomeCampo).verify()) continue;
				} catch (Exception erro) {
					throw new ErroAssinatura("PDF contém assinatura anterior inválida", erro);
				}
				throw new ErroAssinatura("PDF contém assinatura anterior inválida");
			}
		}
	}

	private boolean conferirReferenciaCertificado(org.openpdf.text.pdf.PdfDictionary dicionario,
			java.security.cert.X509Certificate certificado) throws Exception {
		byte[] codificado = dicionario.getAsString(org.openpdf.text.pdf.PdfName.CONTENTS).getOriginalBytes();
		try (var entrada = new org.bouncycastle.asn1.ASN1InputStream(codificado)) {
			var cms = new org.bouncycastle.cms.CMSSignedData(org.bouncycastle.asn1.cms.ContentInfo.getInstance(entrada.readObject()));
			var identificacao = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(certificado);
			for (var signatario : cms.getSignerInfos().getSigners()) {
				if (!signatario.getSID().match(identificacao)) continue;
				var atributos = signatario.getSignedAttributes();
				var identificador = org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.id_aa_signingCertificateV2;
				if (atributos == null || atributos.getAll(identificador).size() != 1) return false;
				var referencias = org.bouncycastle.asn1.ess.SigningCertificateV2.getInstance(
						atributos.get(identificador).getAttrValues().getObjectAt(0)).getCerts();
				if (referencias.length == 0) return false;
				var calculador = new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().build().get(referencias[0].getHashAlgorithm());
				calculador.getOutputStream().write(certificado.getEncoded());
				return MessageDigest.isEqual(calculador.getDigest(), referencias[0].getCertHash());
			}
			return false;
		}
	}

	private void validarRecursosDoDocumento(PdfReader leitor) {
		if (leitor.isEncrypted()) throw new ErroAssinatura("Remova a criptografia do PDF antes de preparar a assinatura");
		if (leitor.getXrefSize() > 150000) throw new ErroAssinatura("PDF possui estrutura muito complexa. Gere uma versão simplificada");
		java.util.Set<org.openpdf.text.pdf.PdfName> acoesProibidas = java.util.Set.of(
				org.openpdf.text.pdf.PdfName.JAVASCRIPT, org.openpdf.text.pdf.PdfName.LAUNCH,
				org.openpdf.text.pdf.PdfName.SUBMITFORM, org.openpdf.text.pdf.PdfName.IMPORTDATA,
				new org.openpdf.text.pdf.PdfName("GoToR"), new org.openpdf.text.pdf.PdfName("GoToE"), new org.openpdf.text.pdf.PdfName("Rendition"));
		for (int indice = 0; indice < leitor.getXrefSize(); indice++) {
			var objeto = leitor.getPdfObjectRelease(indice);
			if (!(objeto instanceof org.openpdf.text.pdf.PdfDictionary dicionario)) continue;
			if (dicionario.contains(org.openpdf.text.pdf.PdfName.JS) || dicionario.contains(org.openpdf.text.pdf.PdfName.XFA)
					|| org.openpdf.text.pdf.PdfName.EMBEDDEDFILE.equals(dicionario.getAsName(org.openpdf.text.pdf.PdfName.TYPE))
					|| dicionario.getAsName(org.openpdf.text.pdf.PdfName.S) != null
							&& acoesProibidas.contains(dicionario.getAsName(org.openpdf.text.pdf.PdfName.S))) {
				throw new ErroAssinatura("PDF possui ações ativas, formulário dinâmico ou arquivos anexados. Gere uma versão estática antes de assinar");
			}
		}
	}

	public record Resultado(String resumoDocumento, boolean integridadeConfirmada, List<Assinatura> assinaturas) { }
	public record Assinatura(String campo, String titular, String emissor, boolean integra,
			boolean cobreDocumentoInteiro, int revisao, Instant certificadoValidoAte, String confianca,
			String revogacao, String carimboTempo, Instant horarioCarimbo) { }
}
