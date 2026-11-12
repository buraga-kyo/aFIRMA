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

  public static final String TEXTO_ACEITE =
    "Li o documento apresentado e concordo com seu conteúdo. " +
    "Confirmo minhas marcas e sei que a empresa finalizará o PDF com seu certificado digital. " +
    "Recebi as informações de tratamento e preservação dos meus dados. Confirmo a atuação e os poderes apresentados. Confirmei acesso ao e-mail cadastrado. Nome, identidade civil e poderes de representação não são verificados automaticamente.";
  private static final SecureRandom ALEATORIO = new SecureRandom();
  private static final int LIMITE_IMAGEM = 2 * 1024 * 1024;
  private final RepositorioEnvelopeAssinatura envelopes;
  private final RepositorioSignatarioEnvelope signatarios;
  private final RepositorioCampoAssinatura campos;
  private final ValidadorPedidoAssinatura validador;
  private final ServicoCamposPdf pdfs;
  private final ServicoAssinaturaPdf assinador;
  private final ArmazenamentoMinio armazenamento;
  private final ServicoAutenticacaoAssinatura autenticacaoAssinatura;
  private final RepositorioDocumentoAssinatura documentos;
  private final RepositorioSignatarioHistorico historicos;
  private final PaginaAssinaturaEmpresa paginas;
  private final ServicoEvidenciasAssinatura evidencias;
  private final ServicoAutenticacaoConvites autenticacao;
  private final PoliticaDocumentosAssinatura politicaDocumentos;
  private final ServicoPedidosAssinatura pedidos;
  private final ServicoRegistroParcial registroParcial;

  @org.springframework.beans.factory.annotation.Value(
    "${APP_PERMITIR_CERTIFICADO_VENCIDO:false}"
  )
  private boolean permitirCertificadoVencido;

  public ServicoEnvelopes(
    RepositorioEnvelopeAssinatura envelopes,
    RepositorioSignatarioEnvelope signatarios,
    RepositorioCampoAssinatura campos,
    ValidadorPedidoAssinatura validador,
    ServicoCamposPdf pdfs,
    ServicoAssinaturaPdf assinador,
    ArmazenamentoMinio armazenamento,
    ServicoAutenticacaoAssinatura autenticacaoAssinatura,
    RepositorioDocumentoAssinatura documentos,
    RepositorioSignatarioHistorico historicos,
    PaginaAssinaturaEmpresa paginas,
    ServicoEvidenciasAssinatura evidencias,
    ServicoAutenticacaoConvites autenticacao,
    PoliticaDocumentosAssinatura politicaDocumentos,
    ServicoPedidosAssinatura pedidos,
    ServicoRegistroParcial registroParcial
  ) {
    this.envelopes = envelopes;
    this.signatarios = signatarios;
    this.campos = campos;
    this.validador = validador;
    this.pdfs = pdfs;
    this.assinador = assinador;
    this.armazenamento = armazenamento;
    this.autenticacaoAssinatura = autenticacaoAssinatura;
    this.documentos = documentos;
    this.historicos = historicos;
    this.paginas = paginas;
    this.evidencias = evidencias;
    this.autenticacao = autenticacao;
    this.politicaDocumentos = politicaDocumentos;
    this.pedidos = pedidos;
    this.registroParcial = registroParcial;
  }

  @Transactional(rollbackFor = Exception.class)
  public Criado criar(
    MultipartFile arquivo,
    PedidoCriacao pedido,
    String criador
  ) throws Exception {
    return criarComDocumentos(arquivo, pedido, criador, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public Criado criarComDocumentos(
    MultipartFile arquivo,
    PedidoCriacao pedido,
    String criador,
    List<DocumentoAssinatura> fontes
  ) throws Exception {
    return criarComDocumentos(arquivo, pedido, criador, fontes, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public Criado criarComDocumentos(MultipartFile arquivo, PedidoCriacao pedido, String criador,
      List<DocumentoAssinatura> fontes, ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    if (
      pedido == null ||
      pedido.nomeEmpresa() == null ||
      pedido.nomeEmpresa().isBlank() ||
      pedido.nomeEmpresa().length() > 120 ||
      pedido.signatarios() == null ||
      (pedido.signatarios().isEmpty() && !pedido.empresaParticipante()) ||
      pedido.signatarios().size() > 20
    ) {
      throw erro("Informe empresa e entre 1 e 20 signatários");
    }
    if (
      pedido.nomeAssinatura() != null && pedido.nomeAssinatura().length() > 255
    ) throw erro("Nome da assinatura deve ter até 255 caracteres");
    politicaDocumentos.validarCriacao(
      pedido.tipoDocumento(),
      pedido.declaracaoFormalidades()
    );
    byte[] pdf = validador.validarPdf(arquivo);
    // O identificador interno do PDF reunido pode variar entre tentativas; os originais não.
    String resumoFontes = fontes == null ? null : fontes.stream()
        .map(DocumentoAssinatura::getHashDoPdfOriginal).collect(java.util.stream.Collectors.joining(":"));
    String resumoPedido = pedidos.resumir(java.util.Arrays.asList(pedido, coletaEmpresa == null ? null : coletaEmpresa.pedido()), fontes == null ? pdf : resumoFontes.getBytes(StandardCharsets.US_ASCII), null, null);
    var anterior = pedidos.iniciar(
      pedido.idRequisicao(),
      criador,
      "CONVITES",
      resumoPedido
    );
    if (anterior != null) {
      obterEnvelope(anterior.envelope(), criador);
      return pedidos.lerResposta(anterior, Criado.class);
    }
    assinador.validarDocumentoAntesDeAlterar(pdf);
    int paginas;
    try {
      paginas = pdfs.paginas(pdf);
    } catch (IOException falha) {
      throw erro("PDF inválido ou protegido");
    }
    String nomeArquivo = arquivo.getOriginalFilename();
    if (nomeArquivo == null || nomeArquivo.isBlank()) nomeArquivo =
      "documento.pdf";
    if (nomeArquivo.length() > 255) nomeArquivo = nomeArquivo.substring(0, 255);
    UUID identificador = UUID.randomUUID();
    Instant agora = Instant.now();
    EnvelopeAssinatura novoEnvelope = new EnvelopeAssinatura(
      identificador,
      pedido.nomeEmpresa().strip(),
      nomeArquivo,
      criador,
      pdf,
      agora,
      agora.plusSeconds(7 * 86400)
    );
    if (pedido.nomeAssinatura() != null && !pedido.nomeAssinatura().isBlank()) {
      novoEnvelope.setNome(pedido.nomeAssinatura().strip());
    }
    if (armazenamento.ativo()) novoEnvelope.guardarOriginalNoMinio(
      armazenamento.guardar(identificador, "original", pdf)
    );
    novoEnvelope.definirFormalidades(
      pedido.tipoDocumento(),
      pedido.declaracaoFormalidades(),
      politicaDocumentos.prazoPreservacao(agora)
    );
    // O UUID já está definido, então o repositório pode devolver outra instância gerenciada.
    // As alterações feitas depois do cadastro precisam continuar nessa instância para chegar ao banco.
    novoEnvelope = envelopes.saveAndFlush(novoEnvelope);
    evidencias.registrar(
      identificador,
      null,
      "ENVELOPE_CRIADO",
      java.util.Map.of(
        "criador",
        criador,
        "tipoDocumento",
        pedido.tipoDocumento(),
        "declaracaoFormalidades",
        pedido.declaracaoFormalidades(),
        "reterAte",
        novoEnvelope.getReterEvidenciasAte().toString()
      )
    );
    evidencias.guardarArquivo(
      identificador,
      null,
      "PDF_ORIGINAL",
      nomeArquivo,
      "application/pdf",
      pdf,
      novoEnvelope.getPdfOriginalChaveMinio()
    );
    evidencias.guardarArquivo(
      identificador,
      null,
      "AVISO_PRIVACIDADE",
      "aviso-privacidade.txt",
      "text/plain",
      politicaDocumentos.avisoPrivacidade().getBytes(StandardCharsets.UTF_8),
      null
    );
    if (fontes == null) {
      var documento = new DocumentoAssinatura(
        UUID.randomUUID(),
        identificador,
        nomeArquivo
      );
      documento.setBuffer(pdf);
      documento.setPaginas(1, paginas);
      documento.setHashDoPdfOriginal(
        HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(pdf)
        )
      );
      fontes = List.of(documento);
    }
    for (var documento : fontes) {
      documento.setEnvelopeId(identificador);
      documentos.save(documento);
      evidencias.guardarArquivo(identificador, null, "PDF_FONTE", documento.getTitulo(),
          "application/pdf", documento.getBuffer(), null);
    }
    List<ConviteCriado> convites = new ArrayList<>();
    int totalCampos = 0;
    for (SignatarioNovo novo : pedido.signatarios()) {
      if (
        novo == null ||
        novo.nome() == null ||
        novo.nome().isBlank() ||
        novo.nome().length() > 120 ||
        (novo.email() != null && novo.email().length() > 254) ||
        (novo.email() != null &&
          !novo.email().isBlank() &&
          !novo.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) ||
        novo.campos() == null ||
        novo.campos().isEmpty()
      ) {
        throw erro(
          "Cada signatário precisa de nome, e-mail válido quando informado e campos"
        );
      }
      for (String valor : Arrays.asList(
        novo.cpf(),
        novo.rg(),
        novo.whatsapp(),
        novo.qualificacao()
      )) {
        if (valor != null && valor.length() > 255) throw erro(
          "Dados do signatário devem ter até 255 caracteres por campo"
        );
      }
      UUID idSignatario = UUID.randomUUID();
      byte[] aleatorio = new byte[32];
      ALEATORIO.nextBytes(aleatorio);
      String token = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(aleatorio);
      politicaDocumentos.validarRepresentacao(
        novo.papel(),
        novo.representado(),
        novo.fundamentoRepresentacao()
      );

      SignatarioEnvelope novoSignatario = new SignatarioEnvelope(
        idSignatario,
        identificador,
        novo.nome().strip(),
        novo.email() == null ? "" : novo.email().strip(),
        resumo(token)
      );

      if (novo.cpf() != null && !novo.cpf().isBlank()) novoSignatario.setCpf(
        novo.cpf().strip()
      );
      if (
        novo.whatsapp() != null && !novo.whatsapp().isBlank()
      ) novoSignatario.setWhatsapp(novo.whatsapp().strip());
      if (novo.rg() != null && !novo.rg().isBlank()) novoSignatario.setRg(
        novo.rg().strip()
      );
      if (
        novo.qualificacao() != null && !novo.qualificacao().isBlank()
      ) novoSignatario.setQualificacao(novo.qualificacao().strip());

      autenticacaoAssinatura.configurar(
        novoSignatario,
        novo.formaDeAutenticacao(),
        novo.formaDeNotificacao()
      );
      novoSignatario = signatarios.save(novoSignatario);
      historicos.save(
        new SignatarioHistorico(
          idSignatario,
          "naoabriulink",
          "convite_criado",
          null,
          null
        )
      );
      novoSignatario.definirRepresentacao(
        novo.papel(),
        novo.representado(),
        novo.fundamentoRepresentacao()
      );
      signatarios.flush();
      evidencias.registrar(
        identificador,
        idSignatario,
        "PARTICIPANTE_CADASTRADO",
        java.util.Map.of(
          "nome",
          novoSignatario.getNome(),
          "email",
          novoSignatario.getEmail(),
          "papel",
          novo.papel(),
          "representado",
          novo.representado() == null ? "" : novo.representado(),
          "fundamentoRepresentacao",
          novo.fundamentoRepresentacao() == null
            ? ""
            : novo.fundamentoRepresentacao()
        )
      );
      for (CampoNovo campo : novo.campos()) {
        if (campo == null || campo.tipo() == null) throw erro(
          "Tipo de campo inválido"
        );
        politicaDocumentos.validarFotografia(
          campo.tipo() == CampoAssinatura.Tipo.SELFIE
        );
        CampoAssinatura entidade = new CampoAssinatura(
          UUID.randomUUID(),
          idSignatario,
          campo.tipo(),
          campo.pagina(),
          campo.posicaoX(),
          campo.posicaoY(),
          campo.largura(),
          campo.altura()
        );
        entidade.setRotacao(campo.rotacao());
        try {
          pdfs.validar(entidade, paginas);
        } catch (IllegalArgumentException falha) {
          throw erro(falha.getMessage());
        }
        var origem = fontes
          .stream()
          .filter(
            d ->
              campo.pagina() >= d.getPaginaInicio() &&
              campo.pagina() <= d.getPaginaFim()
          )
          .findFirst()
          .orElseThrow(() -> erro("Campo sem documento de origem"));
        entidade.vincularDocumento(origem);
        if (campo.nome() != null && campo.nome().length() > 80) throw erro(
          "Nome de campo deve ter até 80 caracteres"
        );
        entidade.configurar(
          campo.nome(),
          campo.obrigatorio() == null || campo.obrigatorio()
        );
        campos.save(entidade);
        totalCampos++;
      }
      convites.add(
        new ConviteCriado(
          novo.nome().strip(),
          novo.email() == null ? "" : novo.email().strip(),
          token
        )
      );
    }
    if (totalCampos > 100) throw erro("Limite de 100 campos por documento");
    registroParcial.configurar(novoEnvelope, criador, pedido.empresaParticipante());
    if (registroParcial.automatica(novoEnvelope)) registroParcial.assinarConfigurado(novoEnvelope, pdf, coletaEmpresa, dispositivoEmpresa, criador, "Criação dos convites");
    guardarParcial(novoEnvelope, registroParcial.gerar(novoEnvelope, pdf, List.of(),
        signatarios.findByEnvelopeIdOrderByNome(identificador)), null);
    if (pedido.signatarios().isEmpty() && registroParcial.automatica(novoEnvelope))
      finalizar(identificador, criador, null, null, false, null, PoliticaDocumentosAssinatura.DECLARACAO_PODERES);
    envelopes.flush();
    Criado criado = new Criado(identificador, paginas, convites);
    pedidos.concluir(
      pedido.idRequisicao(),
      criador,
      "CONVITES",
      resumoPedido,
      identificador,
      criado
    );
    return criado;
  }

  public int paginas(MultipartFile arquivo) throws Exception {
    try {
      return pdfs.paginas(validador.validarPdf(arquivo));
    } catch (IOException falha) {
      throw erro("PDF inválido ou protegido");
    }
  }

  public byte[] previsualizar(MultipartFile arquivo, int pagina)
    throws Exception {
    try {
      return pdfs.previsualizar(validador.validarPdf(arquivo), pagina);
    } catch (IOException falha) {
      throw erro("PDF inválido ou protegido");
    }
  }

  @Transactional(readOnly = true)
  public EstadoEnvelope consultar(UUID id, String criador) {
    EnvelopeAssinatura envelope = envelopes
      .findById(id)
      .filter(
        item ->
          item.getCriador().equals(criador) && item.getExcluidoEm() == null
      )
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    List<EstadoSignatario> estados = signatarios
      .findByEnvelopeIdOrderByNome(id)
      .stream()
      .map(signatario ->
        new EstadoSignatario(
          signatario.getNome(),
          signatario.getEmail(),
          signatario.getAssinadoEm(),
          signatario.getSituacaoAssinatura(),
          signatario.getQuantidadeDeAcessosNoLinkDeAssinatura(),
          signatario.getFirmadoVia(),
          historicos
            .findBySignatarioIdOrderByCriadoEmAscIdAsc(signatario.getId())
            .stream()
            .map(h ->
              new HistoricoSignatario(
                h.getStatus(),
                h.getAcao(),
                h.getEnderecoIp(),
                h.getDispositivo(),
                h.getCriadoEm()
              )
            )
            .toList(),
          campos
            .findBySignatarioId(signatario.getId())
            .stream()
            .filter(c -> c.getAnexoNome() != null)
            .map(c -> new AnexoResumo(c.getId(), c.getAnexoNome()))
            .toList(),
          signatario.getId(),
          signatario.getRecusadoEm(),
          signatario.getAutenticadoEm()
        )
      )
      .toList();
    return new EstadoEnvelope(
      id,
      envelope.getNomeEmpresa(),
      envelope.getNomeDocumento(),
      envelope.getExpiraEm(),
      envelope.getFinalizadoEm(),
      estados,
      envelope.getNome(),
      envelope.getResponsavel(),
      envelope.getStatus(),
      envelope.getQuantidadeDocumentoAssinado(),
      documentos
        .findByEnvelopeIdOrderByPaginaInicio(id)
        .stream()
        .map(d ->
          new DocumentoEstado(
            d.getId(),
            d.getTitulo(),
            d.getPaginaInicio(),
            d.getPaginaFim(),
            d.getHashDoPdfOriginal()
          )
        )
        .toList(),
      envelope.getTipoDocumento(),
      envelope.getReterEvidenciasAte(),
      envelope.getCanceladoEm(),
      envelope.getEmpresaAssinouEm(),
      registroParcial.automatica(envelope),
      registroParcial.participa(envelope)
    );
  }

  @Transactional(readOnly = true)
  public DadosConvite consultarConvite(String token) {
    SignatarioEnvelope signatario = obterConvite(token);
    EnvelopeAssinatura envelope = envelopes
      .findById(signatario.getEnvelopeId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    validarPrazoConvite(envelope);
    List<CampoResposta> posicoes = campos
      .findBySignatarioId(signatario.getId())
      .stream()
      .map(campo ->
        new CampoResposta(
          campo.getTipo(),
          campo.getPagina(),
          campo.getPosicaoX(),
          campo.getPosicaoY(),
          campo.getLargura(),
          campo.getAltura(),
          campo.getId(),
          campo.getNome(),
          campo.isObrigatorio(),
          campo.getValor(),
          campo.getRotacao()
        )
      )
      .toList();
    int paginas;
    byte[] documentoDisponivel;
    try {
      documentoDisponivel = pdfDisponivel(envelope);
      try (
        var leitor = new org.openpdf.text.pdf.PdfReader(documentoDisponivel)
      ) {
        paginas = leitor.getNumberOfPages();
      }
    } catch (Exception falha) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new DadosConvite(
      envelope.getNomeEmpresa(),
      envelope.getNomeDocumento(),
      paginas,
      signatario.getNome(),
      signatario.getAssinadoEm() != null,
      envelope.getFinalizadoEm() != null,
      posicoes,
      signatario.getFormaDeAutenticacao(),
      documentos
        .findByEnvelopeIdOrderByPaginaInicio(envelope.getId())
        .stream()
        .map(d ->
          new DocumentoConvite(
            d.getTitulo(),
            d.getPaginaInicio(),
            d.getPaginaFim()
          )
        )
        .toList(),
      resumo(documentoDisponivel),
      signatario.getEmail(),
      TEXTO_ACEITE,
      envelope.getExpiraEm(),
      signatario.getPapel(),
      signatario.getRepresentado(),
      signatario.getFundamentoRepresentacao(),
      signatario.getRecusadoEm(),
      evidencias.avisoPrivacidade(envelope.getId())
    );
  }

  @Transactional
  public void registrarAcessoConvite(
    String token,
    String ip,
    String dispositivo
  ) {
    var convidado = obterConvite(token);
    var envelope = envelopes
      .buscarParaFinalizar(convidado.getEnvelopeId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    validarPrazoConvite(envelope);
    var pessoa = signatarios
      .buscarConviteParaAssinar(resumoToken(token))
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido")
      );
    pessoa.registrarAcesso(dispositivo);
    if (envelope.getFinalizadoEm() == null) envelope.setStatus("Em Processo");
    historicos.save(
      new SignatarioHistorico(
        pessoa.getId(),
        pessoa.getAssinadoEm() == null ? "visualizado" : "assinado",
        "link_aberto",
        ip,
        dispositivo
      )
    );
  }

  @Transactional(readOnly = true)
  public byte[] documentoConvite(String token) throws Exception {
    return documentoConvite(token, null);
  }

  @Transactional(readOnly = true)
  public byte[] documentoConvite(String token, String resumoEsperado)
    throws Exception {
    SignatarioEnvelope signatario = obterConvite(token);
    EnvelopeAssinatura envelope = envelopes
      .findById(signatario.getEnvelopeId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    validarPrazoConvite(envelope);
    byte[] documento = pdfDisponivel(envelope);
    if (resumoEsperado != null && !resumo(documento).equals(resumoEsperado)) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "O documento foi atualizado. Atualize a leitura para continuar"
      );
    }
    return documento;
  }

  @Transactional(readOnly = true)
  public byte[] previsualizarConvite(
    String token,
    int pagina,
    String resumoEsperado
  ) throws Exception {
    return pdfs.previsualizar(documentoConvite(token, resumoEsperado), pagina);
  }

  @Transactional(readOnly = true)
  public VerificadorAssinaturasPdf.Resultado verificarConvite(String token)
    throws Exception {
    return assinador.verificarDocumento(documentoConvite(token));
  }

  @Transactional(readOnly = true)
  public VerificadorAssinaturasPdf.Resultado verificarEnvelope(
    UUID identificador,
    String criador
  ) throws Exception {
    return assinador.verificarDocumento(
      pdfDisponivel(obterEnvelope(identificador, criador))
    );
  }

  @Transactional(readOnly = true)
  public byte[] documentoFinalConvite(String token) throws Exception {
    SignatarioEnvelope signatario = obterConvite(token);
    if (signatario.getAssinadoEm() == null) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN
    );
    EnvelopeAssinatura envelope = envelopes
      .findById(signatario.getEnvelopeId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    validarPrazoConvite(envelope);
    return pdfDisponivel(envelope);
  }

  @Transactional(rollbackFor = Exception.class)
  public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
      MultipartFile selfie, boolean aceite, String enderecoIp, String resumoDocumentoLido,
      String textoAceite, boolean cienciaInclusaoImagem, ServicoAutenticacaoConvites.Sessao sessao) throws Exception {
    assinarConvite(token, assinatura, rubrica, selfie, aceite, enderecoIp, null,
        java.util.Map.of(), java.util.Map.of(), resumoDocumentoLido, textoAceite, cienciaInclusaoImagem, sessao);
  }

  @Transactional(rollbackFor = Exception.class)
  public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
      MultipartFile selfie, boolean aceite, String enderecoIp, String dispositivo,
      java.util.Map<String, String> valores, java.util.Map<String, MultipartFile> anexos,
      String resumoDocumentoLido, String textoAceite, boolean cienciaInclusaoImagem,
      ServicoAutenticacaoConvites.Sessao sessao) throws Exception {
    assinarConvite(token, assinatura, rubrica, selfie, aceite, enderecoIp, dispositivo, valores, anexos,
        resumoDocumentoLido, textoAceite, cienciaInclusaoImagem, sessao, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public void assinarConvite(String token, MultipartFile assinatura, MultipartFile rubrica,
      MultipartFile selfie, boolean aceite, String enderecoIp, String dispositivo,
      java.util.Map<String, String> valores, java.util.Map<String, MultipartFile> anexos,
      String resumoDocumentoLido, String textoAceite, boolean cienciaInclusaoImagem,
      ServicoAutenticacaoConvites.Sessao sessao, ColetaDadosAssinatura.Dados coleta) throws Exception {
    if (coleta == null) throw erro("A localização e o IP do navegador são obrigatórios");
    if (!aceite) throw erro("Confirme a leitura e o aceite do documento");
    String resumoConvite = resumoToken(token);
    // Consulta somente o identificador para não manter um signatário desatualizado antes do bloqueio.
    UUID identificadorEnvelope = signatarios
      .buscarEnvelopeDoConvite(resumoConvite)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido")
      );
    // As assinaturas do mesmo envelope são serializadas para que cada participante
    // aplique suas marcas sobre o PDF já atualizado pelo participante anterior.
    EnvelopeAssinatura envelope = envelopes
      .buscarParaFinalizar(identificadorEnvelope)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    SignatarioEnvelope signatario = signatarios
      .buscarConviteParaAssinar(resumoConvite)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido")
      );
    autenticacaoAssinatura.exigirVerificacaoComAcessoConfirmado(signatario, sessao);
    registroParcial.restaurarCriacao(envelope);
    if (signatario.getRecusadoEm() != null) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Este convite foi recusado"
    );
    if (
      resumoDocumentoLido == null ||
      !resumoDocumentoLido.matches("[0-9a-f]{64}") ||
      textoAceite == null ||
      textoAceite.length() > 1000
    ) {
      throw erro("Abra o documento antes de confirmar o aceite");
    }
    EnumSet<CampoAssinatura.Tipo> tipos = EnumSet.noneOf(
      CampoAssinatura.Tipo.class
    );
    if (signatario.getFormaDeAutenticacao() != null) {
      if (signatario.getFormaDeAutenticacao().contains("tela")) tipos.add(
        CampoAssinatura.Tipo.ASSINATURA
      );
      if (signatario.getFormaDeAutenticacao().contains("selfie")) tipos.add(
        CampoAssinatura.Tipo.SELFIE
      );
    }
    var posicoesPessoa = campos.findBySignatarioId(signatario.getId());
    var identificadores = new java.util.HashSet<String>();
    for (var campo : posicoesPessoa) {
      identificadores.add(campo.getId().toString());
      if (!campo.preenchivel() && campo.getTipo() != CampoAssinatura.Tipo.ANEXO) tipos.add(campo.getTipo());
    }
    if (
      !identificadores.containsAll(valores.keySet()) ||
      !identificadores.containsAll(anexos.keySet())
    ) throw erro("Campo não pertence a este signatário");
    for (var posicao : posicoesPessoa)
      if (
        anexos.containsKey(posicao.getId().toString()) &&
        posicao.getTipo() != CampoAssinatura.Tipo.ANEXO
      ) throw erro("Campo não aceita anexo");
    var obrigatorios = EnumSet.noneOf(CampoAssinatura.Tipo.class);
    posicoesPessoa
      .stream()
      .filter(CampoAssinatura::isObrigatorio)
      .forEach(c -> obrigatorios.add(c.getTipo()));
    if (signatario.getFormaDeAutenticacao() != null) {
      if (
        signatario.getFormaDeAutenticacao().contains("tela")
      ) obrigatorios.add(CampoAssinatura.Tipo.ASSINATURA);
      if (
        signatario.getFormaDeAutenticacao().contains("selfie")
      ) obrigatorios.add(CampoAssinatura.Tipo.SELFIE);
    }
    politicaDocumentos.validarFotografia(
      tipos.contains(CampoAssinatura.Tipo.SELFIE)
    );
    if (
      tipos.contains(CampoAssinatura.Tipo.SELFIE) && !cienciaInclusaoImagem
    ) throw erro(
      "Confirme a ciência de que a fotografia será incluída no PDF e nas evidências"
    );
    byte[] desenho = validarMarcaOpcional(
      assinatura,
      tipos.contains(CampoAssinatura.Tipo.ASSINATURA),
      obrigatorios.contains(CampoAssinatura.Tipo.ASSINATURA)
    );
    byte[] inicial = validarMarcaOpcional(
      rubrica,
      tipos.contains(CampoAssinatura.Tipo.RUBRICA),
      obrigatorios.contains(CampoAssinatura.Tipo.RUBRICA)
    );
    byte[] fotografia = validarMarcaOpcional(
      selfie,
      tipos.contains(CampoAssinatura.Tipo.SELFIE),
      obrigatorios.contains(CampoAssinatura.Tipo.SELFIE)
    );
    String resumoEnvio = resumoEnvio(
      resumoDocumentoLido,
      textoAceite,
      desenho,
      inicial,
      fotografia
    );
    var resumosAnexos = new java.util.TreeMap<String, String>();
    for (var anexo : anexos.entrySet()) {
      if (anexo.getValue().getSize() > 2 * 1024 * 1024) throw erro("Cada anexo deve ter até 2 MB");
      try (var entrada = anexo.getValue().getInputStream()) {
        byte[] dados = entrada.readNBytes(2 * 1024 * 1024 + 1);
        if (dados.length > 2 * 1024 * 1024) throw erro("Cada anexo deve ter até 2 MB");
        resumosAnexos.put(anexo.getKey(), resumo(dados));
      }
    }
    var conteudoCampos = new java.util.TreeMap<String, Object>();
    conteudoCampos.put("valores", new java.util.TreeMap<>(valores));
    conteudoCampos.put("anexos", resumosAnexos);
    conteudoCampos.put("coleta", coleta.pedido());
    resumoEnvio = resumo((resumoEnvio + new tools.jackson.databind.ObjectMapper().writeValueAsString(conteudoCampos))
        .getBytes(StandardCharsets.UTF_8));
    if (signatario.getAssinadoEm() != null) {
      // A mesma confirmação pode voltar após perda de conexão, sem aplicar as imagens novamente.
      if (resumoEnvio.equals(signatario.getResumoEnvio())) return;
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Documento já assinado por este signatário"
      );
    }
    coleta.exigirRecente();
    if (!TEXTO_ACEITE.equals(textoAceite)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "A declaração de aceite foi atualizada. Atualize a leitura antes de confirmar"
    );
    if (
      !Instant.now().isBefore(envelope.getExpiraEm())
    ) throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado");
    if (temFinal(envelope)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Documento já finalizado"
    );
    evidencias.conferir(envelope.getId());
    byte[] documentoLido = pdfAtual(envelope);
    evidencias.conferirConteudoRegistrado(
      envelope.getId(),
      documentoLido,
      temPdfAtualPreservado(envelope) ? "PDF_ATUAL" : "PDF_ORIGINAL"
    );
    if (!resumo(documentoLido).equals(resumoDocumentoLido)) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "O documento recebeu outra assinatura. Atualize e leia a nova versão antes de confirmar"
      );
    }
    for (var campo : posicoesPessoa) {
      if (campo.getTipo() == CampoAssinatura.Tipo.ANEXO) preencherAnexo(campo, anexos.get(campo.getId().toString()));
      else if (campo.preenchivel()) preencherValor(campo, valores.getOrDefault(campo.getId().toString(), ""));
    }
    String ip = coleta.endereco();
    signatario.registrarOrigem(coleta.ipConexao(), coleta.origemIp());
    signatario.setGeolocalizacao(new tools.jackson.databind.ObjectMapper().writeValueAsString(coleta.localizacao()));
    signatario.assinar(desenho, inicial, fotografia, ip, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    signatario.setDispositivo(
      dispositivo == null
        ? null
        : dispositivo.substring(0, Math.min(1024, dispositivo.length()))
    );
    var metodos = new java.util.LinkedHashSet<String>(
      signatario.getFormaDeAutenticacao() == null
        ? List.of()
        : signatario.getFormaDeAutenticacao()
    );
    if (desenho != null) metodos.add("tela");
    if (inicial != null) metodos.add("rubrica");
    if (fotografia != null) metodos.add("selfie");
    signatario.setFirmadoVia(String.join(",", metodos));
    historicos.save(
      new SignatarioHistorico(
        signatario.getId(),
        "assinado",
        "assinatura_confirmada",
        ip,
        dispositivo
      )
    );
    if (temFinal(envelope)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Documento já finalizado"
    );
    signatario.registrarAceite(resumoDocumentoLido, resumoEnvio, textoAceite);
    signatario.registrarAutenticacao(sessao);
    java.util.Map<String, Object> informacoesAceite = new java.util.TreeMap<>();
    informacoesAceite.put("resumoDocumento", resumoDocumentoLido);
    informacoesAceite.put("declaracao", textoAceite);
    informacoesAceite.put("autenticacao", sessao.identificador().toString());
    informacoesAceite.put("autenticadoEm", sessao.autenticadaEm().toString());
    informacoesAceite.put("enderecoIp", ip);
    informacoesAceite.put("ipConexao", coleta.ipConexao());
    informacoesAceite.put("origemIp", coleta.origemIp());
    informacoesAceite.put("geolocalizacao", signatario.getGeolocalizacao());
    informacoesAceite.put("cpf", signatario.getCpf());
    informacoesAceite.put("rg", signatario.getRg());
    informacoesAceite.put("whatsapp", signatario.getWhatsapp());
    informacoesAceite.put("assinadoEm", signatario.getAssinadoEm().toString());
    informacoesAceite.put("resumoEnvio", resumoEnvio);
    informacoesAceite.put("camposPreenchidos", conteudoCampos);
    informacoesAceite.put(
      "resumoAvisoPrivacidade",
      resumo(evidencias.avisoPrivacidade(envelope.getId()))
    );
    informacoesAceite.put("cienciaInclusaoImagem", cienciaInclusaoImagem);
    informacoesAceite.put("nome", signatario.getNome());
    informacoesAceite.put("email", signatario.getEmail());
    informacoesAceite.put("papel", signatario.getPapel());
    informacoesAceite.put("representado", signatario.getRepresentado());
    informacoesAceite.put(
      "fundamentoRepresentacao",
      signatario.getFundamentoRepresentacao()
    );
    evidencias.registrar(
      envelope.getId(),
      signatario.getId(),
      "ACEITE_REGISTRADO",
      informacoesAceite
    );
    for (var campo : posicoesPessoa) {
      if (campo.getTipo() == CampoAssinatura.Tipo.ANEXO && campo.getAnexo() != null) {
        evidencias.guardarArquivo(envelope.getId(), signatario.getId(), "ANEXO_PARTICIPANTE",
            campo.getAnexoNome(), campo.getAnexoTipo(), campo.getAnexo(), null);
      }
    }
    guardarMarca(envelope, signatario, "ASSINATURA", desenho);
    guardarMarca(envelope, signatario, "RUBRICA", inicial);
    guardarMarca(envelope, signatario, "SELFIE", fotografia);
    List<SignatarioEnvelope> participantes = signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
    byte[] atualizado = registroParcial.gerar(envelope, pdfOriginal(envelope), marcas(participantes), participantes);
    guardarParcial(envelope, atualizado, signatario.getId());
    atualizarProgresso(envelope);
    if (registroParcial.podeConcluirAutomaticamente(envelope) && participantes.stream().allMatch(pessoa -> pessoa.getAssinadoEm() != null)) {
      // O último aceite fecha também as provas, usando a autorização e o certificado da empresa.
      finalizar(envelope.getId(), envelope.getCriador(), null, null, false, null,
          PoliticaDocumentosAssinatura.DECLARACAO_PODERES);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public void sincronizarAssinaturaEmpresa(UUID id, String criador) throws Exception {
    var envelope = envelopes.buscarParaFinalizar(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!envelope.getCriador().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    validarAtivo(envelope);
    registroParcial.restaurarCriacao(envelope);
    if (temFinal(envelope) || !registroParcial.automatica(envelope) || envelope.getEmpresaAssinouEm() != null) return;
    byte[] original = pdfOriginal(envelope);
    registroParcial.assinarConfigurado(envelope, original);
    var participantes = signatarios.findByEnvelopeIdOrderByNome(id);
    guardarParcial(envelope, registroParcial.gerar(envelope, original, marcas(participantes), participantes), null);
  }

  @Transactional(rollbackFor = Exception.class)
  public void concluirAutomaticamente(UUID id, String criador) throws Exception {
    var envelope = envelopes.buscarParaFinalizar(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!envelope.getCriador().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (!registroParcial.podeConcluirAutomaticamente(envelope)) throw erro("A participação da empresa ainda aguarda assinatura manual");
    finalizar(id, criador, null, null, false, null, PoliticaDocumentosAssinatura.DECLARACAO_PODERES);
  }

  @Transactional(rollbackFor = Exception.class)
  public byte[] finalizar(
    UUID id,
    String criador,
    MultipartFile certificado,
    String senha,
    boolean paginaPersonalizada,
    MultipartFile imagemEmpresa,
    String declaracaoPoderes
  ) throws Exception {
    return finalizar(id, criador, certificado, senha, paginaPersonalizada, imagemEmpresa, declaracaoPoderes, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public byte[] finalizar(UUID id, String criador, MultipartFile certificado, String senha, boolean paginaPersonalizada,
      MultipartFile imagemEmpresa, String declaracaoPoderes, ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    EnvelopeAssinatura envelope = envelopes
      .buscarParaFinalizar(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    if (
      !envelope.getCriador().equals(criador)
    ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    registroParcial.restaurarCriacao(envelope);
    if (temFinal(envelope)) return pdfDisponivel(envelope);
    politicaDocumentos.validarCriacao(
      envelope.getTipoDocumento(),
      envelope.getDeclaracaoFormalidades()
    );
    if (
      !PoliticaDocumentosAssinatura.DECLARACAO_PODERES.equals(declaracaoPoderes)
    ) throw erro("Confirme os poderes para usar o certificado");
    evidencias.conferir(id);
    List<SignatarioEnvelope> participantes =
      signatarios.findByEnvelopeIdOrderByNome(id);
    if (
      (participantes.isEmpty() && !registroParcial.participa(envelope)) ||
      participantes
        .stream()
        .anyMatch(
          s -> s.getAssinadoEm() == null || s.getAutenticacaoId() == null
        )
    ) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Ainda há assinaturas pendentes"
      );
    }
    byte[] pdf = temPdfAtualPreservado(envelope)
      ? pdfAtual(envelope)
      : pdfs.aplicar(pdfOriginal(envelope), marcas(participantes));
    evidencias.conferirConteudoRegistrado(
      id,
      pdf,
      temPdfAtualPreservado(envelope) ? "PDF_ATUAL" : "PDF_ORIGINAL"
    );
    for (var participante : participantes)
      evidencias.conferirAceite(participante);
    for (var arquivoPreservado : evidencias.arquivos(id))
      evidencias.lerArquivo(id, arquivoPreservado);
    byte[] marcaEmpresa =
      !paginaPersonalizada || imagemEmpresa == null
        ? null
        : validarImagem(imagemEmpresa, false);
    byte[] dadosCertificado;
    char[] segredo;
    if (certificado == null || certificado.isEmpty()) {
      var material = registroParcial.carregarCertificado(envelope);
      dadosCertificado = material.dados();
      segredo = material.senha();
    } else {
      dadosCertificado = validador.validarCertificado(certificado);
      try { segredo = validador.resolverSenhaCertificado(senha).toCharArray(); }
      catch (Exception falha) { Arrays.fill(dadosCertificado, (byte) 0); throw falha; }
    }
    try {
      if (envelope.getEmpresaAssinouEm() == null) {
        registroParcial.assinarEmpresa(envelope, pdfOriginal(envelope), dadosCertificado, segredo, coletaEmpresa, dispositivoEmpresa, criador, "Assinatura manual da empresa");
        pdf = registroParcial.gerar(envelope, pdfOriginal(envelope), marcas(participantes), participantes);
        guardarParcial(envelope, pdf, null);
      }
      // O relatório já contém os participantes. As declarações completas permanecem nas provas preservadas.
      evidencias.registrar(
        id,
        null,
        "FINALIZACAO_SOLICITADA",
        java.util.Map.of(
          "criador",
          criador,
          "declaracaoPoderes",
          declaracaoPoderes
        )
      );
      byte[] manifesto = evidencias.manifestoParaAssinar(id);
      byte[] seloManifesto = assinador.assinarManifesto(
        manifesto,
        dadosCertificado,
        segredo,
        permitirCertificadoVencido
      );
      evidencias.guardarArquivo(
        id,
        null,
        "MANIFESTO_ASSINADO",
        "manifesto-assinado.json",
        "application/json",
        manifesto,
        null
      );
      evidencias.guardarArquivo(
        id,
        null,
        "SELO_MANIFESTO",
        "manifesto-assinado.p7s",
        "application/pkcs7-signature",
        seloManifesto,
        null
      );
      pdf = new ComprovanteAceites().acrescentarVinculoEvidencias(
        pdf,
        envelope,
        resumo(manifesto),
        resumo(seloManifesto)
      );
      // O relatório da empresa já foi gerado. O selo final protege essas páginas sem criar uma segunda folha de assinaturas.
      byte[] finalizado = paginaPersonalizada && registroParcial.participa(envelope)
          ? assinador.assinar(pdf, dadosCertificado, segredo, permitirCertificadoVencido, true,
              envelope.getNomeEmpresa(), marcaEmpresa, registroParcial.modelo(envelope))
          : assinador.assinar(pdf, dadosCertificado, segredo, permitirCertificadoVencido);
      envelope.finalizar(finalizado, Instant.now());
      if (armazenamento.ativo()) envelope.guardarFinalNoMinio(
        armazenamento.guardar(envelope.getId(), "final", finalizado)
      );
      envelope.setQuantidadeDocumentoAssinado(
        String.valueOf(
          Math.max(1, documentos.findByEnvelopeIdOrderByPaginaInicio(id).size())
        )
      );
      for (var pessoa : participantes)
        historicos.save(
          new SignatarioHistorico(
            pessoa.getId(),
            "assinado",
            "certificado_aplicado",
            null,
            null
          )
        );
      evidencias.guardarArquivo(
        id,
        null,
        "PDF_FINAL",
        "documento-final.pdf",
        "application/pdf",
        finalizado,
        envelope.getPdfFinalChaveMinio()
      );
      evidencias.registrar(
        id,
        null,
        "ENVELOPE_FINALIZADO",
        java.util.Map.of(
          "resumoPdfFinal",
          resumo(finalizado),
          "resumoManifesto",
          resumo(manifesto),
          "resumoSeloManifesto",
          resumo(seloManifesto)
        )
      );
      participantes.forEach(SignatarioEnvelope::limparImagens);
      return finalizado;
    } finally {
      Arrays.fill(segredo, '\0');
      Arrays.fill(dadosCertificado, (byte) 0);
    }
  }

  @Transactional(readOnly = true)
  public byte[] documentoFinal(UUID id, String criador) throws Exception {
    EnvelopeAssinatura envelope = obterEnvelope(id, criador);
    return pdfDisponivel(envelope);
  }

  private void guardarParcial(EnvelopeAssinatura envelope, byte[] pdf, UUID participante) {
    if (armazenamento.ativo()) envelope.guardarAtualNoMinio(armazenamento.guardar(envelope.getId(), "atual", pdf));
    else envelope.atualizarPdf(pdf);
    evidencias.guardarArquivo(envelope.getId(), participante, "PDF_ATUAL", "documento-parcial.pdf", "application/pdf", pdf, envelope.getPdfAtualChaveMinio());
  }

  @Transactional(rollbackFor = Exception.class)
  public byte[] assinarPelaEmpresa(UUID id, String criador, MultipartFile certificado, String senha, String declaracaoPoderes) throws Exception {
    return assinarPelaEmpresa(id, criador, certificado, senha, declaracaoPoderes, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public byte[] assinarPelaEmpresa(UUID id, String criador, MultipartFile certificado, String senha, String declaracaoPoderes,
      ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    var envelope = envelopes.buscarParaFinalizar(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    if (!envelope.getCriador().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (temFinal(envelope) || envelope.getEmpresaAssinouEm() != null) return pdfDisponivel(envelope);
    if (!PoliticaDocumentosAssinatura.DECLARACAO_PODERES.equals(declaracaoPoderes)) throw erro("Confirme os poderes para usar o certificado");
    evidencias.conferir(id);
    registroParcial.restaurarCriacao(envelope);
    if (envelope.getEmpresaAssinouEm() != null) return pdfDisponivel(envelope);
    if (envelope.getConfiguracaoRegistroAssinatura() == null) registroParcial.configurar(envelope, criador);
    byte[] original = pdfOriginal(envelope);
    if (certificado == null || certificado.isEmpty()) registroParcial.assinarConfigurado(envelope, original, coletaEmpresa, dispositivoEmpresa, criador, "Assinatura manual da empresa");
    else {
      byte[] dados = validador.validarCertificado(certificado);
      char[] segredo = null;
      try {
        segredo = validador.resolverSenhaCertificado(senha).toCharArray();
        registroParcial.assinarEmpresa(envelope, original, dados, segredo, coletaEmpresa, dispositivoEmpresa, criador, "Assinatura manual da empresa");
      } finally {
        Arrays.fill(dados, (byte) 0);
        if (segredo != null) Arrays.fill(segredo, '\0');
      }
    }
    var participantes = signatarios.findByEnvelopeIdOrderByNome(id);
    guardarParcial(envelope, registroParcial.gerar(envelope, original, marcas(participantes), participantes), null);
    if (participantes.stream().allMatch(pessoa -> pessoa.getAssinadoEm() != null))
      return finalizar(id, criador, certificado, senha, false, null, declaracaoPoderes);
    return pdfDisponivel(envelope);
  }

  private void atualizarProgresso(EnvelopeAssinatura envelope) {
    var pessoas = signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
    var documentosAtual = documentos.findByEnvelopeIdOrderByPaginaInicio(
      envelope.getId()
    );
    var posicoes = pessoas
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(SignatarioEnvelope::getId, p ->
          campos.findBySignatarioId(p.getId())
        )
      );
    long completos = 0;
    for (var documento : documentosAtual) {
      var envolvidos = pessoas
        .stream()
        .filter(p ->
          posicoes
            .get(p.getId())
            .stream()
            .anyMatch(c -> documento.getId().equals(c.getDocumentoId()))
        )
        .toList();
      if (
        !envolvidos.isEmpty() &&
        envolvidos.stream().allMatch(p -> p.getAssinadoEm() != null)
      ) completos++;
    }
    if (
      documentosAtual.isEmpty() &&
      !pessoas.isEmpty() &&
      pessoas.stream().allMatch(p -> p.getAssinadoEm() != null)
    ) completos = 1;
    envelope.setQuantidadeDocumentoAssinado(String.valueOf(completos));
    envelope.setStatus("Em Processo");
  }

  private byte[] pdfDisponivel(EnvelopeAssinatura envelope) throws Exception {
    byte[] documento;
    String tipo;
    if (temFinal(envelope)) {
      documento = pdfFinal(envelope);
      tipo = "PDF_FINAL";
    } else if (temPdfAtualPreservado(envelope)) {
      documento = pdfAtual(envelope);
      tipo = "PDF_ATUAL";
    } else {
      documento = pdfs.aplicar(
        pdfOriginal(envelope),
        marcas(signatarios.findByEnvelopeIdOrderByNome(envelope.getId()))
      );
      tipo = "PDF_ORIGINAL";
    }
    if (envelope.getTipoDocumento() != null) {
      evidencias.conferir(envelope.getId());
      evidencias.conferirConteudoRegistrado(envelope.getId(), documento, tipo);
    }
    return documento;
  }

  @Transactional(readOnly = true)
  public CampoAssinatura anexoConvite(String token, UUID id) {
    var pessoa = obterConvite(token);
    var envelope = envelopes
      .findById(pessoa.getEnvelopeId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    validarPrazoConvite(envelope);
    return campos
      .findBySignatarioId(pessoa.getId())
      .stream()
      .filter(c -> c.getId().equals(id) && c.getAnexo() != null)
      .findFirst()
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public CampoAssinatura anexoEmpresa(
    UUID envelopeId,
    UUID id,
    String usuario
  ) {
    obterEnvelope(envelopeId, usuario);
    for (var pessoa : signatarios.findByEnvelopeIdOrderByNome(envelopeId))
      for (var campo : campos.findBySignatarioId(pessoa.getId()))
        if (campo.getId().equals(id) && campo.getAnexo() != null) return campo;
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  private boolean temFinal(EnvelopeAssinatura envelope) {
    return (
      envelope.getPdfFinal() != null || envelope.getPdfFinalChaveMinio() != null
    );
  }

  private byte[] pdfOriginal(EnvelopeAssinatura envelope) {
    return envelope.getPdfOriginalChaveMinio() == null
      ? envelope.getPdfOriginal()
      : armazenamento.ler(envelope.getPdfOriginalChaveMinio());
  }

  private boolean temPdfAtualPreservado(EnvelopeAssinatura envelope) {
    return envelope.temPdfAtual() || evidencias.arquivos(envelope.getId()).stream().anyMatch(arquivo -> "PDF_ATUAL".equals(arquivo.tipo()));
  }

  private byte[] pdfAtual(EnvelopeAssinatura envelope) {
    if (envelope.getPdfAtualChaveMinio() != null) return armazenamento.ler(envelope.getPdfAtualChaveMinio());
    if (envelope.temPdfAtual()) return envelope.getPdfAtual();
    var preservados = evidencias.arquivos(envelope.getId()).stream().filter(arquivo -> "PDF_ATUAL".equals(arquivo.tipo())).toList();
    return preservados.isEmpty() ? pdfOriginal(envelope) : evidencias.lerArquivo(envelope.getId(), preservados.getLast());
  }

  private byte[] pdfFinal(EnvelopeAssinatura envelope) {
    return envelope.getPdfFinalChaveMinio() == null
      ? envelope.getPdfFinal()
      : armazenamento.ler(envelope.getPdfFinalChaveMinio());
  }

  private void guardarMarca(
    EnvelopeAssinatura envelope,
    SignatarioEnvelope pessoa,
    String tipo,
    byte[] imagem
  ) {
    if (imagem == null) return;
    String mime =
      imagem.length > 2 && imagem[0] == (byte) 0x89
        ? "image/png"
        : "image/jpeg";
    evidencias.guardarArquivo(
      envelope.getId(),
      pessoa.getId(),
      tipo,
      tipo.toLowerCase(java.util.Locale.ROOT),
      mime,
      imagem,
      null
    );
  }

  private List<ServicoCamposPdf.Marca> marcas(
    List<SignatarioEnvelope> participantes
  ) {
    List<ServicoCamposPdf.Marca> marcas = new ArrayList<>();
    for (SignatarioEnvelope signatario : participantes) {
      if (signatario.getAssinadoEm() == null) continue;
      for (CampoAssinatura campo : campos.findBySignatarioId(
        signatario.getId()
      )) {
        byte[] imagem = switch (campo.getTipo()) {
          case ASSINATURA -> signatario.getAssinatura();
          case RUBRICA -> signatario.getRubrica();
          case SELFIE -> signatario.getSelfie();
          case TEXTO, DATA, SELECAO, NUMERO, ANEXO -> null;
        };
        if (imagem != null || campo.preenchivel()) marcas.add(
          new ServicoCamposPdf.Marca(campo, signatario, imagem)
        );
      }
    }
    return marcas;
  }

  private EnvelopeAssinatura obterEnvelope(UUID id, String criador) {
    EnvelopeAssinatura envelope = envelopes
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    validarAtivo(envelope);
    if (
      !envelope.getCriador().equals(criador)
    ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return envelope;
  }

  private void validarAtivo(EnvelopeAssinatura envelope) {
    if (
      envelope.getExcluidoEm() != null || envelope.getCanceladoEm() != null
    ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  private SignatarioEnvelope obterConvite(String token) {
    return signatarios
      .findByResumoConvite(resumoToken(token))
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite inválido")
      );
  }

  private void validarPrazoConvite(EnvelopeAssinatura envelope) {
    Instant limite =
      envelope.getFinalizadoEm() == null
        ? envelope.getExpiraEm()
        : envelope.getFinalizadoEm().plusSeconds(7 * 86400);
    if (Instant.now().isAfter(limite)) {
      throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado");
    }
  }

  private String resumoToken(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Convite inválido"
      );
    }
    return resumo(token);
  }

  private String resumo(String texto) {
    return resumo(texto.getBytes(StandardCharsets.UTF_8));
  }

  private String resumoEnvio(
    String resumoDocumento,
    String textoAceite,
    byte[]... imagens
  ) throws Exception {
    MessageDigest digestor = MessageDigest.getInstance("SHA-256");
    digestor.update(resumoDocumento.getBytes(StandardCharsets.US_ASCII));
    digestor.update(textoAceite.getBytes(StandardCharsets.UTF_8));
    for (byte[] imagem : imagens) {
      byte[] conteudo = imagem == null ? new byte[0] : imagem;
      digestor.update(
        java.nio.ByteBuffer.allocate(4).putInt(conteudo.length).array()
      );
      digestor.update(conteudo);
    }
    return HexFormat.of().formatHex(digestor.digest());
  }

  private String resumo(byte[] dados) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(dados)
      );
    } catch (java.security.NoSuchAlgorithmException falha) {
      throw new IllegalStateException("SHA-256 indisponível", falha);
    }
  }

  private byte[] validarImagem(MultipartFile arquivo, boolean obrigatorio)
    throws IOException {
    if (arquivo == null || arquivo.isEmpty()) {
      if (obrigatorio) throw erro("Envie todas as imagens solicitadas");
      return null;
    }
    if (arquivo.getSize() > LIMITE_IMAGEM) throw erro(
      "Cada imagem deve ter até 2 MB"
    );
    String tipoArquivo = arquivo.getContentType();
    if (!"image/png".equals(tipoArquivo) && !"image/jpeg".equals(tipoArquivo)) {
      throw erro("Use imagens PNG ou JPEG");
    }
    byte[] dados;
    try (InputStream entrada = arquivo.getInputStream()) {
      dados = entrada.readNBytes(LIMITE_IMAGEM + 1);
    }
    if (dados.length > LIMITE_IMAGEM) throw erro(
      "Cada imagem deve ter até 2 MB"
    );
    try (
      ImageInputStream entrada = ImageIO.createImageInputStream(
        new java.io.ByteArrayInputStream(dados)
      )
    ) {
      if (entrada == null) throw erro("Imagem inválida");
      var leitores = ImageIO.getImageReaders(entrada);
      if (!leitores.hasNext()) throw erro("Imagem inválida");
      var leitor = leitores.next();
      try {
        leitor.setInput(entrada);
        int largura = leitor.getWidth(0);
        int altura = leitor.getHeight(0);
        if (
          largura < 1 ||
          altura < 1 ||
          largura > 2500 ||
          altura > 2500 ||
          (long) largura * altura > 6_000_000
        ) throw erro("Dimensão da imagem inválida");
      } finally {
        leitor.dispose();
      }
    }
    return dados;
  }

  private void preencherAnexo(CampoAssinatura campo, MultipartFile arquivo)
    throws Exception {
    if (arquivo == null || arquivo.isEmpty()) {
      if (campo.isObrigatorio()) {
        throw erro("Envie o anexo: " + campo.getNome());
      }
      return;
    }
    if (arquivo.getSize() > 2 * 1024 * 1024) {
      throw erro("Anexo deve ter até 2 MB");
    }
    String tipo = arquivo.getContentType();
    byte[] bytes;
    if ("application/pdf".equals(tipo)) {
      bytes = validador.validarPdf(arquivo);
      pdfs.paginas(bytes);
    } else if ("image/png".equals(tipo) || "image/jpeg".equals(tipo)) {
      bytes = validarImagem(arquivo, true);
    } else {
      throw erro("Anexe PDF, PNG ou JPEG");
    }
    String nome = java.util.Objects.toString(
      arquivo.getOriginalFilename(),
      "anexo"
    )
      .replace('\\', '_')
      .replace('/', '_')
      .replaceAll("\\p{Cntrl}", "_");
    nome = nome.substring(0, Math.min(120, nome.length()));
    String hash = HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256").digest(bytes)
    );
    campo.anexar(bytes, nome, tipo, hash);
  }

  private void preencherValor(CampoAssinatura campo, String valor) {
    if (valor == null || valor.length() > 2000) {
      throw erro("Valor de campo inválido ou maior que 2000 caracteres");
    }
    boolean selecaoPendente =
      campo.getTipo() == CampoAssinatura.Tipo.SELECAO && !valor.equals("true");
    if (campo.isObrigatorio() && (valor.isBlank() || selecaoPendente)) {
      throw erro("Preencha o campo obrigatório: " + campo.getNome());
    }
    if (!valor.isBlank()) {
      if (campo.getTipo() == CampoAssinatura.Tipo.DATA) {
        try {
          java.time.LocalDate.parse(valor);
        } catch (Exception e) {
          throw erro("Data inválida");
        }
      }
      if (campo.getTipo() == CampoAssinatura.Tipo.NUMERO) {
        try {
          new java.math.BigDecimal(valor);
        } catch (Exception e) {
          throw erro("Número inválido");
        }
      }
      if (
        campo.getTipo() == CampoAssinatura.Tipo.SELECAO &&
        !java.util.Set.of("true", "false").contains(valor)
      ) {
        throw erro("Seleção inválida");
      }
    }
    campo.preencher(valor);
  }

  private byte[] validarMarcaOpcional(
    MultipartFile arquivo,
    boolean permitido,
    boolean obrigatorio
  ) throws IOException {
    if (!permitido && arquivo != null && !arquivo.isEmpty()) throw erro(
      "Imagem sem campo correspondente"
    );
    return validarImagem(arquivo, obrigatorio);
  }

  private byte[] validarMarca(MultipartFile arquivo, boolean esperado)
    throws IOException {
    if (!esperado && arquivo != null && !arquivo.isEmpty()) {
      throw erro("Imagem sem campo correspondente");
    }
    return validarImagem(arquivo, esperado);
  }

  private ResponseStatusException erro(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  public record PedidoCriacao(
    String nomeEmpresa,
    String nomeAssinatura,
    List<SignatarioNovo> signatarios,
    String tipoDocumento,
    String declaracaoFormalidades,
    UUID idRequisicao,
    boolean empresaParticipante
  ) {
    public PedidoCriacao(String nomeEmpresa, String nomeAssinatura, List<SignatarioNovo> signatarios,
        String tipoDocumento, String declaracaoFormalidades, UUID idRequisicao) {
      this(nomeEmpresa, nomeAssinatura, signatarios, tipoDocumento, declaracaoFormalidades, idRequisicao, false);
    }
    public PedidoCriacao(String nomeEmpresa, List<SignatarioNovo> signatarios, String tipoDocumento,
        String declaracaoFormalidades, UUID idRequisicao) {
      this(nomeEmpresa, null, signatarios, tipoDocumento, declaracaoFormalidades, idRequisicao);
    }
  }

  public record SignatarioNovo(
    String nome,
    String email,
    String cpf,
    String whatsapp,
    String rg,
    String qualificacao,
    List<CampoNovo> campos,
    List<String> formaDeAutenticacao,
    List<String> formaDeNotificacao,
    String papel,
    String representado,
    String fundamentoRepresentacao
  ) {
    public SignatarioNovo {
      cpf = DadosPessoaisSignatario.cpf(cpf);
      rg = DadosPessoaisSignatario.rg(rg);
      whatsapp = DadosPessoaisSignatario.whatsapp(whatsapp);
    }

    public SignatarioNovo(String nome, String email, List<CampoNovo> campos,
        String papel, String representado, String fundamentoRepresentacao) {
      this(nome, email, null, null, null, null, campos, List.of(), List.of(), papel, representado, fundamentoRepresentacao);
    }

    public SignatarioNovo(
      String nome,
      String email,
      String cpf,
      String whatsapp,
      String rg,
      String qualificacao,
      List<CampoNovo> campos
    ) {
      this(nome, email, cpf, whatsapp, rg, qualificacao, campos, null, null, null, null, null);
    }
  }

  public record CampoNovo(
    CampoAssinatura.Tipo tipo,
    int pagina,
    double posicaoX,
    double posicaoY,
    double largura,
    double altura,
    Integer documento,
    String nome,
    Boolean obrigatorio,
    Integer rotacao
  ) {
    public CampoNovo {
      rotacao = rotacao == null ? 0 : rotacao;
    }

    public CampoNovo(CampoAssinatura.Tipo tipo, int pagina, double x, double y,
      double largura, double altura, Integer documento, String nome, Boolean obrigatorio) {
      this(tipo, pagina, x, y, largura, altura, documento, nome, obrigatorio, 0);
    }

    public CampoNovo(
      CampoAssinatura.Tipo tipo,
      int pagina,
      double x,
      double y,
      double largura,
      double altura,
      Integer documento
    ) {
      this(tipo, pagina, x, y, largura, altura, documento, null, true);
    }

    public CampoNovo(
      CampoAssinatura.Tipo tipo,
      int pagina,
      double x,
      double y,
      double largura,
      double altura
    ) {
      this(tipo, pagina, x, y, largura, altura, null, null, true);
    }
  }

  public record ConviteCriado(String nome, String email, String token) {}

  public record Criado(
    UUID identificador,
    int paginas,
    List<ConviteCriado> convites
  ) {}

  public record EstadoSignatario(
    String nome,
    String email,
    Instant assinadoEm,
    String situacao,
    String acessos,
    String autenticadoVia,
    List<HistoricoSignatario> historicos,
    List<AnexoResumo> anexos,
    UUID identificador,
    Instant recusadoEm,
    Instant autenticadoEm
  ) {}

  public record AnexoResumo(UUID campo, String nome) {}

  public record HistoricoSignatario(
    String status,
    String acao,
    String enderecoIp,
    String dispositivo,
    Instant criadoEm
  ) {}

  public record DocumentoEstado(
    UUID identificador,
    String titulo,
    int paginaInicio,
    int paginaFim,
    String hashOriginal
  ) {}

  public record EstadoEnvelope(
    UUID identificador,
    String nomeEmpresa,
    String nomeDocumento,
    Instant expiraEm,
    Instant finalizadoEm,
    List<EstadoSignatario> signatarios,
    String nome,
    String responsavel,
    String status,
    String quantidadeDocumentoAssinado,
    List<DocumentoEstado> documentos,
    String tipoDocumento,
    Instant reterEvidenciasAte,
    Instant canceladoEm,
    Instant empresaAssinouEm,
    boolean assinaturaAutomatica,
    boolean empresaParticipante
  ) {}

  public record CampoResposta(
    CampoAssinatura.Tipo tipo,
    int pagina,
    double posicaoX,
    double posicaoY,
    double largura,
    double altura,
    UUID id,
    String nome,
    boolean obrigatorio,
    String valor,
    int rotacao
  ) {
    public CampoResposta(
      CampoAssinatura.Tipo tipo,
      int pagina,
      double x,
      double y,
      double largura,
      double altura
    ) {
      this(tipo, pagina, x, y, largura, altura, null, "", true, null, 0);
    }
  }

  public record DadosConvite(
    String nomeEmpresa,
    String nomeDocumento,
    int paginas,
    String nomeSignatario,
    boolean assinado,
    boolean finalizado,
    List<CampoResposta> campos,
    List<String> formaDeAutenticacao,
    List<DocumentoConvite> documentos,
    String resumoDocumento,
    String emailSignatario,
    String textoAceite,
    Instant expiraEm,
    String papel,
    String representado,
    String fundamentoRepresentacao,
    Instant recusadoEm,
    String avisoPrivacidade
  ) {}

  public record DocumentoConvite(
    String nome,
    int paginaInicio,
    int paginaFim
  ) {}
}
