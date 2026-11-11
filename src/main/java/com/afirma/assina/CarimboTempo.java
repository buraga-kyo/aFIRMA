package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.time.Duration;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.openpdf.text.pdf.PdfPKCS7;
import org.openpdf.text.pdf.TSAClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CarimboTempo implements TSAClient {
	private final URI endereco;
	private final PoliticaCertificados autoridades;
	private final HttpClient cliente;

	@org.springframework.beans.factory.annotation.Autowired
	public CarimboTempo(@Value("${ASSINEAI_TSA_URL:}") String endereco,
			@Value("${ASSINEAI_TSA_RAIZES_PEM:}") String arquivoRaizes) {
		this(endereco, arquivoRaizes, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER).build());
	}

	CarimboTempo(String endereco, String arquivoRaizes, HttpClient cliente) {
		this.cliente = cliente;
		this.endereco = endereco.isBlank() ? null : URI.create(endereco);
		this.autoridades = new PoliticaCertificados(arquivoRaizes, "");
		if (this.endereco != null && (!"https".equalsIgnoreCase(this.endereco.getScheme()) || arquivoRaizes.isBlank())) {
			throw new IllegalArgumentException("Carimbo do tempo exige HTTPS e raízes da autoridade configuradas");
		}
	}

	public boolean ativo() { return endereco != null; }
	@Override public int getTokenSizeEstimate() { return 32768; }
	@Override public MessageDigest getMessageDigest() throws GeneralSecurityException {
		return MessageDigest.getInstance("SHA-256");
	}

	@Override
	public byte[] getTimeStampToken(PdfPKCS7 assinatura, byte[] resumoAssinatura) {
		CompletableFuture<HttpResponse<byte[]>> respostaPendente = null;
		try {
			TimeStampRequestGenerator gerador = new TimeStampRequestGenerator();
			gerador.setCertReq(true);
			var pedido = gerador.generate(TSPAlgorithms.SHA256, resumoAssinatura,
					new BigInteger(160, new SecureRandom()));
			HttpRequest requisicao = HttpRequest.newBuilder(endereco).timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/timestamp-query")
					.header("Accept", "application/timestamp-reply")
					.POST(HttpRequest.BodyPublishers.ofByteArray(pedido.getEncoded())).build();
			respostaPendente = cliente.sendAsync(requisicao, informacoes -> new RecebedorCarimbo());
			var resposta = respostaPendente.get(15, TimeUnit.SECONDS);
			if (resposta.statusCode() != 200) throw new IllegalStateException("Autoridade indisponível");
			byte[] conteudo = resposta.body();
			if (conteudo.length > 65536) throw new IllegalStateException("Carimbo acima do limite");
			TimeStampResponse carimbo = new TimeStampResponse(conteudo);
			carimbo.validate(pedido);
			if (carimbo.getTimeStampToken() == null) throw new IllegalStateException("Carimbo recusado");
			validar(carimbo.getTimeStampToken());
			long diferenca = Math.abs(Duration.between(carimbo.getTimeStampToken().getTimeStampInfo().getGenTime().toInstant(),
					Instant.now()).toSeconds());
			if (diferenca > 300) throw new IllegalArgumentException("Horário do carimbo fora da tolerância");
			return carimbo.getTimeStampToken().getEncoded();
		} catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
			throw indisponivel();
		} catch (Exception erro) {
			// Se o carimbo foi solicitado, não devolve silenciosamente um PDF sem ele.
			throw indisponivel();
		} finally {
			if (respostaPendente != null && !respostaPendente.isDone()) respostaPendente.cancel(true);
		}
	}

	private static class RecebedorCarimbo implements HttpResponse.BodySubscriber<byte[]> {
		private final CompletableFuture<byte[]> resultado = new CompletableFuture<>();
		private final ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
		private Flow.Subscription transmissao;

		@Override public CompletionStage<byte[]> getBody() { return resultado; }
		@Override public void onSubscribe(Flow.Subscription transmissao) {
			this.transmissao = transmissao;
			transmissao.request(1);
		}
		@Override public void onNext(List<ByteBuffer> blocos) {
			for (var bloco : blocos) {
				if (conteudo.size() + bloco.remaining() > 65536) {
					transmissao.cancel();
					resultado.completeExceptionally(new IOException("Carimbo acima do limite"));
					return;
				}
				byte[] bytes = new byte[bloco.remaining()];
				bloco.get(bytes);
				conteudo.writeBytes(bytes);
			}
			transmissao.request(1);
		}
		@Override public void onError(Throwable erro) { resultado.completeExceptionally(erro); }
		@Override public void onComplete() { resultado.complete(conteudo.toByteArray()); }
	}

	public void validar(TimeStampToken carimbo) throws Exception {
		var correspondentes = carimbo.getCertificates().getMatches(carimbo.getSID());
		if (correspondentes.size() != 1) throw new IllegalArgumentException("Certificado da autoridade ausente ou ambíguo");
		X509CertificateHolder titular = (X509CertificateHolder) correspondentes.iterator().next();
		carimbo.validate(new JcaSimpleSignerInfoVerifierBuilder().build(titular));
		var conversor = new JcaX509CertificateConverter();
		List<Certificate> cadeia = new ArrayList<>();
		cadeia.add(conversor.getCertificate(titular));
		for (Object item : carimbo.getCertificates().getMatches(null)) {
			X509CertificateHolder certificado = (X509CertificateHolder) item;
			if (!certificado.equals(titular)) cadeia.add(conversor.getCertificate(certificado));
		}
		autoridades.validarCadeia(cadeia.toArray(Certificate[]::new), false);
	}

	private ResponseStatusException indisponivel() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Não foi possível obter um carimbo do tempo confiável. Tente novamente");
	}
}
