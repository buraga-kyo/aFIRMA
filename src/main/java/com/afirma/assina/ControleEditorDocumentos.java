package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assineai/editor")
public class ControleEditorDocumentos {

  private final IdentidadeAssineai identidade;
  private final EditorDocumentosPdf editor;
  private final ModelosDocumentos modelos;
  private final ServicoCamposPdf pdfs;
  private final ObjectMapper json;
  private final ServicoEnvelopes envelopes;
  private final RepositorioEnvelopeAssinatura repositorioEnvelopes;

  public ControleEditorDocumentos(
    IdentidadeAssineai identidade,
    EditorDocumentosPdf editor,
    ModelosDocumentos modelos,
    ServicoCamposPdf pdfs,
    ObjectMapper json,
    ServicoEnvelopes envelopes,
    RepositorioEnvelopeAssinatura repositorioEnvelopes
  ) {
    this.identidade = identidade;
    this.editor = editor;
    this.modelos = modelos;
    this.pdfs = pdfs;
    this.json = json;
    this.envelopes = envelopes;
    this.repositorioEnvelopes = repositorioEnvelopes;
  }

  @PostMapping("/paginas")
  public Object paginas(@RequestParam MultipartFile pdf, HttpServletRequest requisicao)
    throws Exception {
    identidade.autenticar(requisicao);
    return Map.of("paginas", editor.paginas(pdf.getBytes()));
  }

  @PostMapping("/previa")
  public ResponseEntity<byte[]> previa(
    @RequestParam MultipartFile pdf,
    @RequestParam int pagina,
    HttpServletRequest requisicao
  ) throws Exception {
    identidade.autenticar(requisicao);
    var bytes = editor.validar(pdf.getBytes());
    if (pagina < 1 || pagina > editor.paginas(bytes).size()) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Página inválida."
    );
    return ResponseEntity.ok()
      .contentType(MediaType.IMAGE_PNG)
      .header("Cache-Control", "no-store")
      .body(pdfs.previsualizar(bytes, pagina));
  }

  @PostMapping("/detectar")
  public Object detectar(@RequestParam MultipartFile pdf, HttpServletRequest requisicao)
    throws Exception {
    identidade.autenticar(requisicao);
    return Map.of("campos", editor.detectar(pdf.getBytes()));
  }

  @PostMapping("/editar")
  public ResponseEntity<byte[]> editar(
    @RequestParam MultipartFile pdf,
    @RequestParam String operacao,
    HttpServletRequest requisicao
  ) throws Exception {
    identidade.autenticar(requisicao);
    return arquivo(editor.editar(pdf.getBytes(), ler(operacao, EditorDocumentosPdf.Pedido.class)));
  }

  @PostMapping("/combinar")
  public ResponseEntity<byte[]> combinar(
    @RequestParam List<MultipartFile> pdfs,
    HttpServletRequest requisicao
  ) throws Exception {
    identidade.autenticar(requisicao);
    var arquivos = new ArrayList<byte[]>();
    long total = 0;
    for (var pdf : pdfs) {
      total += pdf.getSize();
      if (total > 20_000_000) throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Total máximo de 20 MB."
      );
      arquivos.add(pdf.getBytes());
    }
    return arquivo(editor.combinar(arquivos));
  }

  @GetMapping("/modelos")
  public Object listar(HttpServletRequest requisicao) {
    return modelos.listar(identidade.autenticar(requisicao));
  }

  @GetMapping("/modelos/{id}")
  public Object obter(
    @PathVariable UUID id,
    @RequestParam(required = false) Integer versao,
    HttpServletRequest requisicao
  ) {
    return modelos.obter(id, identidade.autenticar(requisicao), versao);
  }

  @GetMapping("/modelos/{id}/versoes")
  public Object versoes(@PathVariable UUID id, HttpServletRequest requisicao) {
    return modelos.versoes(id, identidade.autenticar(requisicao));
  }

  @GetMapping("/modelos/{id}/pdf")
  public ResponseEntity<byte[]> pdf(
    @PathVariable UUID id,
    @RequestParam(required = false) Integer versao,
    HttpServletRequest requisicao
  ) {
    return arquivo(modelos.pdf(id, identidade.autenticar(requisicao), versao));
  }

  @PostMapping("/modelos")
  public Object salvar(
    @RequestParam MultipartFile pdf,
    @RequestParam String nome,
    @RequestParam String campos,
    @RequestParam(required = false) UUID id,
    @RequestParam(required = false) Integer versao,
    HttpServletRequest requisicao
  ) throws Exception {
    String usuario = identidade.autenticar(requisicao);
    return modelos.salvar(id, versao, nome, pdf.getBytes(), ler(campos, List.class), usuario);
  }

  @GetMapping("/envelopes")
  public Object envelopes(HttpServletRequest requisicao) {
    return repositorioEnvelopes.findResumoByCriadorAndExcluidoEmIsNullOrderByCriadoEmDesc(
      identidade.autenticar(requisicao)
    );
  }

  @GetMapping("/envelopes/{id}")
  public Object envelope(@PathVariable UUID id, HttpServletRequest requisicao) {
    return envelopes.consultar(id, identidade.autenticar(requisicao));
  }

  @GetMapping("/envelopes/{id}/pdf")
  public ResponseEntity<byte[]> documento(@PathVariable UUID id, HttpServletRequest requisicao)
    throws Exception {
    return arquivo(envelopes.documentoFinal(id, identidade.autenticar(requisicao)));
  }

  @PostMapping("/envelopes/{id}/finalizar")
  public ResponseEntity<byte[]> finalizar(
    @PathVariable UUID id,
    @RequestParam MultipartFile certificado,
    @RequestParam(value = "senha", required = false) String senha,
    @RequestParam String declaracaoPoderes,
    HttpServletRequest requisicao
  ) throws Exception {
    return arquivo(
      envelopes.finalizar(id, identidade.autenticar(requisicao), certificado, senha, true, null, declaracaoPoderes)
    );
  }

  @GetMapping("/envelopes/{envelope}/anexos/{campo}")
  public ResponseEntity<byte[]> anexo(
    @PathVariable UUID envelope,
    @PathVariable UUID campo,
    HttpServletRequest requisicao
  ) {
    var anexo = envelopes.anexoEmpresa(envelope, campo, identidade.autenticar(requisicao));
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(anexo.getAnexoTipo()))
      .header("Cache-Control", "no-store")
      .header(
        "Content-Disposition",
        ContentDisposition.attachment()
          .filename(anexo.getAnexoNome(), java.nio.charset.StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body(anexo.getAnexo());
  }

  public record Participante(String papel, String nome, String email) {}

  @PostMapping("/convites")
  public Object convites(
    @RequestParam MultipartFile pdf,
    @RequestParam String nome,
    @RequestParam String campos,
    @RequestParam String participantes,
    @RequestParam String tipoDocumento,
    @RequestParam String declaracaoFormalidades,
    @RequestParam UUID idRequisicao,
    HttpServletRequest requisicao
  ) throws Exception {
    String usuario = identidade.autenticar(requisicao);
    var bytes = editor.validar(pdf.getBytes());
    List<?> lista = ler(campos, List.class);
    CamposEditorDocumentos.validar(lista, editor.paginas(bytes).size());
    if (lista.size() > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Use até 100 campos por envelope de assinatura."
    );
    var pessoas = ler(participantes, Participante[].class);
    if (pessoas.length < 1 || pessoas.length > 20) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Use entre 1 e 20 participantes."
    );
    var novos = prepararSignatarios(lista, pessoas);
    var empresa = identidade.empresa(usuario);
    String nomeEmpresa = Objects.toString(empresa.get("EmpresaNome"), "FIRMA");
    return envelopes.criar(
      pdf,
      new ServicoEnvelopes.PedidoCriacao(nomeEmpresa, nome, novos, tipoDocumento, declaracaoFormalidades, idRequisicao),
      usuario
    );
  }

  private List<ServicoEnvelopes.SignatarioNovo> prepararSignatarios(
    List<?> campos,
    Participante[] pessoas
  ) {
    var papeis = new HashSet<String>();
    var signatarios = new ArrayList<ServicoEnvelopes.SignatarioNovo>();
    for (var pessoa : pessoas) {
      if (pessoa == null || pessoa.papel() == null || !papeis.add(pessoa.papel())) {
        throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Papel de participante inválido ou duplicado."
        );
      }
      var posicoes = camposDoPapel(campos, pessoa.papel());
      if (posicoes.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participante sem campos.");
      }
      signatarios.add(
        new ServicoEnvelopes.SignatarioNovo(
          pessoa.nome(),
          pessoa.email(),
          null,
          null,
          null,
          pessoa.papel(),
          posicoes,
          List.of(),
          List.of(),
          "EM_NOME_PROPRIO", null, null
        )
      );
    }
    // A atribuição é feita pelo papel, não pelo nome da pessoa: nomes podem se repetir.
    for (var objeto : campos) {
      var campo = (Map<?, ?>) objeto;
      if (!papeis.contains(campo.get("role"))) {
        throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Vincule todos os papéis a participantes."
        );
      }
    }
    return signatarios;
  }

  private List<ServicoEnvelopes.CampoNovo> camposDoPapel(List<?> campos, String papel) {
    var posicoes = new ArrayList<ServicoEnvelopes.CampoNovo>();
    for (var objeto : campos) {
      var campo = (Map<?, ?>) objeto;
      if (!papel.equals(campo.get("role"))) continue;
      posicoes.add(
        new ServicoEnvelopes.CampoNovo(
          tipoCampo((String) campo.get("type")),
          ((Number) campo.get("page")).intValue(),
          ((Number) campo.get("x")).doubleValue(),
          ((Number) campo.get("y")).doubleValue(),
          ((Number) campo.get("w")).doubleValue(),
          ((Number) campo.get("h")).doubleValue(),
          null,
          (String) campo.get("name"),
          (Boolean) campo.get("required")
        )
      );
    }
    return posicoes;
  }

  private CampoAssinatura.Tipo tipoCampo(String tipo) {
    return switch (tipo) {
      case "signature" -> CampoAssinatura.Tipo.ASSINATURA;
      case "initials" -> CampoAssinatura.Tipo.RUBRICA;
      case "date" -> CampoAssinatura.Tipo.DATA;
      case "checkbox" -> CampoAssinatura.Tipo.SELECAO;
      case "number" -> CampoAssinatura.Tipo.NUMERO;
      case "file" -> CampoAssinatura.Tipo.ANEXO;
      default -> CampoAssinatura.Tipo.TEXTO;
    };
  }

  private <T> T ler(String valor, Class<T> tipo) {
    try {
      if (valor.length() > 200000) throw new IllegalArgumentException();
      return json.readValue(valor, tipo);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do editor inválidos.");
    }
  }

  private ResponseEntity<byte[]> arquivo(byte[] bytes) {
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_PDF)
      .header("Cache-Control", "no-store")
      .header("Content-Disposition", "attachment; filename=documento-firma.pdf")
      .body(bytes);
  }
}
