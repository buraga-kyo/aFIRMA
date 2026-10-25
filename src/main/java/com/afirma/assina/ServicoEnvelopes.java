package com.afirma.assina;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoEnvelopes {
	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final int LIMITE_IMAGEM = 2 * 1024 * 1024;
	private final RepositorioEnvelopeAssinatura envelopes;
	private final RepositorioSignatarioEnvelope signatarios;
	private final RepositorioCampoAssinatura campos;
	private final ValidadorPedidoAssinatura validador;
	private final ServicoCamposPdf pdfs;
	private final ServicoAssinaturaPdf assinador;
	private final ArmazenamentoMinio armazenamento;

	public ServicoEnvelopes(RepositorioEnvelopeAssinatura envelopes,
			RepositorioSignatarioEnvelope signatarios, RepositorioCampoAssinatura campos,
			ValidadorPedidoAssinatura validador, ServicoCamposPdf pdfs,
			ServicoAssinaturaPdf assinador, ArmazenamentoMinio armazenamento) {
		this.envelopes = envelopes;
		this.signatarios = signatarios;
		this.campos = campos;
		this.validador = validador;
		this.pdfs = pdfs;
		this.assinador = assinador;
		this.armazenamento = armazenamento;
	}

	@Transactional
	public Criado criar(MultipartFile arquivo, PedidoCriacao pedido, String criador) throws Exception {
		if (pedido == null || pedido.nomeEmpresa() == null || pedido.nomeEmpresa().isBlank()
				|| pedido.nomeEmpresa().length() > 120 || pedido.signatarios() == null
				|| pedido.signatarios().isEmpty() || pedido.signatarios().size() > 20) {
			throw erro("Informe empresa e entre 1 e 20 signatários");
		}
		byte[] pdf = validador.validarPdf(arquivo);
		int paginas;
		try { paginas = pdfs.paginas(pdf); }
		catch (IOException falha) { throw erro("PDF inválido ou protegido"); }
		String nomeArquivo = arquivo.getOriginalFilename();
		if (nomeArquivo == null || nomeArquivo.isBlank()) nomeArquivo = "documento.pdf";
		if (nomeArquivo.length() > 255) nomeArquivo = nomeArquivo.substring(0, 255);
		UUID identificador = UUID.randomUUID();
		Instant agora = Instant.now();
		EnvelopeAssinatura novoEnvelope = new EnvelopeAssinatura(identificador, pedido.nomeEmpresa().strip(),
				nomeArquivo, criador, pdf, agora, agora.plusSeconds(7 * 86400));
		if (armazenamento.ativo())
			novoEnvelope.guardarOriginalNoMinio(armazenamento.guardar(identificador, "original", pdf));
		envelopes.save(novoEnvelope);
		List<ConviteCriado> convites = new ArrayList<>();
		int totalCampos = 0;
		for (SignatarioNovo novo : pedido.signatarios()) {
			if (novo == null || novo.nome() == null || novo.nome().isBlank() || novo.nome().length() > 120
					|| novo.email() == null || novo.email().length() > 254
					|| !novo.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
					|| novo.campos() == null || novo.campos().isEmpty()) {
				throw erro("Cada signatário precisa de nome, e-mail e campos");
			}
			UUID idSignatario = UUID.randomUUID();
			byte[] aleatorio = new byte[32];
			ALEATORIO.nextBytes(aleatorio);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
			signatarios.save(new SignatarioEnvelope(idSignatario, identificador, novo.nome().strip(),
					novo.email().strip(), resumo(token)));
			for (CampoNovo campo : novo.campos()) {
				if (campo == null || campo.tipo() == null) throw erro("Tipo de campo inválido");
				CampoAssinatura entidade = new CampoAssinatura(UUID.randomUUID(), idSignatario,
						campo.tipo(), campo.pagina(), campo.posicaoX(), campo.posicaoY(),
						campo.largura(), campo.altura());
				try { pdfs.validar(entidade, paginas); }
				catch (IllegalArgumentException falha) { throw erro(falha.getMessage()); }
				campos.save(entidade);
				totalCampos++;
			}
			convites.add(new ConviteCriado(novo.nome().strip(), novo.email().strip(), token));
		}
		if (totalCampos > 100) throw erro("Limite de 100 campos por documento");
		return new Criado(identificador, paginas, convites);
	}

	public int paginas(MultipartFile arquivo) throws Exception {
		try { return pdfs.paginas(validador.validarPdf(arquivo)); }
		catch (IOException falha) { throw erro("PDF inválido ou protegido"); }
	}

	public byte[] previsualizar(MultipartFile arquivo, int pagina) throws Exception {
		try { return pdfs.previsualizar(validador.validarPdf(arquivo), pagina); }
		catch (IOException falha) { throw erro("PDF inválido ou protegido"); }
	}

	@Transactional(readOnly = true)
	public EstadoEnvelope consultar(UUID id, String criador) {
		EnvelopeAssinatura envelope = obterEnvelope(id, criador);
		List<EstadoSignatario> estados = signatarios.findByEnvelopeIdOrderByNome(id).stream()
				.map(signatario -> new EstadoSignatario(signatario.getNome(), signatario.getEmail(),
						signatario.getAssinadoEm())).toList();
		return new EstadoEnvelope(id, envelope.getNomeEmpresa(), envelope.getNomeDocumento(),
				envelope.getExpiraEm(), envelope.getFinalizadoEm(), estados);
	}

	@Transactional(readOnly = true)
	public DadosConvite consultarConvite(String token) {
		SignatarioEnvelope signatario = obterConvite(token);
		EnvelopeAssinatura envelope = envelopes.findById(signatario.getEnvelopeId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		validarPrazoConvite(envelope);
		List<CampoResposta> posicoes = campos.findBySignatarioId(signatario.getId()).stream()
				.map(campo -> new CampoResposta(campo.getTipo(), campo.getPagina(), campo.getPosicaoX(),
						campo.getPosicaoY(), campo.getLargura(), campo.getAltura())).toList();
		int paginas;
		try { paginas = pdfs.paginas(pdfAtual(envelope)); }
		catch (Exception falha) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR); }
		return new DadosConvite(envelope.getNomeEmpresa(), envelope.getNomeDocumento(), paginas,
				signatario.getNome(), signatario.getAssinadoEm() != null,
				envelope.getFinalizadoEm() != null, posicoes);
	}

	@Transactional(readOnly = true)
	public byte[] documentoConvite(String token) throws Exception {
		SignatarioEnvelope signatario = obterConvite(token);
		EnvelopeAssinatura envelope = envelopes.findById(signatario.getEnvelopeId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		validarPrazoConvite(envelope);
		return pdfDisponivel(envelope);
	}

	@Transactional(readOnly = true)
	public byte[] previsualizarConvite(String token, int pagina) throws Exception {
		return pdfs.previsualizar(documentoConvite(token), pagina);
	}

	@Transactional(readOnly = true)
	public byte[] documentoFinalConvite(String token) {
		SignatarioEnvelope signatario = obterConvite(token);
		if (signatario.getAssinadoEm() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		EnvelopeAssinatura envelope = envelopes.findById(signatario.getEnvelopeId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		validarPrazoConvite(envelope);
		if (!temFinal(envelope)) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento ainda não finalizado");
		return pdfFinal(envelope);
	}

	@Transactional
	public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
			MultipartFile selfie, boolean aceite, String enderecoIp) throws Exception {
		if (!aceite) throw erro("Confirme a leitura e o aceite do documento");
		String resumoConvite = resumoToken(token);
		SignatarioEnvelope convidado = signatarios.findByResumoConvite(resumoConvite)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido"));
		EnvelopeAssinatura envelope = envelopes.buscarParaFinalizar(convidado.getEnvelopeId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		SignatarioEnvelope signatario = signatarios.buscarConviteParaAssinar(resumoConvite)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido"));
		if (Instant.now().isAfter(envelope.getExpiraEm())) throw new ResponseStatusException(HttpStatus.GONE,
				"Convite expirado");
		if (signatario.getAssinadoEm() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento já assinado por este signatário");
		EnumSet<CampoAssinatura.Tipo> tipos = EnumSet.noneOf(CampoAssinatura.Tipo.class);
		for (CampoAssinatura campo : campos.findBySignatarioId(signatario.getId())) tipos.add(campo.getTipo());
		byte[] desenho = validarMarca(assinatura, tipos.contains(CampoAssinatura.Tipo.ASSINATURA));
		byte[] inicial = validarMarca(rubrica, tipos.contains(CampoAssinatura.Tipo.RUBRICA));
		byte[] fotografia = validarMarca(selfie, tipos.contains(CampoAssinatura.Tipo.SELFIE));
		String ip = enderecoIp == null ? "" : enderecoIp.substring(0, Math.min(64, enderecoIp.length()));
		signatario.assinar(desenho, inicial, fotografia, ip, Instant.now());
		if (temFinal(envelope)) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento já finalizado");
		List<SignatarioEnvelope> participantes = envelope.temPdfAtual()
				? List.of(signatario) : signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
		byte[] atualizado = pdfs.aplicar(pdfAtual(envelope), marcas(participantes));
		if (armazenamento.ativo()) envelope.guardarAtualNoMinio(armazenamento.guardar(envelope.getId(), "atual", atualizado));
		else envelope.atualizarPdf(atualizado);
	}

	@Transactional
	public byte[] finalizar(UUID id, String criador, MultipartFile certificado, String senha,
			boolean paginaPersonalizada, MultipartFile imagemEmpresa) throws Exception {
		EnvelopeAssinatura envelope = envelopes.buscarParaFinalizar(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		if (!envelope.getCriador().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		if (temFinal(envelope)) return pdfFinal(envelope);
		List<SignatarioEnvelope> participantes = signatarios.findByEnvelopeIdOrderByNome(id);
		if (participantes.isEmpty() || participantes.stream().anyMatch(s -> s.getAssinadoEm() == null)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Ainda há assinaturas pendentes");
		}
		if (certificado == null || certificado.isEmpty() || senha == null || senha.isBlank()) {
			throw erro("Certificado e senha são obrigatórios");
		}
		if (certificado.getSize() > 2 * 1024 * 1024) throw erro("Certificado deve ter até 2 MB");
		byte[] pdf = envelope.temPdfAtual() ? pdfAtual(envelope)
				: pdfs.aplicar(pdfOriginal(envelope), marcas(participantes));
		byte[] marcaEmpresa = imagemEmpresa == null ? null : validarImagem(imagemEmpresa, false);
		char[] segredo = senha.toCharArray();
		try {
			byte[] finalizado = assinador.assinar(pdf, certificado.getBytes(), segredo, false,
					paginaPersonalizada, envelope.getNomeEmpresa(), marcaEmpresa);
			envelope.finalizar(finalizado, Instant.now());
			if (armazenamento.ativo()) envelope.guardarFinalNoMinio(armazenamento.guardar(envelope.getId(), "final", finalizado));
			participantes.forEach(SignatarioEnvelope::limparImagens);
			return finalizado;
		} finally {
			Arrays.fill(segredo, '\0');
		}
	}

	@Transactional(readOnly = true)
	public byte[] documentoFinal(UUID id, String criador) throws Exception {
		EnvelopeAssinatura envelope = obterEnvelope(id, criador);
		return pdfDisponivel(envelope);
	}

	private byte[] pdfDisponivel(EnvelopeAssinatura envelope) throws Exception {
		if (temFinal(envelope)) return pdfFinal(envelope);
		if (envelope.temPdfAtual()) return pdfAtual(envelope);
		return pdfs.aplicar(pdfOriginal(envelope),
				marcas(signatarios.findByEnvelopeIdOrderByNome(envelope.getId())));
	}

	private boolean temFinal(EnvelopeAssinatura envelope) {
		return envelope.getPdfFinal() != null || envelope.getPdfFinalChaveMinio() != null;
	}

	private byte[] pdfOriginal(EnvelopeAssinatura envelope) {
		return envelope.getPdfOriginalChaveMinio() == null ? envelope.getPdfOriginal()
				: armazenamento.ler(envelope.getPdfOriginalChaveMinio());
	}

	private byte[] pdfAtual(EnvelopeAssinatura envelope) {
		return envelope.getPdfAtualChaveMinio() != null ? armazenamento.ler(envelope.getPdfAtualChaveMinio())
				: envelope.temPdfAtual() ? envelope.getPdfAtual() : pdfOriginal(envelope);
	}

	private byte[] pdfFinal(EnvelopeAssinatura envelope) {
		return envelope.getPdfFinalChaveMinio() == null ? envelope.getPdfFinal()
				: armazenamento.ler(envelope.getPdfFinalChaveMinio());
	}

	private List<ServicoCamposPdf.Marca> marcas(List<SignatarioEnvelope> participantes) {
		List<ServicoCamposPdf.Marca> marcas = new ArrayList<>();
		for (SignatarioEnvelope signatario : participantes) {
			if (signatario.getAssinadoEm() == null) continue;
			for (CampoAssinatura campo : campos.findBySignatarioId(signatario.getId())) {
				byte[] imagem = switch (campo.getTipo()) {
					case ASSINATURA -> signatario.getAssinatura();
					case RUBRICA -> signatario.getRubrica();
					case SELFIE -> signatario.getSelfie();
				};
				marcas.add(new ServicoCamposPdf.Marca(campo, signatario, imagem));
			}
		}
		return marcas;
	}

	private EnvelopeAssinatura obterEnvelope(UUID id, String criador) {
		EnvelopeAssinatura envelope = envelopes.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		validarAtivo(envelope);
		if (!envelope.getCriador().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		return envelope;
	}

	private void validarAtivo(EnvelopeAssinatura envelope) {
		if (envelope.getExcluidoEm() != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	private SignatarioEnvelope obterConvite(String token) {
		return signatarios.findByResumoConvite(resumoToken(token))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido"));
	}

	private void validarPrazoConvite(EnvelopeAssinatura envelope) {
		Instant limite = envelope.getFinalizadoEm() == null ? envelope.getExpiraEm()
				: envelope.getFinalizadoEm().plusSeconds(7 * 86400);
		if (Instant.now().isAfter(limite)) {
			throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado");
		}
	}

	private String resumoToken(String token) {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido");
		}
		return resumo(token);
	}

	private String resumo(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException falha) {
			throw new IllegalStateException("SHA-256 indisponível", falha);
		}
	}

	private byte[] validarImagem(MultipartFile arquivo, boolean obrigatorio) throws IOException {
		if (arquivo == null || arquivo.isEmpty()) {
			if (obrigatorio) throw erro("Envie todas as imagens solicitadas");
			return null;
		}
		if (arquivo.getSize() > LIMITE_IMAGEM) throw erro("Cada imagem deve ter até 2 MB");
		String mime = arquivo.getContentType();
		if (!"image/png".equals(mime) && !"image/jpeg".equals(mime)) {
			throw erro("Use imagens PNG ou JPEG");
		}
		byte[] dados;
		try (InputStream entrada = arquivo.getInputStream()) {
			dados = entrada.readNBytes(LIMITE_IMAGEM + 1);
		}
		if (dados.length > LIMITE_IMAGEM) throw erro("Cada imagem deve ter até 2 MB");
		try (ImageInputStream entrada = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(dados))) {
			if (entrada == null) throw erro("Imagem inválida");
			var leitores = ImageIO.getImageReaders(entrada);
			if (!leitores.hasNext()) throw erro("Imagem inválida");
			var leitor = leitores.next();
			try {
				leitor.setInput(entrada);
				int largura = leitor.getWidth(0);
				int altura = leitor.getHeight(0);
				if (largura < 1 || altura < 1 || largura > 2500 || altura > 2500
						|| (long) largura * altura > 6_000_000) throw erro("Dimensão da imagem inválida");
			} finally { leitor.dispose(); }
		}
		return dados;
	}

	private byte[] validarMarca(MultipartFile arquivo, boolean esperado) throws IOException {
		if (!esperado && arquivo != null && !arquivo.isEmpty()) {
			throw erro("Imagem sem campo correspondente");
		}
		return validarImagem(arquivo, esperado);
	}

	private ResponseStatusException erro(String mensagem) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
	}

	public record PedidoCriacao(String nomeEmpresa, List<SignatarioNovo> signatarios) { }
	public record SignatarioNovo(String nome, String email, List<CampoNovo> campos) { }
	public record CampoNovo(CampoAssinatura.Tipo tipo, int pagina, double posicaoX,
			double posicaoY, double largura, double altura) { }
	public record ConviteCriado(String nome, String email, String token) { }
	public record Criado(UUID identificador, int paginas, List<ConviteCriado> convites) { }
	public record EstadoSignatario(String nome, String email, Instant assinadoEm) { }
	public record EstadoEnvelope(UUID identificador, String nomeEmpresa, String nomeDocumento,
			Instant expiraEm, Instant finalizadoEm, List<EstadoSignatario> signatarios) { }
	public record CampoResposta(CampoAssinatura.Tipo tipo, int pagina, double posicaoX,
			double posicaoY, double largura, double altura) { }
	public record DadosConvite(String nomeEmpresa, String nomeDocumento, int paginas,
			String nomeSignatario,
			boolean assinado, boolean finalizado, List<CampoResposta> campos) { }
}
