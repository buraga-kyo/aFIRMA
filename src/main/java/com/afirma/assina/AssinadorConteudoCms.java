package com.afirma.assina;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Hashtable;
import java.util.List;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public class AssinadorConteudoCms {
	public static String algoritmo(PrivateKey chave) {
		return switch (chave.getAlgorithm()) {
			case "RSA" -> "SHA256withRSA";
			case "EC" -> "SHA256withECDSA";
			default -> throw new ErroAssinatura("Algoritmo do certificado não atendido. Use certificado RSA ou EC");
		};
	}

	public byte[] assinar(InputStream entrada, PrivateKey chave, Certificate[] cadeia, CarimboTempo carimbo) throws Exception {
		String algoritmo = algoritmo(chave);
		var certificado = (X509Certificate) cadeia[0];
		var referencia = new SigningCertificateV2(new ESSCertIDv2(MessageDigest.getInstance("SHA-256").digest(certificado.getEncoded())));
		Hashtable<ASN1ObjectIdentifier, Attribute> atributos = new Hashtable<>();
		atributos.put(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
				new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(referencia)));
		var geradorPadrao = new DefaultSignedAttributeTableGenerator(new AttributeTable(atributos));
		var informacoes = new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
				.setSignedAttributeGenerator(parametros -> geradorPadrao.getAttributes(parametros).remove(CMSAttributes.signingTime))
				.build(new JcaContentSignerBuilder(algoritmo).build(chave), certificado);
		CMSSignedDataGenerator gerador = new CMSSignedDataGenerator();
		gerador.addSignerInfoGenerator(informacoes);
		gerador.addCertificates(new JcaCertStore(List.of(cadeia)));
		CMSTypedData conteudo = new CMSTypedData() {
			@Override public ASN1ObjectIdentifier getContentType() { return PKCSObjectIdentifiers.data; }
			@Override public Object getContent() { return entrada; }
			@Override public void write(OutputStream destino) throws java.io.IOException { entrada.transferTo(destino); }
		};
		CMSSignedData assinatura = gerador.generate(conteudo, false);
		if (carimbo.ativo()) {
			SignerInformation signatario = assinatura.getSignerInfos().getSigners().iterator().next();
			byte[] token = carimbo.getTimeStampToken(null, MessageDigest.getInstance("SHA-256").digest(signatario.getSignature()));
			if (token == null) throw new IllegalStateException("Carimbo do tempo ausente");
			Attribute atributoCarimbo = new Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
					new DERSet(ContentInfo.getInstance(ASN1Primitive.fromByteArray(token))));
			SignerInformation atualizado = SignerInformation.replaceUnsignedAttributes(signatario,
					new AttributeTable(atributoCarimbo));
			assinatura = CMSSignedData.replaceSigners(assinatura, new SignerInformationStore(atualizado));
		}
		return assinatura.getEncoded();
	}
}
