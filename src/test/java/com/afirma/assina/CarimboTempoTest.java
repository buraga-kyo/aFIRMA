package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarimboTempoTest {
	@TempDir Path diretorio;
	private ArquivosAssinaturaTeste.Certificado material;
	private String arquivoRaizes;

	@BeforeEach void prepararAutoridade() throws Exception {
		material = ArquivosAssinaturaTeste.certificado(true);
		Path arquivo = diretorio.resolve("raizes.pem");
		Files.writeString(arquivo, "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[] {'\n'})
				.encodeToString(material.raiz().getEncoded()) + "\n-----END CERTIFICATE-----\n");
		arquivoRaizes = arquivo.toString();
	}

	@Test void incluiCarimboConferidoNaAssinaturaFinal() throws Exception {
		HttpClient cliente = clienteComResposta(false, false);
		CarimboTempo carimbo = new CarimboTempo("https://tsa.exemplo.test", arquivoRaizes, cliente);
		ServicoAssinaturaPdf assinador = new ServicoAssinaturaPdf(new PoliticaCertificados(arquivoRaizes, ""), carimbo);
		byte[] pdf = assinador.assinar(ArquivosAssinaturaTeste.pdf(), material.arquivo(), "senha-teste".toCharArray(), false);
		try (PdfReader leitor = new PdfReader(pdf)) {
			var campos = leitor.getAcroFields();
			var assinatura = campos.verifySignature(campos.getSignedFieldNames().getFirst());
			assertTrue(assinatura.verify());
			assertTrue(assinatura.verifyTimestampImprint());
			carimbo.validar(assinatura.getTimeStampToken());
		}
		var resultado = assinador.verificarDocumento(pdf);
		assertTrue(resultado.integridadeConfirmada());
		assertEquals("CONFIAVEL", resultado.assinaturas().getFirst().carimboTempo());
		assertNotNull(resultado.assinaturas().getFirst().horarioCarimbo());
	}

	@Test void recusaRespostaParaOutroResumo() throws Exception {
		CarimboTempo carimbo = new CarimboTempo("https://tsa.exemplo.test", arquivoRaizes, clienteComResposta(true, false));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
				() -> carimbo.getTimeStampToken(null, new byte[32])).getStatusCode());
	}

	@Test void recusaCarimboForaDoHorarioEsperado() throws Exception {
		CarimboTempo carimbo = new CarimboTempo("https://tsa.exemplo.test", arquivoRaizes, clienteComResposta(false, true));
		assertThrows(ResponseStatusException.class, () -> carimbo.getTimeStampToken(null, new byte[32]));
	}

	@Test void falhaDaAutoridadeImpedeEntregarPdfSemCarimbo() throws Exception {
		HttpClient cliente = mock(HttpClient.class);
		when(cliente.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
				.thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Falha de rede simulada")));
		var assinador = new ServicoAssinaturaPdf(new PoliticaCertificados("", ""),
				new CarimboTempo("https://tsa.exemplo.test", arquivoRaizes, cliente));
		assertThrows(ResponseStatusException.class, () -> assinador.assinar(ArquivosAssinaturaTeste.pdf(), material.arquivo(),
				"senha-teste".toCharArray(), false));
	}

	@Test void indisponibilidadeOcspImpedeAssinatura() {
		var politica = new PoliticaCertificados(arquivoRaizes, "https://127.0.0.1:1");
		var cadeia = new java.security.cert.Certificate[] {material.titular(), material.raiz()};
		assertEquals("CONSULTA_INDISPONIVEL", politica.avaliar(cadeia).revogacao());
		assertThrows(ResponseStatusException.class, () -> politica.exigirParaAssinar(cadeia));
	}

	private HttpClient clienteComResposta(boolean outroResumo, boolean horarioAntigo) throws Exception {
		HttpClient cliente = mock(HttpClient.class);
		when(cliente.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
				.thenAnswer(chamada -> {
					HttpRequest requisicao = chamada.getArgument(0);
					TimeStampRequest pedido = new TimeStampRequest(corpo(requisicao));
					if (outroResumo) {
						pedido = new TimeStampRequestGenerator().generate(TSPAlgorithms.SHA256,
								MessageDigest.getInstance("SHA-256").digest(new byte[] {1}), pedido.getNonce());
					}
					byte[] resposta = responder(pedido, horarioAntigo);
					@SuppressWarnings("unchecked")
					HttpResponse<byte[]> retorno = mock(HttpResponse.class);
					when(retorno.statusCode()).thenReturn(200);
					when(retorno.body()).thenReturn(resposta);
					return CompletableFuture.completedFuture(retorno);
				});
		return cliente;
	}

	private byte[] responder(TimeStampRequest pedido, boolean horarioAntigo) throws Exception {
		Instant agora = Instant.now();
		var construtor = new JcaX509v3CertificateBuilder(new X500Name(material.raiz().getSubjectX500Principal().getName()),
				BigInteger.valueOf(3), Date.from(agora.minusSeconds(3600)), Date.from(agora.plusSeconds(86400)),
				new X500Name("CN=Autoridade de carimbo de testes"), material.chavesTitular().getPublic());
		construtor.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
		X509Certificate certificado = new JcaX509CertificateConverter().getCertificate(construtor.build(
				new JcaContentSignerBuilder("SHA256withRSA").build(material.chavesAutoridade().getPrivate())));
		var calculadores = new JcaDigestCalculatorProviderBuilder().build();
		var assinador = new JcaSignerInfoGeneratorBuilder(calculadores).build(
				new JcaContentSignerBuilder("SHA256withRSA").build(material.chavesTitular().getPrivate()), certificado);
		var gerador = new TimeStampTokenGenerator(assinador, calculadores.get(new AlgorithmIdentifier(TSPAlgorithms.SHA256)),
				new ASN1ObjectIdentifier("1.2.3.4.1"));
		gerador.addCertificates(new JcaCertStore(List.of(certificado, material.raiz())));
		return new TimeStampResponseGenerator(gerador, TSPAlgorithms.ALLOWED).generateGrantedResponse(pedido,
				BigInteger.TEN, Date.from(agora.minusSeconds(horarioAntigo ? 600 : 0))).getEncoded();
	}

	private byte[] corpo(HttpRequest requisicao) throws Exception {
		CompletableFuture<byte[]> recebido = new CompletableFuture<>();
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		requisicao.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
			@Override public void onSubscribe(Flow.Subscription assinatura) { assinatura.request(Long.MAX_VALUE); }
			@Override public void onNext(ByteBuffer bloco) {
				byte[] bytes = new byte[bloco.remaining()];
				bloco.get(bytes);
				saida.writeBytes(bytes);
			}
			@Override public void onError(Throwable erro) { recebido.completeExceptionally(erro); }
			@Override public void onComplete() { recebido.complete(saida.toByteArray()); }
		});
		return recebido.get(5, java.util.concurrent.TimeUnit.SECONDS);
	}
}
