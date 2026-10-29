package com.afirma.assina;

import static com.afirma.assina.BancoAssineai.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ControleAssineai {

  private final ServicoAssineai servico;
  private final IdentidadeAssineai identidade;
  private final ObjectMapper json;
  private final boolean compatibilidadePublica;
  private final ServicoPlanoBuscaAssineAi planos;

  public ControleAssineai(
    ServicoAssineai servico,
    IdentidadeAssineai identidade,
    ObjectMapper json,
    ServicoPlanoBuscaAssineAi planos,
    @org.springframework.beans.factory.annotation.Value(
      "${APP_ASSINEAI_COMPATIBILIDADE_PUBLICA:false}"
    ) boolean publica
  ) {
    this.servico = servico;
    this.identidade = identidade;
    this.json = json;
    this.compatibilidadePublica = publica;
    this.planos = planos;
  }

  private String usuario(HttpServletRequest r) {
    if (
      compatibilidadePublica &&
      !r.getServletPath().startsWith("/api/") &&
      r.getHeader("Authorization") == null
    ) return identidade.identidadeIntegracaoLegada();
    return identidade.autenticar(r);
  }

  private String origem(HttpServletRequest r) {
    return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromRequestUri(
      r
    )
      .replacePath(null)
      .replaceQuery(null)
      .build()
      .toUriString();
  }

  @PostMapping("/api/assineai/plano-busca")
  public Object plano(
    @RequestBody Map<String, String> dados,
    HttpServletRequest r
  ) {
    usuario(r);
    return planos.interpretar(dados.get("consulta"));
  }

  @GetMapping("/api/assineai/recursos")
  public Object recursos(HttpServletRequest r) {
    return servico.recursos(usuario(r));
  }

  @GetMapping("/api/assineai/empresa")
  public Object empresa(HttpServletRequest r) {
    return identidade.empresa(usuario(r));
  }

  @PutMapping("/api/assineai/empresa")
  public Object empresa(
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    return identidade.salvar(usuario(r), dados);
  }

  @PostMapping("/JLv3HEzbcO2uKlg0rsELA66dou")
  public Object login(@RequestBody Map<String, Object> dados) {
    return identidade.login(dados);
  }

  @PostMapping("/q9zePu74UYPUaDqUyay4fIjwNV")
  public ResponseEntity<?> registrar(@RequestBody Map<String, Object> dados) {
    identidade.registrar(dados);
    return ResponseEntity.status(201).body(
      Map.of("message", "Usuário registrado com sucesso.")
    );
  }

  @GetMapping("/PegarTemaTelaAssinatura")
  public Object tema(HttpServletRequest r) {
    return identidade.empresa(usuario(r));
  }

  @PostMapping("/AtualizarTemaTelaAssinatura")
  public Object tema(
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    return identidade.salvar(usuario(r), dados);
  }

  @PostMapping(
    value = "/api/assineai/paginas",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Object paginas(
    @RequestParam("pdf") MultipartFile pdf,
    HttpServletRequest r
  ) throws Exception {
    usuario(r);
    return Map.of("paginas", servico.paginas(pdf));
  }

  @PostMapping(
    value = "/api/assineai/previsualizar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ResponseEntity<byte[]> previsualizar(
    @RequestParam("pdf") MultipartFile pdf,
    @RequestParam int pagina,
    HttpServletRequest r
  ) throws Exception {
    usuario(r);
    return ResponseEntity.ok()
      .contentType(MediaType.IMAGE_PNG)
      .header("Cache-Control", "no-store")
      .body(servico.previsualizar(pdf, pagina));
  }

  @PostMapping(
    value = "/api/assineai/assinaturas",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Object criar(
    @RequestParam("pdfs") List<MultipartFile> pdfs,
    @RequestPart("dados") ServicoLoteAssinatura.Pedido dados,
    HttpServletRequest r
  ) throws Exception {
    return servico.criarFirma(pdfs, dados, usuario(r), origem(r));
  }

  @SuppressWarnings("unchecked")
  @PostMapping(
    value = "/CriarAssinaturaViaAPI",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Object criarLegado(MultipartHttpServletRequest r) throws Exception {
    var arquivos = new ArrayList<MultipartFile>();
    var docs = new ArrayList<Map<String, Object>>();
    if (r.getFileMap().size() > 10) throw new IllegalArgumentException(
      "Limite de 10 documentos"
    );
    for (int i = 0; i < r.getFileMap().size(); i++) {
      var pdf = r.getFile("pdf" + i);
      if (pdf == null) throw new IllegalArgumentException(
        "Envie pdf0, pdf1 e os metadados correspondentes"
      );
      arquivos.add(pdf);
      var bruto = json.readValue(r.getParameter("documento" + i), Map.class);
      docs.add((Map<String, Object>) bruto.get("Documento"));
    }
    var signatarios = json.readValue(r.getParameter("signatarios"), Map.class);
    var assinatura = json.readValue(r.getParameter("assinatura"), Map.class);
    return servico.criar(
      arquivos,
      docs,
      (List<Map<String, Object>>) signatarios.get("dadosDoSignatario"),
      Objects.toString(assinatura.get("assinaturaNome"), ""),
      usuario(r),
      origem(r)
    );
  }

  @GetMapping("/api/assineai/assinaturas")
  public Object listar(HttpServletRequest r) {
    String u = usuario(r);
    var saida = new ArrayList<Map<String, Object>>();
    for (var a : servico.listar(u)) {
      long id = id(a, "AssinaturaId");
      var ps = servico.pessoas(id);
      saida.add(
        Map.of(
          "id",
          id,
          "legado",
          true,
          "titulo",
          texto(a, "AssinaturaNome"),
          "data",
          a.get("createdAt"),
          "responsavel",
          texto(a, "AssinaturaResponsavel"),
          "status",
          texto(a, "AssinaturaStatus"),
          "quantidadeAssinaturas",
          ps.size(),
          "assinaturasConcluidas",
          ps
            .stream()
            .filter(p -> Boolean.TRUE.equals(p.get("SignatarioAssinou")))
            .count(),
          "signatarios",
          ps
            .stream()
            .map(p ->
              Map.of(
                "nome",
                texto(p, "SignatarioNome"),
                "email",
                texto(p, "SignatarioEmail")
              )
            )
            .toList()
        )
      );
    }
    return saida;
  }

  @GetMapping("/api/assineai/assinaturas/{id}")
  public Object detalheFirma(@PathVariable long id, HttpServletRequest r) {
    var a = servico.detalhe(id, usuario(r));
    return adaptarDetalhe(a);
  }

  @GetMapping("/ListarDetalheAssinatura")
  public Object detalhe(
    @RequestParam("AssinaturaId") long id,
    HttpServletRequest r
  ) {
    return servico.detalhe(id, usuario(r));
  }

  @PostMapping("/ListarTodasAssinaturas")
  public Object listaLegada(
    @RequestBody(required = false) Map<String, Object> filtros,
    HttpServletRequest r
  ) {
    var todos = servico.listar(usuario(r));
    var f = filtros == null ? Map.<String, Object>of() : filtros;
    var filtrados = new ArrayList<>(
      todos
        .stream()
        .filter(a -> corresponde(a, f))
        .toList()
    );
    if (
      "asc".equalsIgnoreCase(Objects.toString(f.get("order"), ""))
    ) Collections.reverse(filtrados);
    int inicio =
      f.get("offset") instanceof Number n ? Math.max(0, n.intValue()) : 0;
    int limite =
      f.get("ItemsPorPagina") instanceof Number n
        ? Math.max(1, Math.min(100, n.intValue()))
        : 10;
    var rows = filtrados
      .stream()
      .skip(inicio)
      .limit(limite)
      .map(a -> {
        var copia = new LinkedHashMap<>(a);
        long id = id(a, "AssinaturaId");
        copia.put(
          "AssinaturaDocumentos",
          servico
            .documentos(id)
            .stream()
            .map(d -> Map.of("DocumentoTitulo", texto(d, "DocumentoTitulo")))
            .toList()
        );
        copia.put(
          "AssinaturaSignatarios",
          servico
            .pessoas(id)
            .stream()
            .map(p -> Map.of("SignatarioNome", texto(p, "SignatarioNome")))
            .toList()
        );
        return copia;
      })
      .toList();
    return Map.of(
      "count",
      filtrados.size(),
      "rows",
      rows,
      "totalDeAssinaturas",
      todos.size()
    );
  }

  private boolean corresponde(
    Map<String, Object> a,
    Map<String, Object> filtros
  ) {
    for (String campo : List.of(
      "AssinaturaNome",
      "AssinaturaResponsavel",
      "AssinaturaStatus"
    )) {
      String filtro = Objects.toString(filtros.get(campo), "");
      if (
        !filtro.isBlank() &&
        !texto(a, campo)
          .toLowerCase(Locale.ROOT)
          .contains(filtro.toLowerCase(Locale.ROOT))
      ) return false;
    }
    if (
      filtros.get("AssinaturaId") != null &&
      !Objects.toString(filtros.get("AssinaturaId")).isBlank() &&
      !texto(a, "AssinaturaId").equals(filtros.get("AssinaturaId").toString())
    ) return false;
    long id = id(a, "AssinaturaId");
    String doc = Objects.toString(
      filtros.get("AssinaturaDocumentos"),
      ""
    ).toLowerCase(Locale.ROOT);
    String pessoa = Objects.toString(
      filtros.get("AssinaturaSignatarios"),
      ""
    ).toLowerCase(Locale.ROOT);
    if (
      !doc.isBlank() &&
      servico
        .documentos(id)
        .stream()
        .noneMatch(d ->
          texto(d, "DocumentoTitulo").toLowerCase(Locale.ROOT).contains(doc)
        )
    ) return false;
    if (
      !pessoa.isBlank() &&
      servico
        .pessoas(id)
        .stream()
        .noneMatch(p ->
          texto(p, "SignatarioNome").toLowerCase(Locale.ROOT).contains(pessoa)
        )
    ) return false;
    Object data = filtros.get("createdAt");
    if (data != null && !data.toString().isBlank()) {
      var partes = data instanceof List<?> l ? l : List.of(data);
      if (!partes.isEmpty()) {
        var dia = ((java.util.Date) a.get("createdAt"))
          .toInstant()
          .atZone(java.time.ZoneId.of("UTC"))
          .toLocalDate();
        var de = java.time.LocalDate.parse(
          partes.getFirst().toString().substring(0, 10)
        );
        var ate =
          partes.size() > 1
            ? java.time.LocalDate.parse(
                partes.getLast().toString().substring(0, 10)
              )
            : de;
        if (dia.isBefore(de) || dia.isAfter(ate)) return false;
      }
    }
    return true;
  }

  @DeleteMapping("/api/assineai/assinaturas/{id}")
  public void excluir(@PathVariable long id, HttpServletRequest r) {
    servico.excluir(id, usuario(r));
  }

  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
  @PatchMapping("/api/assineai/assinaturas/{id}")
  public void renomear(
    @PathVariable long id,
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    servico.renomear(id, usuario(r), Objects.toString(dados.get("titulo"), ""));
  }

  @PostMapping("/api/assineai/assinaturas/{id}/notificar")
  public Object notificar(@PathVariable long id, HttpServletRequest r) {
    return servico.notificar(id, usuario(r), origem(r));
  }

  @PostMapping(
    value = "/api/assineai/assinaturas/{id}/certificar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public void certificar(
    @PathVariable long id,
    @RequestParam("certificado") MultipartFile pfx,
    @RequestParam String senha,
    HttpServletRequest r
  ) throws Exception {
    servico.certificar(id, usuario(r), pfx, senha);
  }

  @GetMapping("/api/assineai/convites/{token}")
  public Object convite(@PathVariable String token, HttpServletRequest r)
    throws Exception {
    return servico.convite(token, r.getRemoteAddr(), r.getHeader("User-Agent"));
  }

  @GetMapping("/ListarDetalhesDoSignatario")
  public Object conviteLegado(
    @RequestParam("SignatarioToken") String token,
    HttpServletRequest r
  ) throws Exception {
    var c = new LinkedHashMap<>(
      servico.convite(token, r.getRemoteAddr(), r.getHeader("User-Agent"))
    );
    var docs = new ArrayList<Map<String, Object>>();
    for (var d : servico.documentos(
      id(servico.pessoa(token), "AssinaturaId")
    )) {
      var m = new LinkedHashMap<String, Object>();
      m.put("DocumentoTitulo", d.get("DocumentoTitulo"));
      m.put("DocumentoBuffer", buffer((byte[]) d.get("DocumentoBuffer")));
      docs.add(m);
    }
    c.put("Documentos", docs);
    return c;
  }

  @GetMapping(
    "/api/assineai/convites/{token}/documentos/{documento}/paginas/{pagina}"
  )
  public ResponseEntity<byte[]> pagina(
    @PathVariable String token,
    @PathVariable long documento,
    @PathVariable int pagina
  ) throws Exception {
    return ResponseEntity.ok()
      .header("Cache-Control", "no-store")
      .contentType(MediaType.IMAGE_PNG)
      .body(servico.pagina(token, documento, pagina));
  }

  @GetMapping("/api/assineai/convites/{token}/documentos/{documento}/pdf")
  public ResponseEntity<byte[]> pdf(
    @PathVariable String token,
    @PathVariable long documento,
    @RequestParam(defaultValue = "false") boolean original
  ) {
    var d = servico.documentoConvite(token, documento);
    return pdf(
      original ? (byte[]) d.get("DocumentoBuffer") : servico.disponivel(d),
      texto(d, "DocumentoTitulo")
    );
  }

  @GetMapping("/api/assineai/assinaturas/{id}/documentos/{documento}/pdf")
  public ResponseEntity<byte[]> pdfDono(
    @PathVariable long id,
    @PathVariable long documento,
    HttpServletRequest r
  ) {
    servico.dono(id, usuario(r));
    var d = servico
      .documentos(id)
      .stream()
      .filter(v -> id(v, "DocumentoId") == documento)
      .findFirst()
      .orElseThrow(() ->
        new org.springframework.web.server.ResponseStatusException(
          HttpStatus.NOT_FOUND
        )
      );
    return pdf(servico.disponivel(d), texto(d, "DocumentoTitulo"));
  }

  @PostMapping("/api/assineai/convites/{token}/codigos/{canal}")
  public void codigo(
    @PathVariable String token,
    @PathVariable String canal,
    HttpServletRequest r
  ) {
    servico.enviarCodigo(
      token,
      canal,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
  }

  @PostMapping("/api/assineai/convites/{token}/codigos/{canal}/verificar")
  public ResponseEntity<?> validar(
    @PathVariable String token,
    @PathVariable String canal,
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    boolean ok = servico.verificarCodigo(
      token,
      Objects.toString(dados.get("codigo"), ""),
      canal,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
    return ResponseEntity.status(ok ? 204 : 400).build();
  }

  @PostMapping("/ValidarToken")
  public boolean validar(
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    return servico.verificarCodigo(
      Objects.toString(dados.get("SignatarioToken"), ""),
      Objects.toString(dados.get("otp"), ""),
      null,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
  }

  @PostMapping({ "/EnviarToken", "/enviarToken" })
  public boolean enviar(
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) {
    String canal = Objects.toString(
      dados.getOrDefault(
        "canal",
        dados.getOrDefault(
          "metodo",
          dados.getOrDefault("tipo", dados.get("formaDeAutenticacao"))
        )
      ),
      ""
    );
    String token = Objects.toString(dados.get("SignatarioToken"), "");
    if (canal.isBlank()) servico.enviarCodigoLegado(
      token,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
    else servico.enviarCodigo(
      token,
      canal,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
    return true;
  }

  @PostMapping("/api/assineai/convites/{token}/assinar")
  public Object assinar(
    @PathVariable String token,
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) throws Exception {
    var saida = new ArrayList<Map<String, Object>>();
    for (var documento : servico.assinar(
      token,
      dados,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    )) {
      var meta = servico.metadados(documento);
      meta.put("paginas", servico.quantidadePaginas(documento));
      saida.add(meta);
    }
    return saida;
  }

  @PostMapping("/AssineAi_AssinarDocumentoViaAPI")
  public ResponseEntity<?> assinarLegado(
    @RequestBody Map<String, Object> dados,
    HttpServletRequest r
  ) throws Exception {
    String token = Objects.toString(dados.get("SignatarioToken"), "");
    if (
      dados.get("otp") != null &&
      !servico.verificarCodigo(
        token,
        dados.get("otp").toString(),
        null,
        r.getRemoteAddr(),
        r.getHeader("User-Agent")
      )
    ) return ResponseEntity.badRequest().body(
      Map.of("mensagem", "Código inválido")
    );
    var entrada = new LinkedHashMap<>(dados);
    entrada.put("aceite", true);
    var docs = servico.assinar(
      token,
      entrada,
      r.getRemoteAddr(),
      r.getHeader("User-Agent")
    );
    return ResponseEntity.ok(
      docs
        .stream()
        .map(d -> {
          var c = new LinkedHashMap<>(d);
          c.put("DocumentoBuffer", buffer((byte[]) d.get("DocumentoBuffer")));
          if (d.get("DocumentoAssinadoBuffer") instanceof byte[] b) c.put(
            "DocumentoAssinadoBuffer",
            buffer(b)
          );
          return c;
        })
        .toList()
    );
  }

  @GetMapping("/api/assineai/autenticidade/{token}")
  public Object autenticidade(@PathVariable String token) {
    return servico.autenticidade(token);
  }

  private ResponseEntity<byte[]> pdf(byte[] dados, String nome) {
    return ResponseEntity.ok()
      .header("Cache-Control", "no-store")
      .header(
        "Content-Disposition",
        ContentDisposition.attachment()
          .filename(
            nome.endsWith(".pdf") ? nome : nome + ".pdf",
            java.nio.charset.StandardCharsets.UTF_8
          )
          .build()
          .toString()
      )
      .contentType(MediaType.APPLICATION_PDF)
      .body(dados);
  }

  private Map<String, Object> buffer(byte[] bytes) {
    var valores = new ArrayList<Integer>(bytes.length);
    for (byte b : bytes) valores.add(b & 255);
    return Map.of("type", "Buffer", "data", valores);
  }

  @SuppressWarnings("unchecked")
  private Object adaptarDetalhe(Map<String, Object> a) {
    var ps = (List<Map<String, Object>>) a.get("AssinaturaSignatarios");
    var ds = (List<Map<String, Object>>) a.get("AssinaturaDocumentos");
    var saida = new LinkedHashMap<String, Object>();
    saida.put("identificador", a.get("AssinaturaId"));
    saida.put("nome", a.get("AssinaturaNome"));
    saida.put("nomeDocumento", a.get("AssinaturaNome"));
    saida.put("responsavel", a.get("AssinaturaResponsavel"));
    saida.put("status", a.get("AssinaturaStatus"));
    saida.put(
      "quantidadeDocumentoAssinado",
      a.get("AssinaturaQuantidadeDocumentoAssinado")
    );
    saida.put(
      "documentos",
      ds
        .stream()
        .map(d ->
          Map.of(
            "identificador",
            d.get("DocumentoId"),
            "titulo",
            texto(d, "DocumentoTitulo"),
            "hashOriginal",
            texto(d, "DocumentoHashDoPDFOriginal"),
            "autenticidade",
            texto(d, "DocumentoLinkAutenticacao")
          )
        )
        .toList()
    );
    saida.put(
      "signatarios",
      ps
        .stream()
        .map(p -> {
          var pessoa = new LinkedHashMap<String, Object>();
          pessoa.put("nome", p.get("SignatarioNome"));
          pessoa.put("situacao", p.get("SignatarioSituacaoAssinatura"));
          pessoa.put(
            "acessos",
            p.get("SignatarioQuantidadeDeAcessosNoLinkDeAssinatura")
          );
          pessoa.put("assinadoEm", p.get("SignatarioDataAssinatura"));
          pessoa.put("autenticadoVia", p.get("SignatarioAutenticadoVia"));
          pessoa.put(
            "historicos",
            ((List<Map<String, Object>>) p.get("historicos"))
              .stream()
              .map(h -> {
                var evento = new LinkedHashMap<String, Object>();
                evento.put("acao", h.get("SignatarioHistoricoAcao"));
                evento.put("criadoEm", h.get("createdAt"));
                evento.put("enderecoIp", h.get("SignatarioHistoricoIp"));
                evento.put(
                  "dispositivo",
                  h.get("SignatarioHistoricoDispositivo")
                );
                return evento;
              })
              .toList()
          );
          return pessoa;
        })
        .toList()
    );
    return saida;
  }
}
