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
	private final ServicoAutenticacaoAssinatura autenticacao;
	private final RepositorioDocumentoAssinatura documentos;
    private final RepositorioSignatarioHistorico historicos;

	public ServicoEnvelopes(RepositorioEnvelopeAssinatura envelopes,
			RepositorioSignatarioEnvelope signatarios, RepositorioCampoAssinatura campos,
			ValidadorPedidoAssinatura validador, ServicoCamposPdf pdfs,
			ServicoAssinaturaPdf assinador, ArmazenamentoMinio armazenamento,
			ServicoAutenticacaoAssinatura autenticacao, RepositorioDocumentoAssinatura documentos, RepositorioSignatarioHistorico historicos) {
		this.envelopes = envelopes;
		this.signatarios = signatarios;
		this.campos = campos;
		this.validador = validador;
		this.pdfs = pdfs;
		this.assinador = assinador;
		this.armazenamento = armazenamento;
		this.autenticacao = autenticacao;
		this.documentos = documentos;
        this.historicos = historicos;
	}

	@Transactional(rollbackFor = Exception.class)
	public Criado criar(MultipartFile arquivo, PedidoCriacao pedido, String criador) throws Exception {
        return criarComDocumentos(arquivo, pedido, criador, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Criado criarComDocumentos(MultipartFile arquivo, PedidoCriacao pedido, String criador,
            List<DocumentoAssinatura> fontes) throws Exception {
		if (pedido == null || pedido.nomeEmpresa() == null || pedido.nomeEmpresa().isBlank()
				|| pedido.nomeEmpresa().length() > 120 || pedido.signatarios() == null
				|| pedido.signatarios().isEmpty() || pedido.signatarios().size() > 20) {
			throw erro("Informe empresa e entre 1 e 20 signatários");
		}
		if (pedido.nomeAssinatura() != null && pedido.nomeAssinatura().length() > 255) throw erro("Nome da assinatura deve ter até 255 caracteres");
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
		if (pedido.nomeAssinatura() != null && !pedido.nomeAssinatura().isBlank()) {
			novoEnvelope.setNome(pedido.nomeAssinatura().strip());
		}
		if (armazenamento.ativo())
			novoEnvelope.guardarOriginalNoMinio(armazenamento.guardar(identificador, "original", pdf));
		envelopes.save(novoEnvelope);
        if (fontes == null) {
            var documento = new DocumentoAssinatura(UUID.randomUUID(), identificador, nomeArquivo);
            documento.setBuffer(pdf); documento.setPaginas(1, paginas);
            documento.setHashDoPdfOriginal(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
            fontes = List.of(documento);
        }
        for (var documento : fontes) {
            documento.setEnvelopeId(identificador);
            documentos.save(documento);
        }
		List<ConviteCriado> convites = new ArrayList<>();
		int totalCampos = 0;
		for (SignatarioNovo novo : pedido.signatarios()) {
			if (novo == null || novo.nome() == null || novo.nome().isBlank() || novo.nome().length() > 120
					|| (novo.email() != null && novo.email().length() > 254)
					|| (novo.email() != null && !novo.email().isBlank() && !novo.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
					|| novo.campos() == null || novo.campos().isEmpty()) {
				throw erro("Cada signatário precisa de nome, e-mail válido quando informado e campos");
			}
            for (String valor : Arrays.asList(novo.cpf(), novo.rg(), novo.whatsapp(), novo.qualificacao())) {
                if (valor != null && valor.length() > 255) throw erro("Dados do signatário devem ter até 255 caracteres por campo");
            }
			UUID idSignatario = UUID.randomUUID();
			byte[] aleatorio = new byte[32];
			ALEATORIO.nextBytes(aleatorio);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
			
			SignatarioEnvelope novoSignatario = new SignatarioEnvelope(idSignatario, identificador, novo.nome().strip(),
					(novo.email() == null ? "" : novo.email().strip()), resumo(token));
			
			if (novo.cpf() != null && !novo.cpf().isBlank()) novoSignatario.setCpf(novo.cpf().strip());
			if (novo.whatsapp() != null && !novo.whatsapp().isBlank()) novoSignatario.setWhatsapp(novo.whatsapp().strip());
			if (novo.rg() != null && !novo.rg().isBlank()) novoSignatario.setRg(novo.rg().strip());
			if (novo.qualificacao() != null && !novo.qualificacao().isBlank()) novoSignatario.setQualificacao(novo.qualificacao().strip());
			
			autenticacao.configurar(novoSignatario, novo.formaDeAutenticacao(), novo.formaDeNotificacao());
			signatarios.save(novoSignatario);
            historicos.save(new SignatarioHistorico(idSignatario, "naoabriulink", "convite_criado", null, null));
			for (CampoNovo campo : novo.campos()) {
				if (campo == null || campo.tipo() == null) throw erro("Tipo de campo inválido");
				CampoAssinatura entidade = new CampoAssinatura(UUID.randomUUID(), idSignatario,
						campo.tipo(), campo.pagina(), campo.posicaoX(), campo.posicaoY(),
						campo.largura(), campo.altura());
				try { pdfs.validar(entidade, paginas); }
				catch (IllegalArgumentException falha) { throw erro(falha.getMessage()); }
                var origem = fontes.stream().filter(d -> campo.pagina() >= d.getPaginaInicio() && campo.pagina() <= d.getPaginaFim())
                    .findFirst().orElseThrow(() -> erro("Campo sem documento de origem"));
                entidade.vincularDocumento(origem);
				campos.save(entidade);
				totalCampos++;
			}
			convites.add(new ConviteCriado(novo.nome().strip(), (novo.email() == null ? "" : novo.email().strip()), token));
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
						signatario.getAssinadoEm(), signatario.getSituacaoAssinatura(),
                        signatario.getQuantidadeDeAcessosNoLinkDeAssinatura(), signatario.getAutenticadoVia(),
                        historicos.findBySignatarioIdOrderByCriadoEmAscIdAsc(signatario.getId()).stream()
                            .map(h -> new HistoricoSignatario(h.getStatus(), h.getAcao(), h.getEnderecoIp(), h.getDispositivo(), h.getCriadoEm())).toList())).toList();
		return new EstadoEnvelope(id, envelope.getNomeEmpresa(), envelope.getNomeDocumento(),
				envelope.getExpiraEm(), envelope.getFinalizadoEm(), estados,
                envelope.getNome(), envelope.getResponsavel(), envelope.getStatus(), envelope.getQuantidadeDocumentoAssinado(),
                documentos.findByEnvelopeIdOrderByPaginaInicio(id).stream()
                    .map(d -> new DocumentoEstado(d.getId(), d.getTitulo(), d.getPaginaInicio(), d.getPaginaFim(), d.getHashDoPdfOriginal())).toList());
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
				envelope.getFinalizadoEm() != null, posicoes, signatario.getFormaDeAutenticacao(),
                documentos.findByEnvelopeIdOrderByPaginaInicio(envelope.getId()).stream()
                    .map(d -> new DocumentoConvite(d.getTitulo(), d.getPaginaInicio(), d.getPaginaFim())).toList());
	}

    @Transactional
    public void registrarAcessoConvite(String token, String ip, String dispositivo) {
        var convidado = obterConvite(token);
        var envelope = envelopes.buscarParaFinalizar(convidado.getEnvelopeId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validarAtivo(envelope); validarPrazoConvite(envelope);
        var pessoa = signatarios.buscarConviteParaAssinar(resumoToken(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido"));
        pessoa.registrarAcesso(dispositivo);
        if (envelope.getFinalizadoEm() == null) envelope.setStatus("Em Processo");
        historicos.save(new SignatarioHistorico(pessoa.getId(), pessoa.getAssinadoEm() == null ? "visualizado" : "assinado",
            "link_aberto", ip, dispositivo));
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

	@Transactional(rollbackFor = Exception.class)
	public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
			MultipartFile selfie, boolean aceite, String enderecoIp) throws Exception {
        assinarConvite(token, assinatura, rubrica, selfie, aceite, enderecoIp, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
            MultipartFile selfie, boolean aceite, String enderecoIp, String dispositivo) throws Exception {
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
		autenticacao.exigirVerificacao(signatario);
        EnumSet<CampoAssinatura.Tipo> tipos = EnumSet.noneOf(CampoAssinatura.Tipo.class);
        if (signatario.getFormaDeAutenticacao() != null) {
            if (signatario.getFormaDeAutenticacao().contains("tela")) tipos.add(CampoAssinatura.Tipo.ASSINATURA);
            if (signatario.getFormaDeAutenticacao().contains("selfie")) tipos.add(CampoAssinatura.Tipo.SELFIE);
        }
		for (CampoAssinatura campo : campos.findBySignatarioId(signatario.getId())) tipos.add(campo.getTipo());
		byte[] desenho = validarMarca(assinatura, tipos.contains(CampoAssinatura.Tipo.ASSINATURA));
		byte[] inicial = validarMarca(rubrica, tipos.contains(CampoAssinatura.Tipo.RUBRICA));
		byte[] fotografia = validarMarca(selfie, tipos.contains(CampoAssinatura.Tipo.SELFIE));
		String ip = enderecoIp == null ? "" : enderecoIp.substring(0, Math.min(64, enderecoIp.length()));
		signatario.assinar(desenho, inicial, fotografia, ip, Instant.now());
        signatario.setDispositivo(dispositivo == null ? null : dispositivo.substring(0, Math.min(1024, dispositivo.length())));
        var metodos = new java.util.LinkedHashSet<String>(signatario.getFormaDeAutenticacao() == null ? List.of() : signatario.getFormaDeAutenticacao());
        if (desenho != null) metodos.add("tela");
        if (inicial != null) metodos.add("rubrica");
        if (fotografia != null) metodos.add("selfie");
        signatario.setAutenticadoVia(String.join(",", metodos));
        historicos.save(new SignatarioHistorico(signatario.getId(), "assinado", "assinatura_confirmada", ip, dispositivo));
		if (temFinal(envelope)) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento já finalizado");
		List<SignatarioEnvelope> participantes = envelope.temPdfAtual()
				? List.of(signatario) : signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
		byte[] atualizado = pdfs.aplicar(pdfAtual(envelope), marcas(participantes));
		if (armazenamento.ativo()) envelope.guardarAtualNoMinio(armazenamento.guardar(envelope.getId(), "atual", atualizado));
		else envelope.atualizarPdf(atualizado);
        atualizarProgresso(envelope);
	}

	@Transactional(rollbackFor = Exception.class)
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
            envelope.setQuantidadeDocumentoAssinado(String.valueOf(Math.max(1, documentos.findByEnvelopeIdOrderByPaginaInicio(id).size())));
            for (var pessoa : participantes) historicos.save(new SignatarioHistorico(pessoa.getId(), "assinado", "certificado_aplicado", null, null));
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

    private void atualizarProgresso(EnvelopeAssinatura envelope) {
        var pessoas = signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
        var docs = documentos.findByEnvelopeIdOrderByPaginaInicio(envelope.getId());
        var posicoes = pessoas.stream().collect(java.util.stream.Collectors.toMap(SignatarioEnvelope::getId,
            p -> campos.findBySignatarioId(p.getId())));
        long completos = 0;
        for (var documento : docs) {
            var envolvidos = pessoas.stream().filter(p -> posicoes.get(p.getId()).stream()
                .anyMatch(c -> documento.getId().equals(c.getDocumentoId()))).toList();
            if (!envolvidos.isEmpty() && envolvidos.stream().allMatch(p -> p.getAssinadoEm() != null)) completos++;
        }
        if (docs.isEmpty() && !pessoas.isEmpty() && pessoas.stream().allMatch(p -> p.getAssinadoEm() != null)) completos = 1;
        envelope.setQuantidadeDocumentoAssinado(String.valueOf(completos));
        envelope.setStatus("Em Processo");
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

	public record PedidoCriacao(String nomeEmpresa, String nomeAssinatura, List<SignatarioNovo> signatarios) { }
	public record SignatarioNovo(String nome, String email, String cpf, String whatsapp, String rg, String qualificacao,
            List<CampoNovo> campos, List<String> formaDeAutenticacao, List<String> formaDeNotificacao) {
        public SignatarioNovo(String nome, String email, String cpf, String whatsapp, String rg, String qualificacao, List<CampoNovo> campos) {
            this(nome, email, cpf, whatsapp, rg, qualificacao, campos, null, null);
        }
    }
	public record CampoNovo(CampoAssinatura.Tipo tipo, int pagina, double posicaoX,
			double posicaoY, double largura, double altura, Integer documento) {
        public CampoNovo(CampoAssinatura.Tipo tipo, int pagina, double x, double y, double largura, double altura) {
            this(tipo, pagina, x, y, largura, altura, null);
        }
    }
    public record ConviteCriado(String nome, String email, String token) { }
	public record Criado(UUID identificador, int paginas, List<ConviteCriado> convites) { }
	public record EstadoSignatario(String nome, String email, Instant assinadoEm, String situacao,
            String acessos, String autenticadoVia, List<HistoricoSignatario> historicos) { }
    public record HistoricoSignatario(String status, String acao, String enderecoIp, String dispositivo, Instant criadoEm) { }
    public record DocumentoEstado(UUID identificador, String titulo, int paginaInicio, int paginaFim, String hashOriginal) { }
	public record EstadoEnvelope(UUID identificador, String nomeEmpresa, String nomeDocumento,
			Instant expiraEm, Instant finalizadoEm, List<EstadoSignatario> signatarios,
            String nome, String responsavel, String status, String quantidadeDocumentoAssinado, List<DocumentoEstado> documentos) { }
	public record CampoResposta(CampoAssinatura.Tipo tipo, int pagina, double posicaoX,
			double posicaoY, double largura, double altura) { }
	public record DadosConvite(String nomeEmpresa, String nomeDocumento, int paginas,
			String nomeSignatario,
			boolean assinado, boolean finalizado, List<CampoResposta> campos, List<String> formaDeAutenticacao, List<DocumentoConvite> documentos) { }
    public record DocumentoConvite(String nome, int paginaInicio, int paginaFim) { }
}
