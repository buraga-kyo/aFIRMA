package com.afirma.assina;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfDate;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfSignature;
import org.openpdf.text.pdf.PdfSignatureAppearance;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfString;
import org.springframework.stereotype.Service;

@Service
public class ServicoAssinaturaPdf {
	private final PoliticaCertificados politica;
	private final CarimboTempo carimboTempo;
	private final VerificadorAssinaturasPdf verificador;

	public ServicoAssinaturaPdf() {
		this(new PoliticaCertificados("", ""), new CarimboTempo("", ""));
	}

	@org.springframework.beans.factory.annotation.Autowired
	public ServicoAssinaturaPdf(PoliticaCertificados politica, CarimboTempo carimboTempo) {
		this.politica = politica;
		this.carimboTempo = carimboTempo;
		this.verificador = new VerificadorAssinaturasPdf(politica, carimboTempo);
	}

	public VerificadorAssinaturasPdf.Resultado verificarDocumento(byte[] pdf) throws Exception {
		return verificador.verificar(pdf);
	}

	public void validarDocumentoAntesDeAlterar(byte[] pdf) throws Exception {
		try { verificador.validarAntesDeAlterar(pdf); }
		catch (java.io.IOException erro) { throw new ErroAssinatura("PDF inválido ou protegido por senha", erro); }
	}

	public DadosCertificado consultarCertificado(byte[] certificado, char[] senha) throws Exception {
		return consultarCertificado(certificado, senha, false);
	}

	public DadosCertificado consultarCertificado(byte[] certificado, char[] senha,
			boolean permitirCertificadoVencido) throws Exception {
		MaterialAssinatura material = carregarCertificado(certificado, senha, permitirCertificadoVencido);
		var folha = (X509Certificate) material.cadeia()[0];
		var resultado = politica.exigirParaAssinar(material.cadeia());
		return new DadosCertificado(folha.getSubjectX500Principal().getName(), folha.getIssuerX500Principal().getName(),
				folha.getSerialNumber().toString(16), folha.getNotBefore().toInstant(), folha.getNotAfter().toInstant(),
				resultado.confianca(), resultado.revogacao(), carimboTempo.ativo());
	}

	public record DadosCertificado(String titular, String emissor, String numeroSerie,
			java.time.Instant validoDesde, java.time.Instant validoAte, String confianca,
			String revogacao, boolean carimboTempoConfigurado) { }
	private record MaterialAssinatura(PrivateKey chave, Certificate[] cadeia) { }

	private MaterialAssinatura carregarCertificado(byte[] certificado, char[] senha,
			boolean permitirCertificadoVencido) throws Exception {

		KeyStore armazenamento = KeyStore.getInstance("PKCS12");
		try {
			armazenamento.load(new ByteArrayInputStream(certificado), senha);
		} catch (java.io.IOException erro) {
			if (erro.getCause() instanceof UnrecoverableKeyException) {
				throw new UnrecoverableKeyException("Senha do certificado incorreta");
			}
			throw new ErroAssinatura("Arquivo do certificado inválido", erro);
		}

		String apelido = null;
		Enumeration<String> apelidos = armazenamento.aliases();
		while (apelidos.hasMoreElements()) {
			String candidato = apelidos.nextElement();
			if (armazenamento.isKeyEntry(candidato)) {
				if (apelido != null) throw new ErroAssinatura("Certificado possui mais de uma chave privada; envie um arquivo com um único titular");
				apelido = candidato;
			}
		}
		if (apelido == null) {
			throw new ErroAssinatura("Certificado sem chave privada");
		}

		var chaveCarregada = armazenamento.getKey(apelido, senha);
		if (!(chaveCarregada instanceof PrivateKey chave)) throw new ErroAssinatura("Certificado sem chave privada");
		Certificate[] cadeia = armazenamento.getCertificateChain(apelido);
		if (chave == null || cadeia == null || cadeia.length == 0) {
			throw new ErroAssinatura("Certificado sem chave privada ou cadeia de certificados");
		}
		if (!(cadeia[0] instanceof X509Certificate folha)) {
			throw new ErroAssinatura("Certificado X.509 inválido");
		}
		try {
			folha.checkValidity();
		} catch (CertificateExpiredException erro) {
			if (!permitirCertificadoVencido) {
				throw erro;
			}
		}
		boolean[] usos = folha.getKeyUsage();
		if (usos != null && !usos[0] && (usos.length < 2 || !usos[1])) {
			throw new ErroAssinatura("Certificado não permite assinatura digital");
		}
		if (chave instanceof java.security.interfaces.RSAPrivateKey rsa && rsa.getModulus().bitLength() < 2048
				|| chave instanceof java.security.interfaces.ECPrivateKey ec && ec.getParams().getCurve().getField().getFieldSize() < 256) {
			throw new ErroAssinatura("Certificado possui chave abaixo do tamanho mínimo atendido");
		}
		var conferenciaChave = java.security.Signature.getInstance(AssinadorConteudoCms.algoritmo(chave));
		byte[] desafio = new byte[32];
		new java.security.SecureRandom().nextBytes(desafio);
		conferenciaChave.initSign(chave); conferenciaChave.update(desafio);
		byte[] prova = conferenciaChave.sign();
		conferenciaChave.initVerify(folha); conferenciaChave.update(desafio);
		if (!conferenciaChave.verify(prova)) throw new ErroAssinatura("Chave privada não corresponde ao certificado");
		return new MaterialAssinatura(chave, cadeia);
	}

	public byte[] assinarManifesto(byte[] manifesto, byte[] certificado, char[] senha, boolean permitirVencido) throws Exception {
		MaterialAssinatura material = carregarCertificado(certificado, senha, permitirVencido);
		politica.exigirParaAssinar(material.cadeia());
		try (var entrada = new ByteArrayInputStream(manifesto)) {
			return new AssinadorConteudoCms().assinar(entrada, material.chave(), material.cadeia(), carimboTempo);
		}
	}

	public byte[] assinar(byte[] pdf, byte[] certificado, char[] senha,
			boolean permitirCertificadoVencido) throws Exception {
		return assinar(pdf, certificado, senha, permitirCertificadoVencido, false, null, null);
	}

	public byte[] assinar(byte[] pdf, byte[] certificado, char[] senha,
			boolean permitirCertificadoVencido, boolean paginaPersonalizada,
			String nomeEmpresa, byte[] imagemEmpresa) throws Exception {
		return assinar(pdf, certificado, senha, permitirCertificadoVencido, paginaPersonalizada,
				nomeEmpresa, imagemEmpresa, TemplatesEmpresa.padrao());
	}

	public byte[] assinar(byte[] pdf, byte[] certificado, char[] senha,
			boolean permitirCertificadoVencido, boolean paginaPersonalizada,
			String nomeEmpresa, byte[] imagemEmpresa, Map<String, Object> modelo) throws Exception {
		if (pdf == null || pdf.length == 0 || certificado == null || certificado.length == 0) {
			throw new ErroAssinatura("PDF e certificado são obrigatórios");
		}

		validarDocumentoAntesDeAlterar(pdf);
		MaterialAssinatura material = carregarCertificado(certificado, senha, permitirCertificadoVencido);
		PrivateKey chave = material.chave();
		Certificate[] cadeia = material.cadeia();
		X509Certificate folha = (X509Certificate) cadeia[0];
		politica.exigirParaAssinar(cadeia);
		if (paginaPersonalizada) {
			pdf = new PaginaComprovante().acrescentar(pdf, nomeEmpresa, imagemEmpresa,
					"Certificado digital", folha.getSubjectX500Principal().getName(), modelo);
		}

		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		PdfReader leitor;
		try {
			leitor = new PdfReader(pdf);
		} catch (java.io.IOException erro) {
			throw new ErroAssinatura("PDF inválido ou protegido por senha", erro);
		}
		try {
			var assinaturasAnteriores = java.util.List.copyOf(leitor.getAcroFields().getSignedFieldNames());
			PdfStamper assinador = PdfStamper.createSignature(leitor, saida, null, null, true);
			PdfSignatureAppearance aparencia = assinador.getSignatureAppearance();
			aparencia.setCrypto(chave, cadeia, null, PdfSignatureAppearance.WINCER_SIGNED);
			PdfSignature dicionario = new PdfSignature(PdfName.ADOBE_PPKLITE, new PdfName("ETSI.CAdES.detached"));
			dicionario.setDate(new PdfDate(aparencia.getSignDateNullSafe()));
			aparencia.setCryptoDictionary(dicionario);

			int tamanhoReservado = carimboTempo.ativo() ? 98304 : 16384;
			Map<PdfName, Integer> espaco = new HashMap<>();
			espaco.put(PdfName.CONTENTS, tamanhoReservado * 2 + 2);
			aparencia.preClose(espaco);

			byte[] conteudo;
			try (var dados = aparencia.getRangeStream()) {
				conteudo = new AssinadorConteudoCms().assinar(dados, chave, cadeia, carimboTempo);
			}
			if (conteudo.length > tamanhoReservado) {
				throw new IllegalStateException("Assinatura maior que espaço reservado no PDF");
			}
			byte[] conteudoReservado = new byte[tamanhoReservado];
			System.arraycopy(conteudo, 0, conteudoReservado, 0, conteudo.length);
			PdfDictionary fechamento = new PdfDictionary();
			fechamento.put(PdfName.CONTENTS, new PdfString(conteudoReservado).setHexWriting(true));
			aparencia.close(fechamento);
			byte[] documentoAssinado = saida.toByteArray();
			if (documentoAssinado.length > 50 * 1024 * 1024) throw new ErroAssinatura("PDF resultante excede 50 MB. Reduza o tamanho das imagens");
			// Só entrega o PDF depois de conferir o selo novo e todas as assinaturas anteriores.
			try (PdfReader conferencia = new PdfReader(documentoAssinado)) {
				var campos = conferencia.getAcroFields();
				var nomes = campos.getSignedFieldNames();
				if (nomes.size() != assinaturasAnteriores.size() + 1 || !nomes.containsAll(assinaturasAnteriores)
						|| nomes.stream().filter(nomeCampo -> !assinaturasAnteriores.contains(nomeCampo))
								.noneMatch(campos::signatureCoversWholeDocument)) {
					throw new IllegalStateException("Assinatura final não cobre o documento completo");
				}
				for (String nomeCampo : nomes) {
					var assinaturaConferida = campos.verifySignature(nomeCampo);
					if (!assinaturaConferida.verify()) {
						throw new IllegalStateException("Falha na conferência da assinatura gerada");
					}
					if (carimboTempo.ativo() && !assinaturasAnteriores.contains(nomeCampo)) {
						if (assinaturaConferida.getTimeStampToken() == null || !assinaturaConferida.verifyTimestampImprint()) {
							throw new IllegalStateException("Carimbo do tempo não corresponde à assinatura gerada");
						}
						carimboTempo.validar(assinaturaConferida.getTimeStampToken());
					}
				}
			}
			return documentoAssinado;
		} finally {
			leitor.close();
		}
	}
}
