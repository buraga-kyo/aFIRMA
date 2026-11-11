package com.afirma.assina;

import static com.afirma.assina.BancoAssineai.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoAssineai {

  @org.springframework.beans.factory.annotation.Autowired
  private ColetaDadosAssinatura coleta;
  @org.springframework.beans.factory.annotation.Autowired
  private DadosResponsavelEmpresa responsavelEmpresa;

  @org.springframework.beans.factory.annotation.Autowired
  private CertificadoConfiguradoEmpresa certificadoEmpresa;
  private final BancoAssineai banco;
  private final IdentidadeAssineai identidades;
  private final ObjectMapper json;
  private final ValidadorPedidoAssinatura validador;
  private final ServicoCamposPdf paginas;
  private final PdfAssineai pdfs;
  private final ServicoAssinaturaPdf certificado;
  private final ServicoEntregaAssinatura entrega;
  private final String caminhoCertificado, senhaCertificado;
  @Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}")
  private boolean permitirCertificadoVencido;
  private static final SecureRandom SORTEADOR = new SecureRandom();

  public ServicoAssineai(
    BancoAssineai banco,
    IdentidadeAssineai identidades,
    ObjectMapper json,
    ValidadorPedidoAssinatura validador,
    ServicoCamposPdf paginas,
    PdfAssineai pdfs,
    ServicoAssinaturaPdf certificado,
    ServicoEntregaAssinatura entrega,
    @Value("${APP_ASSINEAI_CERTIFICADO_PATH:}") String caminho,
    @Value("${APP_ASSINEAI_CERTIFICADO_SENHA:${ASSINEAI_CERTIFICADO_SENHA:}}") String senha
  ) {
    this.banco = banco;
    this.identidades = identidades;
    this.json = json;
    this.validador = validador;
    this.paginas = paginas;
    this.pdfs = pdfs;
    this.certificado = certificado;
    this.entrega = entrega;
    this.caminhoCertificado = caminho;
    this.senhaCertificado = senha;
  }

  public void validarConvite(String token) { pessoa(token); }

  public int paginas(MultipartFile pdf) throws Exception {
    return paginas.paginas(validador.validarPdf(pdf));
  }

  public byte[] previsualizar(MultipartFile pdf, int pagina) throws Exception {
    return paginas.previsualizar(validador.validarPdf(pdf), pagina);
  }

  public Map<String, Object> recursos(String identidade) {
    return Map.of(
      "entregaDisponivel",
      entrega.disponivel(),
      "certificadoDisponivel",
      !caminhoCertificado.isBlank(),
      "usuario",
      texto(identidades.empresa(identidade), "EmpresaUsuario"),
      "empresa",
      identidades.empresa(identidade)
    );
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> criarFirma(
    List<MultipartFile> arquivos,
    ServicoLoteAssinatura.Pedido pedido,
    String identidade,
    String origem
  ) throws Exception {
    return criarFirma(arquivos, pedido, identidade, origem, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> criarFirma(List<MultipartFile> arquivos, ServicoLoteAssinatura.Pedido pedido,
      String identidade, String origem, ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    if (
      pedido == null ||
      pedido.documentos() == null ||
      pedido.signatarios() == null
    ) throw erro("Informe documentos e signatários");
    if (pedido.documentos().size() != arquivos.size()) throw erro(
      "Quantidade de documentos inválida"
    );
    var pessoas = new ArrayList<Map<String, Object>>();
    var identificadoresUnicos = new ArrayList<String>();
    for (var p : pedido.signatarios()) {
      if (p == null) throw erro("Signatário inválido");
      String identificadorUnico = UUID.randomUUID().toString();
      identificadoresUnicos.add(identificadorUnico);
      var pessoa = new LinkedHashMap<String, Object>();
      pessoa.put("GUID", identificadorUnico);
      pessoa.put("Nome", p.nome());
      pessoa.put("Email", p.email());
      pessoa.put("WhatsApp", p.whatsapp());
      pessoa.put("CPF", p.cpf());
      pessoa.put("RG", p.rg());
      pessoa.put("Qualificacao", p.qualificacao());
      pessoa.put("FormaDeAutenticacao", p.formaDeAutenticacao());
      pessoa.put("FormaDeNotificacao", p.formaDeNotificacao());
      pessoas.add(pessoa);
    }
    var documentosAtual = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < arquivos.size(); i++) {
      var campos = new ArrayList<Map<String, Object>>();
      for (int s = 0; s < pedido.signatarios().size(); s++) {
        var pessoa = pedido.signatarios().get(s);
        if (pessoa.campos() == null) continue;
        for (var c : pessoa.campos()) {
          if (
            c == null ||
            c.tipo() == null ||
            c.documento() == null ||
            c.documento() < 0 ||
            c.documento() >= arquivos.size()
          ) throw erro("Documento do campo inválido");
          if (c.documento() != i) continue;
          campos.add(
            new LinkedHashMap<>(
              Map.of(
                "GUID",
                identificadoresUnicos.get(s),
                "tipo",
                c.tipo().name(),
                "pagina",
                c.pagina(),
                "posicaoX",
                c.posicaoX(),
                "posicaoY",
                c.posicaoY(),
                "largura",
                c.largura(),
                "altura",
                c.altura()
              )
            )
          );
        }
      }
      documentosAtual.add(
        new LinkedHashMap<>(
          Map.of(
            "Nome",
            pedido.documentos().get(i).nome(),
            "ColecaoDeDivArrastavel",
            campos
          )
        )
      );
    }
    if (
      pedido.nomeEmpresa() != null && !pedido.nomeEmpresa().isBlank()
    ) identidades.salvar(
      identidade,
      Map.of("EmpresaNome", pedido.nomeEmpresa())
    );
    long id = criar(
      arquivos,
      documentosAtual,
      pessoas,
      pedido.nomeAssinatura(),
      identidade,
      origem, coletaEmpresa, dispositivoEmpresa, pedido.empresaParticipante()
    );
    var convites = new ArrayList<Map<String, Object>>();
    for (var p : pessoas)
      convites.add(
        Map.of(
          "nome",
          Objects.toString(p.get("Nome"), ""),
          "email",
          Objects.toString(p.get("Email"), ""),
          "token",
          p.get("GUID")
        )
      );
    return Map.of(
      "identificador",
      id,
      "convites",
      convites,
      "paginas",
      documentos(id)
        .stream()
        .mapToInt(d -> {
          try {
            return paginas.paginas((byte[]) d.get("DocumentoBuffer"));
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        })
        .sum()
    );
  }

  @Transactional(rollbackFor = Exception.class)
  public long criar(
    List<MultipartFile> arquivos,
    List<Map<String, Object>> documentos,
    List<Map<String, Object>> pessoas,
    String nome,
    String identidade,
    String origem
  ) throws Exception {
    return criar(arquivos, documentos, pessoas, nome, identidade, origem, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public long criar(List<MultipartFile> arquivos, List<Map<String, Object>> documentos, List<Map<String, Object>> pessoas,
      String nome, String identidade, String origem, ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    return criar(arquivos, documentos, pessoas, nome, identidade, origem, coletaEmpresa, dispositivoEmpresa, false);
  }

  @Transactional(rollbackFor = Exception.class)
  public long criar(List<MultipartFile> arquivos, List<Map<String, Object>> documentos, List<Map<String, Object>> pessoas,
      String nome, String identidade, String origem, ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa,
      boolean empresaParticipante) throws Exception {
    if (
      nome == null ||
      nome.isBlank() ||
      nome.length() > 255 ||
      arquivos.isEmpty() ||
      arquivos.size() > 10 ||
      arquivos.size() != documentos.size() ||
      (pessoas.isEmpty() && !empresaParticipante) ||
      pessoas.size() > 20
    ) throw erro(
      "Informe nome da assinatura, de 1 a 10 PDFs e de 1 a 20 signatários"
    );
    pessoas = pessoas.stream().map(pessoa -> pessoa == null ? null : new LinkedHashMap<String, Object>(pessoa)).map(pessoa -> (Map<String, Object>) pessoa).toList();
    var identificadoresUnicos = new HashSet<String>();
    for (var p : pessoas) {
      validarPessoa(p);
      String identificadorUnico = Objects.toString(p.get("GUID"), "");
      if (!identificadorUnico.matches("[A-Za-z0-9_-]{1,200}") || !identificadoresUnicos.add(identificadorUnico)) throw erro(
        "Identificador do signatário inválido ou duplicado"
      );
    }
    for (String identificadorUnico : identificadoresUnicos.stream().sorted().toList()) {
      banco.um("SELECT pg_advisory_xact_lock(hashtext(:g))", Map.of("g", identificadorUnico));
      if (
        banco.um(
          "SELECT \"SignatarioId\" FROM \"AssinaturaSignatarios\" WHERE \"SignatarioTokenLinkAssinatura\"=:g LIMIT 1",
          Map.of("g", identificadorUnico)
        ) != null
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Esse identificador de convite já existe"
      );
    }
    var originais = new ArrayList<byte[]>();
    int total = 0,
      totalCampos = 0;
    long bytes = 0;
    for (int i = 0; i < arquivos.size(); i++) {
      var d = documentos.get(i);
      String titulo = Objects.toString(d.get("Nome"), "").strip();
      if (titulo.isEmpty() || titulo.length() > 255) throw erro(
        "Informe o nome de cada documento"
      );
      byte[] pdf = validador.validarPdf(arquivos.get(i));
      total += paginas.paginas(pdf);
      bytes += pdf.length;
      if (total > 100 || bytes > 20L * 1024 * 1024) throw erro(
        "Limite de 100 páginas e 20 MB por assinatura"
      );
      var campos = lista(d.get("ColecaoDeDivArrastavel"));
      for (var c : campos) {
        if (!identificadoresUnicos.contains(Objects.toString(c.get("GUID"), ""))) throw erro(
          "Campo de um signatário desconhecido"
        );
        pdfs.normalizar(c, pdf);
      }
      totalCampos += campos.size();
      if (totalCampos > 100) throw erro("Limite de 100 campos");
      originais.add(pdf);
    }
    long empresaId = id(identidades.empresa(identidade), "EmpresaId");
    long assinatura = banco.inserir(
      "Assinaturas",
      "AssinaturaId",
      new LinkedHashMap<>(
        Map.of(
          "AssinaturaNome",
          nome.strip(),
          "AssinaturaResponsavel",
          texto(identidades.empresa(identidade), "EmpresaUsuario"),
          "AssinaturaStatus",
          "Pendente",
          "AssinaturaQuantidadeDocumentoAssinado",
          "0"
        )
      )
    );
    banco.executar(
      "INSERT INTO firma_assineai_controle(assinatura_id,criador,empresa_id,assinatura_automatica,empresa_participante) VALUES(:a,:i,:e,:automatica,:participante)",
      Map.of("a", assinatura, "i", identidade, "e", empresaId, "automatica", !empresaParticipante || Boolean.TRUE.equals(identidades.empresa(identidade).get("EmpresaAssinaturaAutomatica")), "participante", empresaParticipante)
    );
    for (int i = 0; i < arquivos.size(); i++) {
      String token = UUID.randomUUID().toString();
      var d = new LinkedHashMap<String, Object>();
      d.put("AssinaturaId", assinatura);
      d.put("DocumentoTitulo", documentos.get(i).get("Nome"));
      d.put("DocumentoBuffer", originais.get(i));
      d.put(
        "DocumentoColecaoDeDivArrastavel",
        lista(documentos.get(i).get("ColecaoDeDivArrastavel"))
      );
      d.put("DocumentoToken", token);
      d.put(
        "DocumentoLinkAutenticacao",
        origem + "/web-panels/autenticidade-documento/" + token
      );
      d.putAll(pdfs.prova(originais.get(i)));
      banco.inserir("AssinaturaDocumentos", "DocumentoId", d);
    }
    for (var pessoa : pessoas) {
      var p = new LinkedHashMap<String, Object>();
      p.put("AssinaturaId", assinatura);
      for (String campo : List.of(
        "Nome",
        "Email",
        "CPF",
        "RG",
        "WhatsApp",
        "Qualificacao"
      ))
        p.put(
          "Signatario" + campo,
          Objects.toString(pessoa.get(campo), "").strip()
        );
      p.put(
        "SignatarioFormaDeAutenticacao",
        metodos(pessoa.get("FormaDeAutenticacao"))
      );
      p.put(
        "SignatarioFormaDeNotificacao",
        metodos(pessoa.get("FormaDeNotificacao"))
      );
      p.put("SignatarioTokenLinkAssinatura", pessoa.get("GUID"));
      p.put(
        "SignatarioLinkAssinatura",
        origem + "/web-panels/assinar-documentos/" + pessoa.get("GUID")
      );
      p.put("SignatarioAssinou", false);
      p.put("SignatarioSituacaoAssinatura", "Não abriu o link");
      p.put("SignatarioQuantidadeDeAcessosNoLinkDeAssinatura", "0");
      p.put(
        "SignatarioMensagemSobreVisualizacaoDoLinkDeAssinatura",
        "Não abriu o link de assinatura"
      );
      long signatario = banco.inserir(
        "AssinaturaSignatarios",
        "SignatarioId",
        p
      );
      evento(signatario, "naoabriulink", "convite_criado", null, null);
    }
    gerarDocumentos(assinatura, null, null, coletaEmpresa, dispositivoEmpresa, identidade);
    return assinatura;
  }

  private void validarPessoa(Map<String, Object> p) {
    if (p == null) throw erro("Signatário inválido");
    p.put("CPF", DadosPessoaisSignatario.cpf(Objects.toString(p.get("CPF"), "")));
    p.put("RG", DadosPessoaisSignatario.rg(Objects.toString(p.get("RG"), "")));
    p.put("WhatsApp", DadosPessoaisSignatario.whatsapp(Objects.toString(p.get("WhatsApp"), "")));
    String nome = Objects.toString(p.get("Nome"), "").strip(),
      email = Objects.toString(p.get("Email"), "").strip(),
      whats = Objects.toString(p.get("WhatsApp"), "").replaceAll("[^0-9]", "");
    if (
      nome.isEmpty() ||
      nome.length() > 255 ||
      email.length() > 254 ||
      (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    ) throw erro("Nome ou e-mail inválido");
    var autenticacao = metodos(p.get("FormaDeAutenticacao"));
    var avisos = metodos(p.get("FormaDeNotificacao"));
    if (
      !Set.of("tela", "email", "whatsapp", "selfie").containsAll(autenticacao) ||
      !Set.of("email", "whatsapp").containsAll(avisos)
    ) throw erro("Método inválido");
    if (
      (autenticacao.contains("email") || avisos.contains("email")) && email.isEmpty()
    ) throw erro("Informe e-mail para o canal escolhido");
    if (
      (autenticacao.contains("whatsapp") || avisos.contains("whatsapp")) &&
      whats.isEmpty()
    ) throw erro("Informe WhatsApp com DDD");
    for (String campo : List.of("CPF", "RG", "WhatsApp", "Qualificacao"))
      if (Objects.toString(p.get(campo), "").length() > 255) throw erro(
        "Campo pessoal muito longo"
      );
  }

  public List<Map<String, Object>> documentos(long assinatura) {
    return banco.listar(
      "SELECT * FROM \"AssinaturaDocumentos\" WHERE \"AssinaturaId\"=:id ORDER BY \"DocumentoId\"",
      Map.of("id", assinatura)
    );
  }

  public List<Map<String, Object>> pessoas(long assinatura) {
    return banco.listar(
      "SELECT * FROM \"AssinaturaSignatarios\" WHERE \"AssinaturaId\"=:id ORDER BY \"SignatarioId\"",
      Map.of("id", assinatura)
    );
  }

  public Map<String, Object> pessoa(String token) {
    if (
      token == null || !token.matches("[A-Za-z0-9_-]{1,200}")
    ) throw naoEncontrado();
    var p = banco.um(
      "SELECT * FROM \"AssinaturaSignatarios\" WHERE \"SignatarioTokenLinkAssinatura\"=:t",
      Map.of("t", token)
    );
    if (
      p == null || !(p.get("AssinaturaId") instanceof Number)
    ) throw naoEncontrado();
    ativo(id(p, "AssinaturaId"));
    return p;
  }

  private void ativo(long id) {
    var c = banco.um(
      "SELECT excluido_em FROM firma_assineai_controle WHERE assinatura_id=:id",
      Map.of("id", id)
    );
    if (c != null && c.get("excluido_em") != null) throw naoEncontrado();
  }

  public void dono(long id, String identidade) {
    ativo(id);
    var c = banco.um(
      "SELECT * FROM firma_assineai_controle WHERE assinatura_id=:id",
      Map.of("id", id)
    );
    if (c == null) {
      if (!identidades.podeConsultarLegada(identidade)) throw naoEncontrado();
    } else if (
      !identidade.equals(c.get("criador")) &&
      ((Number) c.get("empresa_id")).longValue() !=
        id(identidades.empresa(identidade), "EmpresaId")
    ) throw naoEncontrado();
  }

  @Transactional
  public Map<String, Object> detalhe(long assinatura, String identidade) {
    dono(assinatura, identidade);
    var a = banco.um(
      "SELECT * FROM \"Assinaturas\" WHERE \"AssinaturaId\"=:id",
      Map.of("id", assinatura)
    );
    if (a == null) throw naoEncontrado();
    var ps = pessoas(assinatura);
    for (var p : ps) {
      p.remove("SignatarioTokenEmail");
      p.remove("SignatarioTokenWhatsApp");
      p.remove("SignatarioSelfieBase64");
      p.remove("SignatarioAssinaturaEscritaBase64");
      p.put(
        "historicos",
        banco.listar(
          "SELECT * FROM \"SignatarioHistoricos\" WHERE \"SignatarioId\"=:id ORDER BY \"createdAt\",\"SignatarioHistoricoId\"",
          Map.of("id", id(p, "SignatarioId"))
        )
      );
    }
    a.put("AssinaturaSignatarios", ps);
    a.put(
      "AssinaturaDocumentos",
      documentos(assinatura).stream().map(this::metadados).toList()
    );
    return a;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> participanteEmpresa(long assinatura) {
    var empresa = banco.um("SELECT e.\"EmpresaNome\",c.empresa_participante,(SELECT min(d.\"DocumentoEmpresaAssinouEm\") FROM \"AssinaturaDocumentos\" d WHERE d.\"AssinaturaId\"=c.assinatura_id) AS assinou_em FROM firma_assineai_controle c JOIN \"Empresas\" e ON e.\"EmpresaId\"=c.empresa_id WHERE c.assinatura_id=:id", Map.of("id", assinatura));
    if (empresa == null || !Boolean.TRUE.equals(empresa.get("empresa_participante"))) return null;
    return Map.of("SignatarioNome", texto(empresa, "EmpresaNome"), "SignatarioEmail", "", "SignatarioAssinou", empresa.get("assinou_em") != null);
  }

  @Transactional
  public List<Map<String, Object>> listar(String identidade) {
    long empresaId = id(identidades.empresa(identidade), "EmpresaId");
    return banco.listar(
      "SELECT a.* FROM \"Assinaturas\" a LEFT JOIN firma_assineai_controle c ON c.assinatura_id=a.\"AssinaturaId\" WHERE c.excluido_em IS NULL AND (c.criador=:i OR c.empresa_id=:e OR (c.assinatura_id IS NULL AND :legado)) ORDER BY a.\"AssinaturaId\" DESC",
      Map.of(
        "i",
        identidade,
        "e",
        empresaId,
        "legado",
        identidades.podeConsultarLegada(identidade)
      )
    );
  }

  public Map<String, Object> metadados(Map<String, Object> documento) {
    var d = new LinkedHashMap<>(documento);
    d.remove("DocumentoBuffer");
    d.remove("DocumentoAssinadoBuffer");
    d.remove("DocumentoBaseEmpresa");
    return d;
  }

  @Transactional
  public Map<String, Object> convite(
    String token,
    String ip,
    String dispositivo
  ) throws Exception {
    var anterior = pessoa(token);
    long assinatura = id(anterior, "AssinaturaId");
    bloquear(assinatura);
    var p = pessoa(token);
    long acessos;
    try {
      acessos = Long.parseLong(
        texto(p, "SignatarioQuantidadeDeAcessosNoLinkDeAssinatura")
      );
    } catch (NumberFormatException e) {
      acessos = 0;
    }
    acessos++;
    var valores = new LinkedHashMap<String, Object>();
    valores.put(
      "SignatarioQuantidadeDeAcessosNoLinkDeAssinatura",
      Long.toString(acessos)
    );
    valores.put(
      "SignatarioMensagemSobreVisualizacaoDoLinkDeAssinatura",
      "Acessou " + acessos + " vez(es) o link de assinatura"
    );
    if (!Boolean.TRUE.equals(p.get("SignatarioAssinou"))) valores.put(
      "SignatarioSituacaoAssinatura",
      "Visualizou o documento"
    );
    banco.atualizar(
      "AssinaturaSignatarios",
      "SignatarioId",
      id(p, "SignatarioId"),
      valores
    );
    evento(
      id(p, "SignatarioId"),
      "visualizado",
      "link_aberto",
      ip,
      dispositivo
    );
    var a = banco.um(
      "SELECT a.*,e.\"EmpresaNome\",e.\"EmpresaTemaTelaAssinatura\" FROM \"Assinaturas\" a LEFT JOIN firma_assineai_controle c ON c.assinatura_id=a.\"AssinaturaId\" LEFT JOIN \"Empresas\" e ON e.\"EmpresaId\"=c.empresa_id WHERE a.\"AssinaturaId\"=:id",
      Map.of("id", assinatura)
    );
    var documentosAtual = new ArrayList<Map<String, Object>>();
    for (var d : documentos(assinatura)) {
      var meta = metadados(d);
      meta.put("paginas", quantidadePaginas(d));
      meta.put(
        "campos",
        lista(d.get("DocumentoColecaoDeDivArrastavel"))
          .stream()
          .filter(c -> token.equals(Objects.toString(c.get("GUID"), "")))
          .map(c -> {
            try {
              return pdfs.normalizar(c, (byte[]) d.get("DocumentoBuffer"));
            } catch (Exception e) {
              throw new IllegalArgumentException("Campo legado inválido", e);
            }
          })
          .toList()
      );
      documentosAtual.add(meta);
    }
    var pessoaPublica = new LinkedHashMap<String, Object>();
    for (String chave : List.of(
      "AssinaturaId",
      "SignatarioNome",
      "SignatarioEmail",
      "SignatarioWhatsApp",
      "SignatarioAssinou",
      "SignatarioFormaDeAutenticacao",
      "SignatarioQualificacao"
    ))
      pessoaPublica.put(chave, p.get(chave));
    return Map.of(
      "Signatario",
      pessoaPublica,
      "Documentos",
      documentosAtual,
      "empresa",
      Objects.toString(a.get("EmpresaNome"), "aFIRMA"),
      "tema",
      Objects.toString(a.get("EmpresaTemaTelaAssinatura"), "firma"),
      "certificadoDisponivel",
      !caminhoCertificado.isBlank()
    );
  }

  public Map<String, Object> documentoConvite(String token, long documento) {
    long a = id(pessoa(token), "AssinaturaId");
    var d = banco.um(
      "SELECT * FROM \"AssinaturaDocumentos\" WHERE \"DocumentoId\"=:d AND \"AssinaturaId\"=:a",
      Map.of("d", documento, "a", a)
    );
    if (d == null) throw naoEncontrado();
    return d;
  }

  public int quantidadePaginas(Map<String, Object> documento) throws Exception {
    return pdfs.paginasDocumento(disponivel(documento));
  }

  public byte[] disponivel(Map<String, Object> d) {
    return d.get("DocumentoAssinadoBuffer") instanceof byte[] b
      ? b
      : (byte[]) d.get("DocumentoBuffer");
  }

  public byte[] pagina(String token, long documento, int pagina)
    throws Exception {
    return paginas.previsualizar(
      disponivel(documentoConvite(token, documento)),
      pagina
    );
  }

  @Transactional
  public void enviarCodigo(
    String token,
    String canal,
    String ip,
    String dispositivo
  ) {
    emitirCodigo(token, canal, ip, dispositivo, false);
  }

  @Transactional
  public void enviarCodigoLegado(String token, String ip, String dispositivo) {
    var p = pessoa(token);
    var disponiveis = new ArrayList<String>();
    if (!texto(p, "SignatarioEmail").isBlank()) disponiveis.add("email");
    if (!texto(p, "SignatarioWhatsApp").isBlank()) disponiveis.add("whatsapp");
    if (disponiveis.isEmpty()) throw erro(
      "O signatário não tem contato para entrega do código"
    );
    String canal = metodos(p.get("SignatarioFormaDeAutenticacao"))
      .stream()
      .filter(disponiveis::contains)
      .findFirst()
      .orElse(disponiveis.getFirst());
    emitirCodigo(token, canal, ip, dispositivo, true);
  }

  private void emitirCodigo(
    String token,
    String canal,
    String ip,
    String dispositivo,
    boolean legado
  ) {
    var p = pessoa(token);
    if (
      (!legado &&
        !metodos(p.get("SignatarioFormaDeAutenticacao")).contains(canal)) ||
      !Set.of("email", "whatsapp").contains(canal)
    ) throw erro("Canal não solicitado");
    bloquear(id(p, "AssinaturaId"));
    long id = id(p, "SignatarioId");
    var anterior = banco.um(
      "SELECT * FROM firma_assineai_codigo WHERE signatario_id=:id AND canal=:c FOR UPDATE",
      Map.of("id", id, "c", canal)
    );
    if (
      anterior != null &&
      ((java.sql.Timestamp) anterior.get("enviado_em"))
        .toInstant()
        .plusSeconds(60)
        .isAfter(Instant.now())
    ) throw new ResponseStatusException(
      HttpStatus.TOO_MANY_REQUESTS,
      "Aguarde um minuto para reenviar"
    );
    String codigo = String.format("%06d", SORTEADOR.nextInt(1_000_000));
    entrega.enviar(
      canal,
      texto(
        p,
        canal.equals("email") ? "SignatarioEmail" : "SignatarioWhatsApp"
      ),
      "codigo",
      codigo
    );
    banco.executar(
      "INSERT INTO firma_assineai_codigo(signatario_id,canal,resumo,enviado_em,expira_em) VALUES(:id,:c,:r,now(),now()+interval '10 minutes') ON CONFLICT(signatario_id,canal) DO UPDATE SET resumo=excluded.resumo,enviado_em=now(),expira_em=excluded.expira_em,tentativas=0,verificado=false",
      Map.of("id", id, "c", canal, "r", hash(id + ":" + canal + ":" + codigo))
    );
    evento(id, "aguardando_codigo", "codigo_enviado_" + canal, ip, dispositivo);
  }

  @Transactional
  public boolean verificarCodigo(
    String token,
    String codigo,
    String canal,
    String ip,
    String dispositivo
  ) {
    var p = pessoa(token);
    if (codigo == null || !codigo.matches("[0-9]{6}")) return false;
    long id = id(p, "SignatarioId");
    bloquear(id(p, "AssinaturaId"));
    for (String c : List.of("email", "whatsapp")) {
      if (canal != null && !canal.equals(c)) continue;
      var desafio = banco.um(
        "SELECT *,expira_em>now() AS ativo FROM firma_assineai_codigo WHERE signatario_id=:id AND canal=:c FOR UPDATE",
        Map.of("id", id, "c", c)
      );
      boolean valido = false;
      if (desafio != null) {
        if (
          !Boolean.TRUE.equals(desafio.get("ativo")) ||
          (!Boolean.TRUE.equals(desafio.get("verificado")) &&
            ((Number) desafio.get("tentativas")).intValue() >= 5)
        ) continue;
        valido = MessageDigest.isEqual(
          texto(desafio, "resumo").getBytes(StandardCharsets.UTF_8),
          hash(id + ":" + c + ":" + codigo).getBytes(StandardCharsets.UTF_8)
        );
        if (Boolean.TRUE.equals(desafio.get("verificado"))) {
          if (valido) return true;
          else continue;
        }
        banco.executar(
          "UPDATE firma_assineai_codigo SET tentativas=tentativas+1,verificado=verificado OR :v WHERE signatario_id=:id AND canal=:c",
          Map.of("id", id, "c", c, "v", valido)
        );
      } else {
        String antigo = texto(
          p,
          c.equals("email") ? "SignatarioTokenEmail" : "SignatarioTokenWhatsApp"
        );
        valido =
          !antigo.isBlank() &&
          MessageDigest.isEqual(
            antigo.getBytes(StandardCharsets.UTF_8),
            codigo.getBytes(StandardCharsets.UTF_8)
          );
        if (!antigo.isBlank()) banco.executar(
          "INSERT INTO firma_assineai_codigo(signatario_id,canal,resumo,enviado_em,expira_em,tentativas,verificado) VALUES(:id,:c,:r,now(),now()+interval '10 minutes',1,:v)",
          Map.of(
            "id",
            id,
            "c",
            c,
            "r",
            hash(id + ":" + c + ":" + antigo),
            "v",
            valido
          )
        );
      }
      evento(
        id,
        valido ? "autenticado" : "codigo_recusado",
        (valido ? "codigo_validado_" : "codigo_recusado_") + c,
        ip,
        dispositivo
      );
      if (valido) return true;
    }
    return false;
  }

  private List<String> canaisValidados(Map<String, Object> p) {
    long id = id(p, "SignatarioId");
    return banco
      .listar(
        "SELECT canal FROM firma_assineai_codigo WHERE signatario_id=:id AND verificado AND expira_em>now()",
        Map.of("id", id)
      )
      .stream()
      .map(d -> texto(d, "canal"))
      .toList();
  }

  @Transactional(rollbackFor = Exception.class)
  public List<Map<String, Object>> assinar(
    String token,
    Map<String, Object> dados,
    String ip,
    String dispositivo
  ) throws Exception {
    return assinar(token, dados, dispositivo, coleta.receber(ip, dados.get("userLocation"), Objects.toString(dados.get("ipNavegador"), "")));
  }

  @Transactional(rollbackFor = Exception.class)
  public List<Map<String, Object>> assinar(String token, Map<String, Object> dados, String dispositivo,
      ColetaDadosAssinatura.Dados coletados) throws Exception {
    coletados.exigirRecente();
    String ip = coletados.endereco();
    var p = pessoa(token);
    long assinatura = id(p, "AssinaturaId");
    bloquear(assinatura);
    p = pessoa(token);
    if (
      Boolean.TRUE.equals(p.get("SignatarioAssinou"))
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Assinatura já registrada"
    );
    var autenticacao = metodos(p.get("SignatarioFormaDeAutenticacao"));
    var canais = canaisValidados(p);
    if (
      autenticacao.stream().anyMatch(c -> Set.of("email", "whatsapp").contains(c)) &&
      canais.isEmpty()
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Valide um código por e-mail ou WhatsApp"
    );
    if (!Boolean.TRUE.equals(dados.get("aceite"))) throw erro(
      "Confirme a leitura dos documentos"
    );
    var tipos = new HashSet<String>();
    for (var d : documentos(assinatura))
      for (var c : lista(d.get("DocumentoColecaoDeDivArrastavel")))
        if (token.equals(Objects.toString(c.get("GUID"), ""))) tipos.add(
          Objects.toString(c.getOrDefault("tipo", "ASSINATURA"))
        );
    byte[] desenho = pdfs.imagem(
      dados.get("SignatarioAssinaturaEscritaBase64"),
      autenticacao.contains("tela") || tipos.contains("ASSINATURA")
    );
    byte[] selfie = pdfs.imagem(
      dados.get("selfie"),
      autenticacao.contains("selfie") || tipos.contains("SELFIE")
    );
    byte[] rubrica = pdfs.imagem(
      dados.get("rubrica"),
      tipos.contains("RUBRICA")
    );
    var valores = new LinkedHashMap<String, Object>();
    valores.put("SignatarioAssinou", true);
    valores.put("SignatarioSituacaoAssinatura", "Assinado");
    valores.put("SignatarioDataAssinatura", Instant.now().toString());
    valores.put("SignatarioIp", limitar(ip, 64));
    valores.put("SignatarioDispositivo", limitar(dispositivo, 1024));
    var usados = new ArrayList<>(canais);
    if (desenho != null) usados.add("tela");
    if (selfie != null) usados.add("selfie");
    if (rubrica != null) usados.add("rubrica");
    if (usados.isEmpty()) usados.add("aceite");
    valores.put("SignatarioFirmadoVia", String.join(",", usados));
    valores.put(
      "SignatarioAssinaturaEscritaBase64",
      desenho == null ? null : Base64.getEncoder().encodeToString(desenho)
    );
    valores.put(
      "SignatarioSelfieBase64",
      selfie == null ? null : Base64.getEncoder().encodeToString(selfie)
    );
    valores.put("SignatarioGeolocalizacao", coleta.serializar(coletados));
    valores.put("SignatarioIpConexao", coletados.ipConexao());
    valores.put("SignatarioOrigemIp", coletados.origemIp());
    banco.atualizar(
      "AssinaturaSignatarios",
      "SignatarioId",
      id(p, "SignatarioId"),
      valores
    );
    if (rubrica != null) banco.executar(
      "INSERT INTO firma_assineai_marca(signatario_id,rubrica) VALUES(:id,:r) ON CONFLICT(signatario_id) DO UPDATE SET rubrica=excluded.rubrica",
      Map.of("id", id(p, "SignatarioId"), "r", rubrica)
    );
    evento(
      id(p, "SignatarioId"),
      "assinado",
      "assinatura_confirmada",
      ip,
      dispositivo
    );
    gerarDocumentos(assinatura, null, null);
    return documentos(assinatura);
  }

  private void gerarDocumentos(long assinatura, byte[] pfx, char[] senha)
    throws Exception {
    gerarDocumentos(assinatura, pfx, senha, null, null, null);
  }

  private void gerarDocumentos(long assinatura, byte[] pfx, char[] senha, ColetaDadosAssinatura.Dados coletaEmpresa,
      String dispositivoEmpresa, String operador) throws Exception {
    var ps = pessoas(assinatura);
    var empresa = banco.um("SELECT e.*,c.assinatura_automatica,c.dados_assinatura_empresa,c.empresa_participante FROM \"Empresas\" e JOIN firma_assineai_controle c ON c.empresa_id=e.\"EmpresaId\" WHERE c.assinatura_id=:id", Map.of("id",assinatura));
    var modelo = TemplatesEmpresa.ativo(empresa == null ? null : empresa.get("EmpresaTemplates"));
    String nomeEmpresa = empresa == null ? "Sua empresa" : Objects.toString(empresa.get("EmpresaNome"),"Sua empresa");
    var rubricas = new HashMap<Long, byte[]>();
    for (var p : ps) {
      var r = banco.um(
        "SELECT rubrica FROM firma_assineai_marca WHERE signatario_id=:id",
        Map.of("id", id(p, "SignatarioId"))
      );
      if (r != null) rubricas.put(
        id(p, "SignatarioId"),
        (byte[]) r.get("rubrica")
      );
    }
    byte[] chave = pfx;
    char[] segredo = senha;
    CertificadoConfiguradoEmpresa.Material materialConfigurado = null;
    boolean basesPreservadas = documentos(assinatura).stream().allMatch(documento -> documento.get("DocumentoBaseEmpresa") != null);
    if (chave == null && (!basesPreservadas || ps.stream().allMatch(pessoa -> Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"))))
        && empresa != null && Boolean.TRUE.equals(empresa.get("assinatura_automatica"))) {
      materialConfigurado = certificadoEmpresa.carregar(id(empresa, "EmpresaId"));
      chave = materialConfigurado.dados();
      segredo = materialConfigurado.senha();
    }
    boolean empresaParticipante = empresa != null && Boolean.TRUE.equals(empresa.get("empresa_participante"));
    var registroEmpresa = new LinkedHashMap<String, Object>();
    if (empresa != null && empresa.get("dados_assinatura_empresa") instanceof Map<?, ?> preservados)
      preservados.forEach((campo, valor) -> registroEmpresa.put(campo.toString(), valor));
    registroEmpresa.remove("operador");
    registroEmpresa.put("SignatarioNome", nomeEmpresa);
    registroEmpresa.put("SignatarioQualificacao", "Empresa");
    registroEmpresa.put("SignatarioAssinou", chave != null);
    registroEmpresa.put("SignatarioDataAssinatura", chave == null ? "Pendente" : java.time.OffsetDateTime.now().toString());
    registroEmpresa.put("SignatarioFirmadoVia", chave == null ? "Assinatura da empresa pendente" : "Certificado digital");
    try {
      if (chave != null && !basesPreservadas && empresaParticipante) {
        if (empresa == null) throw new ErroAssinatura("Vincule a empresa e seu responsável antes de assinar este documento");
        var dados = responsavelEmpresa.preparar(id(empresa, "EmpresaId"), coletaEmpresa, dispositivoEmpresa, operador, "Criação ou assinatura manual dos documentos");
        banco.executar("UPDATE firma_assineai_controle SET dados_assinatura_empresa=CAST(:dados AS jsonb) WHERE assinatura_id=:id",
            Map.of("dados", json.writeValueAsString(dados), "id", assinatura));
        registroEmpresa.putAll(dados);
      }
      registroEmpresa.remove("operador");
      for (var d : documentos(assinatura)) {
        byte[] base = (byte[]) d.get("DocumentoBaseEmpresa");
        if (base == null && chave != null) {
          var titular = certificado.consultarCertificado(chave, segredo, permitirCertificadoVencido);
          base = certificado.assinar((byte[]) d.get("DocumentoBuffer"), chave, segredo, permitirCertificadoVencido);
          var dadosBase = new LinkedHashMap<String, Object>();
          dadosBase.put("DocumentoBaseEmpresa", base);
          dadosBase.put("DocumentoEmpresaAssinouEm", java.time.OffsetDateTime.now());
          dadosBase.put("DocumentoEmpresaTitular", titular.titular());
          banco.atualizar("AssinaturaDocumentos", "DocumentoId", id(d, "DocumentoId"), dadosBase);
          d.putAll(dadosBase);
        }
        var fonte = new LinkedHashMap<>(d);
        if (base != null) fonte.put("DocumentoBuffer", base);
        registroEmpresa.put("SignatarioAssinou", base != null);
        registroEmpresa.put("SignatarioDataAssinatura", Objects.toString(d.get("DocumentoEmpresaAssinouEm"), "Pendente"));
        registroEmpresa.put("SignatarioFirmadoVia", base == null ? "Assinatura da empresa pendente"
            : "Certificado digital");
        byte[] pdf = pdfs.aplicar(
          fonte,
          ps,
          lista(d.get("DocumentoColecaoDeDivArrastavel")),
          rubricas,
          modelo,
          nomeEmpresa,
          empresaParticipante ? registroEmpresa : null
        );
        if (chave != null) pdf = certificado.assinar(
          pdf,
          chave,
          segredo,
          permitirCertificadoVencido
        );
        banco.atualizar(
          "AssinaturaDocumentos",
          "DocumentoId",
          id(d, "DocumentoId"),
          new LinkedHashMap<>(Map.of("DocumentoAssinadoBuffer", pdf))
        );
      }
      boolean todos = ps
        .stream()
        .allMatch(p -> Boolean.TRUE.equals(p.get("SignatarioAssinou")));
      int completos = todos ? documentos(assinatura).size() : 0;
      banco.atualizar(
        "Assinaturas",
        "AssinaturaId",
        assinatura,
        new LinkedHashMap<>(
          Map.of(
            "AssinaturaStatus",
            todos
              ? chave != null
                ? "Finalizada"
                : "Aguardando certificado"
              : "Em Andamento",
            "AssinaturaQuantidadeDocumentoAssinado",
            Integer.toString(completos)
          )
        )
      );
      if (todos && chave != null) {
        banco.executar(
          "UPDATE firma_assineai_controle SET certificado_em=now() WHERE assinatura_id=:id",
          Map.of("id", assinatura)
        );
        for (var p : ps)
          evento(
            id(p, "SignatarioId"),
            "assinado",
            "certificado_aplicado",
            null,
            null
          );
      }
    } finally {
      if (materialConfigurado != null) materialConfigurado.close();
      else if (segredo != senha && segredo != null) Arrays.fill(segredo, '\0');
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public void certificar(
    long assinatura,
    String identidade,
    MultipartFile pfx,
    String senha
  ) throws Exception {
    certificar(assinatura, identidade, pfx, senha, null, null);
  }

  @Transactional(rollbackFor = Exception.class)
  public void certificar(long assinatura, String identidade, MultipartFile pfx, String senha,
      ColetaDadosAssinatura.Dados coletaEmpresa, String dispositivoEmpresa) throws Exception {
    dono(assinatura, identidade);
    bloquear(assinatura);
    if (
      pfx == null ||
      pfx.isEmpty() ||
      pfx.getSize() > 2 * 1024 * 1024
    ) throw erro("Informe certificado e senha");
    byte[] dadosCertificado = validador.validarCertificado(pfx);
    char[] segredo = null;
    try {
      segredo = validador.resolverSenhaCertificado(senha).toCharArray();
      gerarDocumentos(assinatura, dadosCertificado, segredo, coletaEmpresa, dispositivoEmpresa, identidade);
    } finally {
      if (segredo != null) Arrays.fill(segredo, '\0');
      Arrays.fill(dadosCertificado, (byte) 0);
    }
  }

  public Map<String, Object> autenticidade(String token) {
    var d = banco.um(
      "SELECT * FROM \"AssinaturaDocumentos\" WHERE \"DocumentoToken\"=:t",
      Map.of("t", token)
    );
    if (
      d == null || !(d.get("AssinaturaId") instanceof Number)
    ) throw naoEncontrado();
    ativo(id(d, "AssinaturaId"));
    var controle = banco.um(
      "SELECT certificado_em FROM firma_assineai_controle WHERE assinatura_id=:id",
      Map.of("id", id(d, "AssinaturaId"))
    );
    return Map.of(
      "DocumentoTitulo",
      texto(d, "DocumentoTitulo"),
      "DocumentoToken",
      token,
      "DocumentoHashDoPDFOriginal",
      texto(d, "DocumentoHashDoPDFOriginal"),
      "originalValido",
      pdfs.verificar(d),
      "assinado",
      d.get("DocumentoAssinadoBuffer") != null,
      "certificado",
      controle != null && controle.get("certificado_em") != null,
      "criadoEm",
      d.get("createdAt").toString()
    );
  }

  @Transactional
  public List<Map<String, Object>> notificar(
    long assinatura,
    String identidade,
    String origem
  ) {
    dono(assinatura, identidade);
    var resultado = new ArrayList<Map<String, Object>>();
    for (var p : pessoas(assinatura))
      for (String canal : metodos(p.get("SignatarioFormaDeNotificacao"))) {
        boolean ok;
        try {
          entrega.enviar(
            canal,
            texto(
              p,
              canal.equals("email") ? "SignatarioEmail" : "SignatarioWhatsApp"
            ),
            "convite",
            origem +
              "/web-panels/assinar-documentos/" +
              texto(p, "SignatarioTokenLinkAssinatura")
          );
          ok = true;
        } catch (RuntimeException e) {
          ok = false;
        }
        evento(
          id(p, "SignatarioId"),
          ok ? "notificado" : "falha_entrega",
          (ok ? "convite_enviado_" : "convite_nao_enviado_") + canal,
          null,
          null
        );
        resultado.add(
          Map.of(
            "nome",
            texto(p, "SignatarioNome"),
            "canal",
            canal,
            "enviado",
            ok
          )
        );
      }
    return resultado;
  }

  @Transactional
  public void excluir(long assinatura, String identidade) {
    dono(assinatura, identidade);
    banco.executar(
      "UPDATE firma_assineai_controle SET excluido_em=now() WHERE assinatura_id=:id",
      Map.of("id", assinatura)
    );
  }

  @Transactional
  public void renomear(long assinatura, String identidade, String titulo) {
    dono(assinatura, identidade);
    if (titulo == null || titulo.isBlank() || titulo.length() > 255) throw erro(
      "Título inválido"
    );
    banco.atualizar(
      "Assinaturas",
      "AssinaturaId",
      assinatura,
      new LinkedHashMap<>(Map.of("AssinaturaNome", titulo.strip()))
    );
  }

  private void bloquear(long assinatura) {
    var a = banco.um(
      "SELECT \"AssinaturaId\" FROM \"Assinaturas\" WHERE \"AssinaturaId\"=:id FOR UPDATE",
      Map.of("id", assinatura)
    );
    if (a == null) throw naoEncontrado();
    ativo(assinatura);
  }

  private void evento(
    long signatario,
    String situacao,
    String acao,
    String ip,
    String dispositivo
  ) {
    var d = new LinkedHashMap<String, Object>();
    d.put("SignatarioId", signatario);
    d.put("SignatarioHistoricoStatus", situacao);
    d.put("SignatarioHistoricoAcao", acao);
    d.put("SignatarioHistoricoIp", limitar(ip, 64));
    d.put("SignatarioHistoricoDispositivo", limitar(dispositivo, 1024));
    banco.inserir("SignatarioHistoricos", "SignatarioHistoricoId", d);
  }

  private String limitar(String v, int maiorValor) {
    return v == null ? null : v.substring(0, Math.min(v.length(), maiorValor));
  }

  private String hash(String valor) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          valor.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public List<String> metodos(Object valor) {
    if (valor instanceof List<?> l) return l
      .stream()
      .filter(Objects::nonNull)
      .map(Object::toString)
      .distinct()
      .toList();
    return List.of();
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> lista(Object valor) {
    if (valor == null) return new ArrayList<>();
    if (valor instanceof String s) return json.readValue(s, List.class);
    if (valor instanceof List<?> l) return new ArrayList<>(
      (List<Map<String, Object>>) l
    );
    throw erro("Coleção de campos inválida");
  }

  private ResponseStatusException erro(String m) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }

  private ResponseStatusException naoEncontrado() {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Registro não encontrado"
    );
  }
}
