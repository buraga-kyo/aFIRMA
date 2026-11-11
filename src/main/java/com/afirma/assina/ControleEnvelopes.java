package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ControleEnvelopes {

  @org.springframework.beans.factory.annotation.Autowired
  private ColetaDadosAssinatura coleta;

  private final ServicoEnvelopes servico;
  private final ServicoLoteAssinatura lotes;
  private final ServicoAutenticacaoAssinatura autenticacao;
  private final ServicoEntregaAssinatura entrega;

  public ControleEnvelopes(
    ServicoEnvelopes servico,
    ServicoLoteAssinatura lotes,
    ServicoAutenticacaoAssinatura autenticacao,
    ServicoEntregaAssinatura entrega
  ) {
    this.servico = servico;
    this.lotes = lotes;
    this.autenticacao = autenticacao;
    this.entrega = entrega;
  }

  @GetMapping("/envelopes/coleta-empresa")
  public ColetaDadosAssinatura.Conexao coletaEmpresa(HttpServletRequest requisicao) {
    return coleta.conexao(requisicao);
  }

  @GetMapping("/convites/{token}/coleta")
  public ColetaDadosAssinatura.Conexao coleta(@PathVariable String token, HttpServletRequest requisicao) {
    return coleta.conexao(requisicao);
  }

  @PostMapping("/envelopes/{id}/sincronizar-empresa")
  public ResponseEntity<Void> sincronizarEmpresa(@PathVariable UUID id, Principal usuario) throws Exception {
    servico.sincronizarAssinaturaEmpresa(id, usuario.getName());
    return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  @PostMapping("/envelopes/{id}/concluir-automaticamente")
  public ResponseEntity<Void> concluirAutomaticamente(@PathVariable UUID id, Principal usuario) throws Exception {
    servico.concluirAutomaticamente(id, usuario.getName());
    return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  @PostMapping(value = "/envelopes/{id}/assinar-empresa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> assinarEmpresa(@PathVariable UUID id, Principal usuario,
      @RequestParam(value = "certificado", required = false) MultipartFile certificado,
      @RequestParam(value = "senha", required = false) String senha,
      @RequestParam String declaracaoPoderes, HttpServletRequest requisicao) throws Exception {
    return pdf(servico.assinarPelaEmpresa(id, usuario.getName(), certificado, senha, declaracaoPoderes, coleta.receberEmpresa(requisicao), requisicao.getHeader("User-Agent")));
  }

  @PostMapping(
    value = "/envelopes/previsualizar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.IMAGE_PNG_VALUE
  )
  public ResponseEntity<byte[]> previsualizar(
    @RequestParam("pdf") MultipartFile pdf,
    @RequestParam("pagina") int pagina
  ) throws Exception {
    return imagem(servico.previsualizar(pdf, pagina));
  }

  @PostMapping(
    value = "/envelopes/paginas",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public NumeroPaginas paginas(@RequestParam("pdf") MultipartFile pdf)
    throws Exception {
    return new NumeroPaginas(servico.paginas(pdf));
  }

  @PostMapping(
    value = "/envelopes",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ServicoEnvelopes.Criado criar(
    @RequestParam("pdf") MultipartFile pdf,
    @RequestPart("dados") ServicoEnvelopes.PedidoCriacao dados,
    HttpServletRequest requisicao,
    Principal usuario
  ) throws Exception {
    return servico.criarComDocumentos(pdf, dados, usuario.getName(), null, coleta.receberEmpresa(requisicao), requisicao.getHeader("User-Agent"));
  }

  @GetMapping("/envelopes/recursos")
  public java.util.Map<String, Object> recursos(Principal usuario) {
    return java.util.Map.of(
      "entregaDisponivel",
      entrega.disponivel(),
      "usuario",
      usuario.getName()
    );
  }

  @PostMapping(
    value = "/envelopes/lote",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ServicoEnvelopes.Criado criarLote(
    @RequestParam("pdfs") java.util.List<MultipartFile> pdfs,
    @RequestPart("dados") ServicoLoteAssinatura.Pedido dados,
    HttpServletRequest requisicao,
    Principal usuario
  ) throws Exception {
    return lotes.criar(pdfs, dados, usuario.getName(), coleta.receberEmpresa(requisicao), requisicao.getHeader("User-Agent"));
  }

  @PostMapping("/envelopes/{id}/notificar")
  public java.util.List<ServicoAutenticacaoAssinatura.Notificacao> notificar(
    @PathVariable UUID id,
    @org.springframework.web.bind.annotation.RequestBody java.util.List<String> tokens,
    Principal usuario,
    HttpServletRequest requisicaoAtual
  ) {
    String origem =
      org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromRequestUri(
        requisicaoAtual
      )
        .replacePath(null)
        .replaceQuery(null)
        .build()
        .toUriString();
    return autenticacao.notificar(id, usuario.getName(), tokens, origem);
  }

  @PostMapping("/convites/{token}/codigos/{canal}")
  public ResponseEntity<Void> enviarCodigo(
    @PathVariable String token,
    @PathVariable String canal,
    HttpServletRequest requisicaoAtual
  ) {
    autenticacao.enviarCodigo(
      token,
      canal,
      requisicaoAtual.getRemoteAddr(),
      requisicaoAtual.getHeader("User-Agent")
    );
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/convites/{token}/codigos/{canal}/verificar")
  public ResponseEntity<Void> verificarCodigo(
    @PathVariable String token,
    @PathVariable String canal,
    @org.springframework.web.bind.annotation.RequestBody Codigo codigo,
    HttpServletRequest requisicaoAtual
  ) {
    return autenticacao.verificarCodigo(
      token,
      canal,
      codigo.codigo(),
      requisicaoAtual.getRemoteAddr(),
      requisicaoAtual.getHeader("User-Agent")
    )
      ? ResponseEntity.noContent().build()
      : ResponseEntity.badRequest().build();
  }

  public record Codigo(String codigo) {}

  @GetMapping("/envelopes/{id}")
  public ServicoEnvelopes.EstadoEnvelope consultar(
    @PathVariable UUID id,
    Principal usuario
  ) {
    return servico.consultar(id, usuario.getName());
  }

  @PostMapping(
    value = "/envelopes/{id}/finalizar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.APPLICATION_PDF_VALUE
  )
  public ResponseEntity<byte[]> finalizar(
    @PathVariable UUID id,
    @RequestParam(value = "certificado", required = false) MultipartFile certificado,
    @RequestParam(value = "senha", required = false) String senha,
    @RequestParam(
      value = "paginaPersonalizada",
      defaultValue = "false"
    ) boolean paginaPersonalizada,
    @RequestParam(
      value = "imagemEmpresa",
      required = false
    ) MultipartFile imagemEmpresa,
    @RequestParam("declaracaoPoderes") String declaracaoPoderes,
    HttpServletRequest requisicao,
    Principal usuario
  ) throws Exception {
    return pdf(
      servico.finalizar(
        id,
        usuario.getName(),
        certificado,
        senha,
        paginaPersonalizada,
        imagemEmpresa,
        declaracaoPoderes, coleta.receberEmpresa(requisicao), requisicao.getHeader("User-Agent")
      )
    );
  }

  @GetMapping(
    value = "/envelopes/{id}/pdf",
    produces = MediaType.APPLICATION_PDF_VALUE
  )
  public ResponseEntity<byte[]> documentoFinal(
    @PathVariable UUID id,
    Principal usuario
  ) throws Exception {
    return pdf(servico.documentoFinal(id, usuario.getName()));
  }

  @GetMapping("/convites/{token}")
  public ServicoEnvelopes.DadosConvite convite(
    @PathVariable String token,
    HttpServletRequest requisicaoAtual
  ) {
    var dados = servico.consultarConvite(token);
    servico.registrarAcessoConvite(
      token,
      requisicaoAtual.getRemoteAddr(),
      requisicaoAtual.getHeader("User-Agent")
    );
    return dados;
  }

  @GetMapping(
    value = "/convites/{token}/pdf",
    produces = MediaType.APPLICATION_PDF_VALUE
  )
  public ResponseEntity<byte[]> documentoConvite(
    @PathVariable String token,
    @RequestParam(
      value = "resumoDocumento",
      required = false
    ) String resumoDocumento
  ) throws Exception {
    return pdf(servico.documentoConvite(token, resumoDocumento));
  }

  @GetMapping(
    value = "/convites/{token}/paginas/{pagina}",
    produces = MediaType.IMAGE_PNG_VALUE
  )
  public ResponseEntity<byte[]> paginaConvite(
    @PathVariable String token,
    @PathVariable int pagina,
    @RequestParam(
      value = "resumoDocumento",
      required = false
    ) String resumoDocumento
  ) throws Exception {
    return imagem(servico.previsualizarConvite(token, pagina, resumoDocumento));
  }

  @GetMapping(
    value = "/convites/{token}/final",
    produces = MediaType.APPLICATION_PDF_VALUE
  )
  public ResponseEntity<byte[]> documentoFinalConvite(
    @PathVariable String token
  ) throws Exception {
    return pdf(servico.documentoFinalConvite(token));
  }

  @PostMapping(
    value = "/convites/{token}/assinar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ResponseEntity<Void> assinar(
    @PathVariable String token,
    @RequestParam(
      value = "assinatura",
      required = false
    ) MultipartFile assinatura,
    @RequestParam(value = "rubrica", required = false) MultipartFile rubrica,
    @RequestParam(value = "selfie", required = false) MultipartFile selfie,
    @RequestParam(value = "aceite", defaultValue = "false") boolean aceite,
    @RequestParam(value = "valores", defaultValue = "{}") String valores,
    @RequestParam("resumoDocumento") String resumoDocumento,
    @RequestParam("textoAceite") String textoAceite,
    @RequestParam("localizacao") String localizacao,
    @RequestParam(value = "ipNavegador", defaultValue = "") String ipNavegador,
    @RequestParam(
      value = "cienciaInclusaoImagem",
      defaultValue = "false"
    ) boolean cienciaInclusaoImagem,
    HttpServletRequest requisicao
  ) throws Exception {
    java.util.Map<String, String> campos;
    try {
      if (valores.length() > 250000) throw new IllegalArgumentException();
      var bruto = new tools.jackson.databind.ObjectMapper().readValue(
        valores,
        java.util.Map.class
      );
      campos = new java.util.HashMap<>();
      for (var entrada : bruto.entrySet()) {
        var item = (java.util.Map.Entry<?, ?>) entrada;
        if (
          !(item.getKey() instanceof String chave) ||
          !(item.getValue() instanceof String valor)
        ) throw new IllegalArgumentException();
        campos.put(chave, valor);
      }
    } catch (Exception e) {
      throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.BAD_REQUEST,
        "Valores dos campos inválidos"
      );
    }
    if (localizacao.length() > 2000) throw new ErroAssinatura("Localização inválida");
    var dadosColetados = coleta.receber(requisicao,
        new tools.jackson.databind.ObjectMapper().readValue(localizacao, java.util.Map.class), ipNavegador);
    servico.assinarConvite(
      token,
      assinatura,
      rubrica,
      selfie,
      aceite,
      requisicao.getRemoteAddr(),
      requisicao.getHeader("User-Agent"),
      campos,
      anexos(requisicao),
      resumoDocumento,
      textoAceite,
      cienciaInclusaoImagem,
      (ServicoAutenticacaoConvites.Sessao) requisicao.getAttribute(
        FiltroAcessoConvite.ATRIBUTO_SESSAO
      ), dadosColetados
    );
    return ResponseEntity.noContent()
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .build();
  }

  private java.util.Map<String, MultipartFile> anexos(HttpServletRequest r) {
    var resultado = new java.util.HashMap<String, MultipartFile>();
    if (
      r instanceof
        org.springframework.web.multipart.MultipartHttpServletRequest multipart
    ) multipart.getFileMap().forEach((nome, arquivo) -> {
      if (nome.startsWith("anexo_")) resultado.put(nome.substring(6), arquivo);
    });
    return resultado;
  }

  @GetMapping("/convites/{token}/anexos/{id}")
  public ResponseEntity<byte[]> anexo(
    @PathVariable String token,
    @PathVariable UUID id
  ) {
    var campo = servico.anexoConvite(token, id);
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(campo.getAnexoTipo()))
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename(
            campo.getAnexoNome(),
            java.nio.charset.StandardCharsets.UTF_8
          )
          .build()
          .toString()
      )
      .body(campo.getAnexo());
  }

  @GetMapping("/envelopes/{id}/verificacao")
  public VerificadorAssinaturasPdf.Resultado verificarEnvelope(
    @PathVariable UUID id,
    Principal usuario
  ) throws Exception {
    return servico.verificarEnvelope(id, usuario.getName());
  }

  @GetMapping("/convites/{token}/verificacao")
  public VerificadorAssinaturasPdf.Resultado verificarConvite(
    @PathVariable String token
  ) throws Exception {
    return servico.verificarConvite(token);
  }

  private ResponseEntity<byte[]> pdf(byte[] dados) {
    return ResponseEntity.ok()
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header("Referrer-Policy", "no-referrer")
      .header("X-Content-Type-Options", "nosniff")
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename("documento.pdf")
          .build()
          .toString()
      )
      .contentType(MediaType.APPLICATION_PDF)
      .body(dados);
  }

  private ResponseEntity<byte[]> imagem(byte[] dados) {
    return ResponseEntity.ok()
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .contentType(MediaType.IMAGE_PNG)
      .body(dados);
  }

  public record NumeroPaginas(int paginas) {}
}
