package com.afirma.assina;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXParameters;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PoliticaCertificados {
	private final Set<TrustAnchor> raizes;
	private final URI enderecoOcsp;

	public PoliticaCertificados(@Value("${ASSINEAI_RAIZES_CONFIAVEIS_PEM:}") String arquivoRaizes,
			@Value("${ASSINEAI_OCSP_URL:}") String enderecoOcsp) {
		this.raizes = carregarRaizes(arquivoRaizes);
		this.enderecoOcsp = enderecoOcsp.isBlank() ? null : URI.create(enderecoOcsp);
		if (this.enderecoOcsp != null && (!"https".equalsIgnoreCase(this.enderecoOcsp.getScheme()) || raizes.isEmpty())) {
			throw new IllegalArgumentException("OCSP exige HTTPS e raízes confiáveis configuradas");
		}
	}

	public boolean raizesConfiguradas() { return !raizes.isEmpty(); }
	public boolean consultaRevogacao() { return enderecoOcsp != null; }

	public Resultado avaliar(Certificate[] cadeia) {
		if (raizes.isEmpty()) return new Resultado("NAO_VERIFICADA", "NAO_CONSULTADA");
		try {
			validarCadeia(cadeia, false);
		} catch (Exception erro) {
			return new Resultado("NAO_CONFIAVEL", "NAO_CONSULTADA");
		}
		if (enderecoOcsp == null) return new Resultado("CONFIAVEL", "NAO_CONSULTADA");
		try {
			// Só consulta o serviço configurado depois de validar a cadeia, sem confiar em URLs do arquivo enviado.
			validarCadeia(cadeia, true);
			return new Resultado("CONFIAVEL", "CONSULTADA_SEM_REVOGACAO");
		} catch (CertPathValidatorException erro) {
			return new Resultado("CONFIAVEL", erro.getReason() == CertPathValidatorException.BasicReason.REVOKED
					? "REVOGADO" : "CONSULTA_INDISPONIVEL");
		} catch (Exception erro) {
			return new Resultado("CONFIAVEL", "CONSULTA_INDISPONIVEL");
		}
	}

	public Resultado exigirParaAssinar(Certificate[] cadeia) {
		Resultado resultado = avaliar(cadeia);
		if ("NAO_CONFIAVEL".equals(resultado.confianca())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificado sem cadeia confiável nas raízes configuradas");
		}
		if ("REVOGADO".equals(resultado.revogacao()) || "CONSULTA_INDISPONIVEL".equals(resultado.revogacao())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Não foi possível confirmar que o certificado está livre de revogação");
		}
		return resultado;
	}

	public void validarCadeia(Certificate[] cadeia, boolean consultarRevogacao) throws Exception {
		if (raizes.isEmpty()) throw new IllegalStateException("Raízes confiáveis não configuradas");
		X509CertSelector seletor = new X509CertSelector();
		seletor.setCertificate((X509Certificate) cadeia[0]);
		PKIXBuilderParameters parametros = new PKIXBuilderParameters(raizes, seletor);
		parametros.setRevocationEnabled(false);
		parametros.addCertStore(CertStore.getInstance("Collection",
				new CollectionCertStoreParameters(Arrays.asList(cadeia))));
		CertPathBuilder construtor = CertPathBuilder.getInstance("PKIX");
		PKIXCertPathBuilderResult caminho = (PKIXCertPathBuilderResult) construtor.build(parametros);
		if (consultarRevogacao) {
			CertPathValidator validador = CertPathValidator.getInstance("PKIX");
			PKIXParameters parametrosRevogacao = new PKIXParameters(raizes);
			parametrosRevogacao.setRevocationEnabled(false);
			PKIXRevocationChecker verificador = (PKIXRevocationChecker) validador.getRevocationChecker();
			verificador.setOcspResponder(enderecoOcsp);
			verificador.setOptions(Set.of(PKIXRevocationChecker.Option.NO_FALLBACK));
			parametrosRevogacao.addCertPathChecker(verificador);
			validador.validate(caminho.getCertPath(), parametrosRevogacao);
		}
	}

	private Set<TrustAnchor> carregarRaizes(String arquivo) {
		if (arquivo.isBlank()) return Set.of();
		try (InputStream entrada = Files.newInputStream(Path.of(arquivo))) {
			Set<TrustAnchor> autoridades = new HashSet<>();
			for (Certificate certificado : CertificateFactory.getInstance("X.509").generateCertificates(entrada)) {
				X509Certificate autoridade = (X509Certificate) certificado;
				if (autoridade.getBasicConstraints() < 0) throw new IllegalArgumentException("Raiz precisa ser uma autoridade certificadora");
				autoridades.add(new TrustAnchor(autoridade, null));
			}
			if (autoridades.isEmpty()) throw new IllegalArgumentException("Arquivo de raízes vazio");
			return Set.copyOf(autoridades);
		} catch (Exception erro) {
			throw new IllegalArgumentException("Não foi possível carregar as raízes confiáveis", erro);
		}
	}

	public record Resultado(String confianca, String revogacao) { }
}
