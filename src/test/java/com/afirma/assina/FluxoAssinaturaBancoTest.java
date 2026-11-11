package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

@SpringJUnitConfig(FluxoAssinaturaBancoTest.Configuracao.class)
@EnabledIfEnvironmentVariable(named = "AFIRMA_TESTE_BANCO_URL", matches = ".+")
class FluxoAssinaturaBancoTest {
	@Autowired ServicoEnvelopes servico;
	@Autowired RepositorioEnvelopeAssinatura envelopes;
	@Autowired RepositorioSignatarioEnvelope signatarios;
	@Autowired RepositorioCampoAssinatura campos;
	@Autowired ServicoCamposPdf pdfs;
	@Autowired ServicoAutenticacaoConvites autenticacao;
	@Autowired ServicoEvidenciasAssinatura evidencias;
	@Autowired ServicoGestaoAssinaturas gestao;
	@Autowired ServicoAssinaturaDireta assinaturasDiretas;
	@Autowired org.springframework.jdbc.core.JdbcTemplate banco;
	@Autowired org.springframework.transaction.PlatformTransactionManager transacoes;
	private static final java.util.Map<String, String> codigos = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Map<String, ServicoAutenticacaoConvites.Sessao> sessoes = new java.util.concurrent.ConcurrentHashMap<>();
	private static byte[] certificado;
	private static byte[] imagem;

	@BeforeAll static void prepararArquivos() throws Exception {
		certificado = ArquivosAssinaturaTeste.certificado(true).arquivo();
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(100, 40, BufferedImage.TYPE_INT_RGB), "png", saida);
		imagem = saida.toByteArray();
	}
	@BeforeEach void limparFalhasSimuladas() { reset(pdfs); }

	@Test void percorreCriacaoAceitesFinalizacaoEVerificacao() throws Exception {
		var criado = criar(2);
		String primeiro = criado.convites().getFirst().token();
		String segundo = criado.convites().getLast().token();
		String resumoInicial = servico.consultarConvite(primeiro).resumoDocumento();
		var pendencia = assertThrows(ResponseStatusException.class, () -> finalizar(criado.identificador()));
		assertEquals(HttpStatus.CONFLICT, pendencia.getStatusCode());
		assinar(primeiro, resumoInicial);
		byte[] aposPrimeiro = servico.documentoConvite(primeiro);
		assinar(primeiro, resumoInicial);
		assertArrayEquals(aposPrimeiro, servico.documentoConvite(primeiro));
		byte[] imagemDiferente = java.util.Arrays.copyOf(imagem, imagem.length + 1);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> servico.assinarConvite(primeiro, new MockMultipartFile("assinatura", "outra.png", "image/png", imagemDiferente),
						null, null, true, "127.0.0.1", resumoInicial, ServicoEnvelopes.TEXTO_ACEITE, false, sessao(primeiro))).getStatusCode());
		var desatualizado = assertThrows(ResponseStatusException.class, () -> assinar(segundo, resumoInicial));
		assertEquals(HttpStatus.CONFLICT, desatualizado.getStatusCode());
		assinar(segundo, servico.consultarConvite(segundo).resumoDocumento());
		byte[] finalizado = finalizar(criado.identificador());
		assertArrayEquals(finalizado, finalizar(criado.identificador()));
		assertArrayEquals(finalizado, servico.documentoFinalConvite(primeiro));
		assertTrue(servico.verificarConvite(primeiro).integridadeConfirmada());
		var participantes = signatarios.findByEnvelopeIdOrderByNome(criado.identificador());
		assertEquals(resumoInicial, participantes.getFirst().getResumoDocumentoAceito());
		assertEquals(ServicoEnvelopes.TEXTO_ACEITE, participantes.getFirst().getTextoAceite());
		assertNull(participantes.getFirst().getAssinatura());
		try (var leitor = new org.openpdf.text.pdf.PdfReader(finalizado)) {
			assertEquals(4, leitor.getNumberOfPages());
			String comprovante = new org.openpdf.text.pdf.parser.PdfTextExtractor(leitor).getTextFromPage(2);
			assertTrue(comprovante.contains(resumoInicial));
			assertTrue(comprovante.contains("p***@exemplo.com"));
		}
	}

	@Test void duasConfirmacoesIguaisGeramApenasUmAceite() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		String resumo = servico.consultarConvite(token).resumoDocumento();
		var resultados = simultaneos(() -> { assinar(token, resumo); return "ok"; });
		assertEquals(List.of("ok", "ok"), resultados);
		assertEquals(1, signatarios.findByEnvelopeIdOrderByNome(criado.identificador()).stream()
				.filter(pessoa -> pessoa.getAssinadoEm() != null).count());
		assertTrue(servico.verificarEnvelope(criado.identificador(), "criador").assinaturas().isEmpty());
	}

	@Test void assinaturasSimultaneasExigemRevisaoDaVersaoAtual() throws Exception {
		var criado = criar(2);
		var tokens = criado.convites().stream().map(ServicoEnvelopes.ConviteCriado::token).toList();
		String resumo = servico.consultarConvite(tokens.getFirst()).resumoDocumento();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			CountDownLatch inicio = new CountDownLatch(1);
			var tarefas = tokens.stream().map(token -> executor.submit(() -> {
				inicio.await();
				try { assinar(token, resumo); return 204; }
				catch (ResponseStatusException erro) { return erro.getStatusCode().value(); }
			})).toList();
			inicio.countDown();
			var resultados = List.of(tarefas.getFirst().get(20, TimeUnit.SECONDS), tarefas.getLast().get(20, TimeUnit.SECONDS));
			assertTrue(resultados.contains(204));
			assertTrue(resultados.contains(409));
		}
		for (String token : tokens) {
			var estado = servico.consultarConvite(token);
			if (!estado.assinado()) assinar(token, estado.resumoDocumento());
		}
		assertTrue(servico.verificarEnvelope(criado.identificador(), "criador").assinaturas().isEmpty());
		assertTrue(new VerificadorAssinaturasPdf(new PoliticaCertificados("", ""), new CarimboTempo("", ""))
				.verificar(finalizar(criado.identificador())).integridadeConfirmada());
	}

	@Test void finalizacoesSimultaneasRetornamOMesmoPdf() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		var resultados = simultaneos(() -> finalizar(criado.identificador()));
		assertArrayEquals(resultados.getFirst(), resultados.getLast());
		assertTrue(servico.verificarConvite(token).integridadeConfirmada());
	}

	@Test void erroAoGerarPdfDesfazAceiteEPermiteTentarNovamente() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		String resumo = servico.consultarConvite(token).resumoDocumento();
		doThrow(new IOException("Falha simulada ao gerar PDF")).when(pdfs).aplicar(any(), any());
		assertThrows(IOException.class, () -> assinar(token, resumo));
		assertNull(signatarios.findByEnvelopeIdOrderByNome(criado.identificador()).getFirst().getAssinadoEm());
		assertFalse(envelopes.findById(criado.identificador()).orElseThrow().temPdfAtual());
		reset(pdfs);
		assinar(token, resumo);
		assertTrue(servico.consultarConvite(token).assinado());
	}

	@Test void rejeitaConviteExpiradoEEnvelopeExcluido() throws Exception {
		UUID identificador = UUID.randomUUID();
		String token = "A".repeat(43);
		byte[] documento = ArquivosAssinaturaTeste.pdf();
		Instant agora = Instant.now();
		var envelope = new EnvelopeAssinatura(identificador, "Empresa", "documento.pdf", "criador", documento,
				agora.minusSeconds(120), agora.minusSeconds(60));
		envelopes.saveAndFlush(envelope);
		var pessoa = new SignatarioEnvelope(UUID.randomUUID(), identificador, "Pessoa", "pessoa@exemplo.com", resumo(token.getBytes()));
		signatarios.saveAndFlush(pessoa);
		campos.saveAndFlush(new CampoAssinatura(UUID.randomUUID(), pessoa.getId(), CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1, .4, .2));
		assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class, () -> servico.consultarConvite(token)).getStatusCode());
		assertThrows(ResponseStatusException.class, () -> autenticacao.solicitarCodigo(token));
		envelope.excluir(agora);
		envelopes.saveAndFlush(envelope);
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servico.documentoConvite(token)).getStatusCode());
	}

	@Test void falhaNaFinalizacaoMantemDocumentoEAceites() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		byte[] documentoAntes = servico.documentoConvite(token);
		assertThrows(java.security.UnrecoverableKeyException.class, () -> servico.finalizar(criado.identificador(), "criador",
				arquivoCertificado(), "senha-errada", false, null, PoliticaDocumentosAssinatura.DECLARACAO_PODERES));
		assertArrayEquals(documentoAntes, servico.documentoConvite(token));
		assertNotNull(signatarios.findByEnvelopeIdOrderByNome(criado.identificador()).getFirst().getAssinatura());
		assertNull(envelopes.findById(criado.identificador()).orElseThrow().getFinalizadoEm());
		finalizar(criado.identificador());
	}

	@Test void prazoDeDownloadDoConviteTerminaSeteDiasAposFinalizar() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		byte[] finalizado = finalizar(criado.identificador());
		var envelope = envelopes.findById(criado.identificador()).orElseThrow();
		envelope.finalizar(finalizado, Instant.now().minusSeconds(8 * 86400));
		envelopes.saveAndFlush(envelope);
		assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
				() -> servico.documentoFinalConvite(token)).getStatusCode());
		assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
				() -> servico.verificarConvite(token)).getStatusCode());
		assertArrayEquals(finalizado, servico.documentoFinal(criado.identificador(), "criador"));
	}

	@Test void rejeitaDeclaracaoDiferenteDaApresentada() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		String resumo = servico.consultarConvite(token).resumoDocumento();
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> servico.assinarConvite(token, new MockMultipartFile("assinatura", "marca.png", "image/png", imagem),
						null, null, true, "127.0.0.1", resumo, "Texto diferente", false, sessao(token))).getStatusCode());
		assertFalse(servico.consultarConvite(token).assinado());
	}

	@Test void codigoNaoFicaEmTextoAbertoENaoPodeSerReutilizado() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assertFalse(autenticacao.consultarAcesso(token, null).autenticado());
		assertEquals("p***@exemplo.com", autenticacao.consultarAcesso(token, null).emailMascarado());
		assertEquals("Empresa", autenticacao.consultarAcesso(token, null).nomeEmpresa());
		assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
				() -> autenticacao.exigirSessao(token, null)).getStatusCode());
		autenticacao.solicitarCodigo(token);
		String codigo = codigos.get("pessoa0@exemplo.com");
		assertTrue(codigo.matches("[0-9]{8}"));
		UUID pessoa = signatarios.findByResumoConvite(SegredosAssinatura.resumo(token)).orElseThrow().getId();
		String guardado = banco.queryForObject("SELECT resumo_codigo FROM autenticacao_convite WHERE signatario_id=?", String.class, pessoa);
		assertNotEquals(codigo, guardado);
		var confirmado = autenticacao.autenticar(token, codigo, "127.0.0.1", "Teste");
		assertNotNull(autenticacao.exigirSessao(token, confirmado.segredoSessao()));
		assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
				() -> autenticacao.autenticar(token, codigo, "127.0.0.1", "Teste")).getStatusCode());
		autenticacao.encerrar(token, confirmado.segredoSessao());
		assertThrows(ResponseStatusException.class, () -> autenticacao.exigirSessao(token, confirmado.segredoSessao()));
	}

	@Test void tentativasIncorretasPersistemEBloqueiamMesmoComCodigoCorreto() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		autenticacao.solicitarCodigo(token);
		String correto = codigos.get("pessoa0@exemplo.com");
		String errado = correto.equals("00000000") ? "11111111" : "00000000";
		for (int tentativa = 0; tentativa < 5; tentativa++) {
			var erro = assertThrows(ResponseStatusException.class, () -> autenticacao.autenticar(token, errado, "127.0.0.1", "Teste"));
			assertEquals(tentativa == 4 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED, erro.getStatusCode());
		}
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
				() -> autenticacao.autenticar(token, correto, "127.0.0.1", "Teste")).getStatusCode());
		assertEquals(5, evidencias.eventos(criado.identificador()).stream().filter(evento -> evento.tipo().equals("CODIGO_RECUSADO")).count());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
				() -> autenticacao.solicitarCodigo(token)).getStatusCode());
	}

	@Test void codigoExpiradoESessaoDeOutroConviteNaoPermitemAssinar() throws Exception {
		var criado = criar(2);
		String primeiro = criado.convites().getFirst().token();
		String segundo = criado.convites().getLast().token();
		var sessaoPrimeiro = sessao(primeiro);
		String resumo = servico.consultarConvite(segundo).resumoDocumento();
		assertThrows(ResponseStatusException.class, () -> servico.assinarConvite(segundo,
				new MockMultipartFile("assinatura", "marca.png", "image/png", imagem), null, null, true, "127.0.0.1",
				resumo, ServicoEnvelopes.TEXTO_ACEITE, false, sessaoPrimeiro));
		autenticacao.solicitarCodigo(segundo);
		UUID pessoa = signatarios.findByResumoConvite(SegredosAssinatura.resumo(segundo)).orElseThrow().getId();
		banco.update("UPDATE autenticacao_convite SET codigo_expira_em=? WHERE signatario_id=?",
				java.sql.Timestamp.from(Instant.now().minusSeconds(1)), pessoa);
		assertThrows(ResponseStatusException.class, () -> autenticacao.autenticar(segundo, codigos.get("pessoa1@exemplo.com"), "127.0.0.1", "Teste"));
		banco.update("UPDATE sessao_convite SET expira_em=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), sessaoPrimeiro.identificador());
		assertThrows(ResponseStatusException.class, () -> assinar(primeiro, resumo));
	}

	@Test void cancelamentoESubstituicaoRevogamAcessosSemApagarEvidencias() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		var sessao = sessao(token);
		assertThrows(ResponseStatusException.class, () -> gestao.substituirConvite(criado.identificador(), sessao.participante(), "intruso", "Motivo"));
		var novo = gestao.substituirConvite(criado.identificador(), sessao.participante(), "criador", "Link compartilhado indevidamente");
		assertThrows(ResponseStatusException.class, () -> autenticacao.consultarAcesso(token, null));
		assertFalse(autenticacao.consultarAcesso(novo.token(), null).autenticado());
		assertThrows(ResponseStatusException.class, () -> autenticacao.conferirSessaoParaAceite(sessao.participante(), sessao));
		gestao.cancelar(criado.identificador(), "criador", "Documento substituído");
		assertThrows(ResponseStatusException.class, () -> autenticacao.consultarAcesso(novo.token(), null));
		assertNotNull(servico.consultar(criado.identificador(), "criador").canceladoEm());
		assertTrue(evidencias.consultar(criado.identificador(), "criador").conferencia().historicoPresente());
		assertFalse(evidencias.arquivos(criado.identificador()).isEmpty());
	}

	@Test void recusaFicaRegistradaEImpedeAceitePosterior() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		var sessao = sessao(token);
		gestao.recusar(token, "Não concordo com o valor", sessao);
		assertNotNull(servico.consultarConvite(token).recusadoEm());
		assertThrows(ResponseStatusException.class, () -> assinar(token, servico.consultarConvite(token).resumoDocumento()));
		assertThrows(ResponseStatusException.class, () -> finalizar(criado.identificador()));
		assertTrue(evidencias.eventos(criado.identificador()).stream().anyMatch(evento -> evento.tipo().equals("CONVITE_RECUSADO")));
	}

	@Test void bancoImpedeAlteracaoERemocaoDasProvas() throws Exception {
		var criado = criar(1);
		assertThrows(org.springframework.dao.DataAccessException.class,
				() -> banco.update("UPDATE evento_assinatura SET conteudo='adulterado' WHERE envelope_id=?", criado.identificador()));
		assertThrows(org.springframework.dao.DataAccessException.class,
				() -> banco.update("DELETE FROM arquivo_evidencia_assinatura WHERE envelope_id=?", criado.identificador()));
		assertTrue(evidencias.conferir(criado.identificador()).historicoPresente());
	}

	@Test void alteracaoDoCadastroOuDoPdfDepoisDoAceiteImpedeFinalizacao() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		UUID pessoa = signatarios.findByEnvelopeIdOrderByNome(criado.identificador()).getFirst().getId();
		banco.update("UPDATE signatario_envelope SET nome='Nome alterado' WHERE id=?", pessoa);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> finalizar(criado.identificador())).getStatusCode());
		banco.update("UPDATE signatario_envelope SET nome='Pessoa 0' WHERE id=?", pessoa);
		banco.update("UPDATE envelope_assinatura SET pdf_atual=? WHERE id=?", ArquivosAssinaturaTeste.pdf(), criado.identificador());
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> finalizar(criado.identificador())).getStatusCode());
	}

	@Test void exportaPacoteConferivelForaDaAplicacaoEDetectaArquivoAdulterado() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		finalizar(criado.identificador());
		assertThrows(ResponseStatusException.class, () -> evidencias.consultar(criado.identificador(), "intruso"));
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		evidencias.exportar(criado.identificador(), "criador", saida);
		java.nio.file.Path arquivo = java.nio.file.Files.createTempFile("afirma-pacote-teste-", ".zip");
		try {
			java.nio.file.Files.write(arquivo, saida.toByteArray());
			var processo = new ProcessBuilder("python3", "scripts/verificar-evidencias.py", arquivo.toString(), "--verificar-pdf").redirectErrorStream(true).start();
			String resultado = new String(processo.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(processo.waitFor(30, TimeUnit.SECONDS));
			assertEquals(0, processo.exitValue(), resultado);
			ByteArrayOutputStream adulterado = new ByteArrayOutputStream();
			try (var entrada = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(saida.toByteArray()));
				 var destino = new java.util.zip.ZipOutputStream(adulterado)) {
				java.util.zip.ZipEntry item;
				boolean modificado = false;
				while ((item = entrada.getNextEntry()) != null) {
					byte[] dados = entrada.readAllBytes();
					if (!modificado && item.getName().endsWith(".pdf")) { dados[20] ^= 1; modificado = true; }
					destino.putNextEntry(new java.util.zip.ZipEntry(item.getName())); destino.write(dados); destino.closeEntry();
				}
			}
			java.nio.file.Files.write(arquivo, adulterado.toByteArray());
			var processoInvalido = new ProcessBuilder("python3", "scripts/verificar-evidencias.py", arquivo.toString()).redirectErrorStream(true).start();
			processoInvalido.getInputStream().readAllBytes(); assertTrue(processoInvalido.waitFor(30, TimeUnit.SECONDS));
			assertNotEquals(0, processoInvalido.exitValue());
		} finally { java.nio.file.Files.deleteIfExists(arquivo); }
	}

	@Test void preservacaoPodeSerEstendidaERelatorioExternoNaoViraAprovacaoAutomatica() throws Exception {
		var criado = criar(1);
		String token = criado.convites().getFirst().token();
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		finalizar(criado.identificador());
		Instant limite = Instant.now().plusSeconds(2000L * 86400);
		gestao.estenderPreservacao(criado.identificador(), "criador", limite, "Preservação para defesa de direitos");
		assertThrows(ErroAssinatura.class, () -> gestao.estenderPreservacao(criado.identificador(), "criador", limite.minusSeconds(3600), "Reduzir prazo"));
		var relatorio = new MockMultipartFile("relatorio", "relatorio.txt", "text/plain", "Relatório controlado de testes".getBytes());
		assertThrows(ResponseStatusException.class, () -> gestao.anexarRelatorioExterno(criado.identificador(), "criador", relatorio,
				"0".repeat(64), "Consulta de testes", "APROVADO"));
		String resumo = servico.verificarEnvelope(criado.identificador(), "criador").resumoDocumento();
		gestao.anexarRelatorioExterno(criado.identificador(), "criador", relatorio, resumo, "Consulta de testes", "APROVADO");
		var registro = evidencias.eventos(criado.identificador()).stream().filter(evento -> evento.tipo().equals("RELATORIO_EXTERNO_ANEXADO")).findFirst().orElseThrow();
		assertTrue(registro.conteudo().contains("\"verificacaoAutomatica\":false"));
		assertTrue(evidencias.conferir(criado.identificador()).historicoPresente());
	}

	@Test void assinaturaDiretaPreservaOriginaisManifestoEPdfFinal() throws Exception {
		PedidoAssinaturaPdf pedido = new PedidoAssinaturaPdf();
		pedido.setIdRequisicao(UUID.randomUUID());
		byte[] original = ArquivosAssinaturaTeste.pdf();
		pedido.setPdf(new MockMultipartFile("pdf", "original.pdf", "application/pdf", original));
		pedido.setTipoDocumento("CONTRATO_PARTICULAR");
		pedido.setDeclaracaoFormalidades(PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES);
		pedido.setDeclaracaoPoderes(PoliticaDocumentosAssinatura.DECLARACAO_PODERES);
		var resultado = assinaturasDiretas.assinar(pedido, new ValidadorPedidoAssinatura.ArquivosValidados(original, certificado.clone()),
				"senha-teste".toCharArray(), false, "criador");
		assertTrue(servico.verificarEnvelope(resultado.identificador(), "criador").integridadeConfirmada());
		var repetido = assinaturasDiretas.assinar(pedido, new ValidadorPedidoAssinatura.ArquivosValidados(original, certificado.clone()),
				"senha-teste".toCharArray(), false, "criador");
		assertEquals(resultado.identificador(), repetido.identificador());
		assertArrayEquals(resultado.pdf(), repetido.pdf());
		var tipos = evidencias.arquivos(resultado.identificador()).stream().map(ServicoEvidenciasAssinatura.Arquivo::tipo).toList();
		assertTrue(tipos.containsAll(List.of("PDF_ORIGINAL", "MANIFESTO_ASSINADO", "SELO_MANIFESTO", "PDF_FINAL")));
		pedido.setTipoDocumento("SAUDE");
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(ResponseStatusException.class, () ->
				assinaturasDiretas.assinar(pedido, new ValidadorPedidoAssinatura.ArquivosValidados(original, certificado.clone()),
						"senha-teste".toCharArray(), false, "criador")).getStatusCode());
	}

	@Test void criacaoRepetidaRecuperaOsMesmosLinksSemGuardarTokensAbertos() throws Exception {
		byte[] original = ArquivosAssinaturaTeste.pdf();
		var pdf = new MockMultipartFile("pdf", "contrato.pdf", "application/pdf", original);
		var participante = new ServicoEnvelopes.SignatarioNovo("Pessoa", "pessoa@exemplo.com",
				List.of(new ServicoEnvelopes.CampoNovo(CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1, .4, .2)), "EM_NOME_PROPRIO", null, null);
		UUID requisicao = UUID.randomUUID();
		var pedido = new ServicoEnvelopes.PedidoCriacao("Empresa", List.of(participante), "CONTRATO_PARTICULAR",
				PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES, requisicao);
		var primeira = servico.criar(pdf, pedido, "criador");
		var segunda = servico.criar(pdf, pedido, "criador");
		assertEquals(primeira, segunda);
		byte[] cifrado = banco.queryForObject("SELECT resposta_cifrada FROM pedido_assinatura WHERE criador=? AND requisicao_id=?",
				byte[].class, "criador", requisicao);
		assertFalse(new String(cifrado, java.nio.charset.StandardCharsets.UTF_8).contains(primeira.convites().getFirst().token()));
		var alterado = new ServicoEnvelopes.PedidoCriacao("Outra empresa", List.of(participante), "CONTRATO_PARTICULAR",
				PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES, requisicao);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> servico.criar(pdf, alterado, "criador")).getStatusCode());
	}

	@Test void criacoesSimultaneasComOMesmoIdentificadorGeramUmEnvelope() throws Exception {
		var pdf = new MockMultipartFile("pdf", "contrato.pdf", "application/pdf", ArquivosAssinaturaTeste.pdf());
		var participante = new ServicoEnvelopes.SignatarioNovo("Pessoa", "pessoa@exemplo.com",
				List.of(new ServicoEnvelopes.CampoNovo(CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1, .4, .2)), "EM_NOME_PROPRIO", null, null);
		var pedido = new ServicoEnvelopes.PedidoCriacao("Empresa", List.of(participante), "CONTRATO_PARTICULAR",
				PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES, UUID.randomUUID());
		var respostas = simultaneos(() -> servico.criar(pdf, pedido, "criador"));
		assertEquals(respostas.getFirst(), respostas.getLast());
	}

	@Test void avisoApresentadoFicaPreservadoEAceiteReferenciaSuaVersao() throws Exception {
		var criado = criar(1); String token = criado.convites().getFirst().token();
		String aviso = servico.consultarConvite(token).avisoPrivacidade();
		assertEquals(aviso, autenticacao.consultarAcesso(token, null).avisoPrivacidade());
		assinar(token, servico.consultarConvite(token).resumoDocumento());
		var aceite = evidencias.eventos(criado.identificador()).stream().filter(evento -> evento.tipo().equals("ACEITE_REGISTRADO")).findFirst().orElseThrow();
		assertTrue(aceite.conteudo().contains(SegredosAssinatura.resumo(aviso)));
	}

	@Test void trocaDeChavePreservaConferenciaDosEventosAntigos() throws Exception {
		var criado = criar(1);
		var ambiente = new MockEnvironment().withProperty("ASSINEAI_CHAVES_EVIDENCIAS",
				"{\"principal\":\"afirma-segredo-exclusivo-para-testes-locais-2026\",\"nova\":\"afirma-outra-chave-exclusiva-para-testes-2026\"}")
				.withProperty("ASSINEAI_CHAVE_ATIVA", "nova");
		var rotacionadas = new ServicoEvidenciasAssinatura(banco, new tools.jackson.databind.ObjectMapper(), new SegredosAssinatura(ambiente),
				new ArmazenamentoMinio(new MockEnvironment()));
		assertTrue(rotacionadas.conferir(criado.identificador()).historicoPresente());
		new org.springframework.transaction.support.TransactionTemplate(transacoes).executeWithoutResult(estado ->
				rotacionadas.registrar(criado.identificador(), null, "TESTE_ROTACAO", java.util.Map.of("motivo", "Teste controlado")));
		assertEquals("nova", rotacionadas.eventos(criado.identificador()).getLast().chaveSelo());
		assertTrue(rotacionadas.conferir(criado.identificador()).historicoPresente());
	}

	private ServicoEnvelopes.Criado criar(int quantidade) throws Exception {
		var pessoas = new java.util.ArrayList<ServicoEnvelopes.SignatarioNovo>();
		for (int indice = 0; indice < quantidade; indice++) {
			pessoas.add(new ServicoEnvelopes.SignatarioNovo("Pessoa " + indice, "pessoa" + indice + "@exemplo.com",
					List.of(new ServicoEnvelopes.CampoNovo(CampoAssinatura.Tipo.ASSINATURA, 1, .1, .1 + indice * .25, .4, .2)),
					"EM_NOME_PROPRIO", null, null));
		}
		return servico.criar(new MockMultipartFile("pdf", "contrato.pdf", "application/pdf", ArquivosAssinaturaTeste.pdf()),
				new ServicoEnvelopes.PedidoCriacao("Empresa", pessoas, "CONTRATO_PARTICULAR", PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES, UUID.randomUUID()), "criador");
	}
	private void assinar(String token, String resumo) throws Exception {
		servico.assinarConvite(token, new MockMultipartFile("assinatura", "assinatura.png", "image/png", imagem),
				null, null, true, "127.0.0.1", resumo, ServicoEnvelopes.TEXTO_ACEITE, false, sessao(token));
	}
	private ServicoAutenticacaoConvites.Sessao sessao(String token) {
		return sessoes.computeIfAbsent(token, convite -> {
			autenticacao.solicitarCodigo(convite);
			String email = signatarios.findByResumoConvite(SegredosAssinatura.resumo(convite)).orElseThrow().getEmail();
			var confirmado = autenticacao.autenticar(convite, codigos.get(email), "127.0.0.1", "Navegador de testes");
			return autenticacao.exigirSessao(convite, confirmado.segredoSessao());
		});
	}

	private MockMultipartFile arquivoCertificado() { return new MockMultipartFile("certificado", certificado.clone()); }
	private byte[] finalizar(UUID identificador) throws Exception {
		return servico.finalizar(identificador, "criador", arquivoCertificado(), "senha-teste", false, null, PoliticaDocumentosAssinatura.DECLARACAO_PODERES);
	}
	private String resumo(byte[] conteudo) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
	}
	private <T> List<T> simultaneos(Callable<T> operacao) throws Exception {
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			CountDownLatch inicio = new CountDownLatch(1);
			Callable<T> tarefa = () -> { inicio.await(); return operacao.call(); };
			var primeira = executor.submit(tarefa);
			var segunda = executor.submit(tarefa);
			inicio.countDown();
			return List.of(primeira.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
		}
	}

	@Configuration
	@EnableTransactionManagement
	@EnableJpaRepositories(basePackageClasses = RepositorioEnvelopeAssinatura.class)
	static class Configuracao {
		@Bean DataSource dataSource() {
			return new DriverManagerDataSource(System.getenv("AFIRMA_TESTE_BANCO_URL"), "postgres", "senha-isolada-testes");
		}
		@Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource banco) {
			Flyway.configure().dataSource(banco).locations("classpath:db/migration").load().migrate();
			var fabrica = new LocalContainerEntityManagerFactoryBean();
			fabrica.setDataSource(banco);
			fabrica.setPackagesToScan("com.afirma.assina");
			fabrica.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
			fabrica.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "validate"));
			return fabrica;
		}
		@Bean JpaTransactionManager transactionManager(EntityManagerFactory fabrica) { return new JpaTransactionManager(fabrica); }
		@Bean ServicoCamposPdf pdfs() { return spy(new ServicoCamposPdf()); }
		@Bean org.springframework.core.env.Environment ambiente() {
			var ambiente = new MockEnvironment(); ambiente.setActiveProfiles("local-test"); return ambiente;
		}
		@Bean org.springframework.jdbc.core.JdbcTemplate banco(DataSource fonte) { return new org.springframework.jdbc.core.JdbcTemplate(fonte); }
		@Bean tools.jackson.databind.ObjectMapper json() { return new tools.jackson.databind.ObjectMapper(); }
		@Bean SegredosAssinatura segredos(org.springframework.core.env.Environment ambiente) { return new SegredosAssinatura(ambiente); }
		@Bean PoliticaDocumentosAssinatura politica(org.springframework.core.env.Environment ambiente) { return new PoliticaDocumentosAssinatura(ambiente); }
		@Bean ArmazenamentoMinio armazenamento() { return new ArmazenamentoMinio(new MockEnvironment()); }
		@Bean ServicoAssinaturaPdf assinador() { return new ServicoAssinaturaPdf(); }
		@Bean ServicoEvidenciasAssinatura evidencias(org.springframework.jdbc.core.JdbcTemplate banco,
				tools.jackson.databind.ObjectMapper json, SegredosAssinatura segredos, ArmazenamentoMinio armazenamento) {
			return new ServicoEvidenciasAssinatura(banco, json, segredos, armazenamento);
		}
		@Bean EnviadorCodigoConvite enviador() {
			var enviador = mock(EnviadorCodigoConvite.class);
			when(enviador.configurado()).thenReturn(true);
			doAnswer(chamada -> { codigos.put(chamada.getArgument(0), chamada.getArgument(2)); return null; })
					.when(enviador).enviar(anyString(), anyString(), anyString());
			return enviador;
		}
		@Bean ServicoAutenticacaoConvites autenticacao(org.springframework.jdbc.core.JdbcTemplate banco,
				SegredosAssinatura segredos, EnviadorCodigoConvite enviador, ServicoEvidenciasAssinatura evidencias) {
			return new ServicoAutenticacaoConvites(banco, segredos, enviador, evidencias);
		}
		@Bean ServicoPedidosAssinatura pedidos(org.springframework.jdbc.core.JdbcTemplate banco, SegredosAssinatura segredos, tools.jackson.databind.ObjectMapper json) {
			return new ServicoPedidosAssinatura(banco, segredos, json);
		}

		@Bean ServicoEnvelopes servico(RepositorioEnvelopeAssinatura envelopes, RepositorioSignatarioEnvelope pessoas,
				RepositorioCampoAssinatura campos, RepositorioConfiguracaoAssinatura configuracoes, ServicoCamposPdf pdfs,
				ArmazenamentoMinio armazenamento, ServicoEvidenciasAssinatura evidencias, ServicoAutenticacaoConvites autenticacao,
				PoliticaDocumentosAssinatura politica, ServicoAssinaturaPdf assinador, ServicoPedidosAssinatura pedidos, RepositorioDocumentoAssinatura documentos, RepositorioSignatarioHistorico historicos) {
			return new ServicoEnvelopes(envelopes, pessoas, campos, new ValidadorPedidoAssinatura(configuracoes), pdfs,
					assinador, armazenamento, mock(ServicoAutenticacaoAssinatura.class), documentos, historicos, mock(PaginaAssinaturaEmpresa.class), evidencias, autenticacao, politica, pedidos, mock(ServicoRegistroParcial.class));
		}
		@Bean ServicoGestaoAssinaturas gestao(RepositorioEnvelopeAssinatura envelopes, RepositorioSignatarioEnvelope pessoas,
				org.springframework.jdbc.core.JdbcTemplate banco, ServicoEvidenciasAssinatura evidencias, ServicoAutenticacaoConvites autenticacao) {
			return new ServicoGestaoAssinaturas(envelopes, pessoas, banco, evidencias, autenticacao);
		}
		@Bean ServicoAssinaturaDireta direta(RepositorioEnvelopeAssinatura envelopes, ServicoEvidenciasAssinatura evidencias,
				PoliticaDocumentosAssinatura politica, ServicoAssinaturaPdf assinador, ArmazenamentoMinio armazenamento, ServicoPedidosAssinatura pedidos) {
			return new ServicoAssinaturaDireta(envelopes, evidencias, politica, assinador, armazenamento, pedidos);
		}
	}
}
