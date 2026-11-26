package com.afirma.assina;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ModelosEmailAssinatura {
  private static final String ETAPA_VERIFICACAO = "verificacao";
  private static final String CODIGO = "{{" + VariaveisEmailAssinatura.CODIGO + "}}";
  private static final Pattern VARIAVEL = Pattern.compile("\\{\\{([^{}]+)}}");
  private static final Safelist HTML_PERMITIDO = Safelist.none().addTags(
      "p", "div", "br", "strong", "b", "em", "i", "u", "s", "strike", "del", "h1", "h2", "h3",
      "ul", "ol", "li", "blockquote", "hr", "table", "thead", "tbody", "tr", "td", "th");
  private static final Safelist HTML_MAILY = new Safelist(HTML_PERMITIDO)
      .addTags("a", "img", "span", "code", "small", "tfoot", "pre")
      .addAttributes(":all", "style")
      .addAttributes("a", "href", "title")
      .addProtocols("a", "href", "https", "mailto")
      .addEnforcedAttribute("a", "rel", "noopener noreferrer")
      .addAttributes("img", "src", "width", "height", "alt", "title")
      .addProtocols("img", "src", "https")
      .addAttributes("table", "role", "align", "width", "border", "cellpadding", "cellspacing", "class")
      .addAttributes("td", "align", "width", "valign", "colspan", "rowspan", "class");
  private static final Set<String> CLASSES_MAILY = Set.of("tab-row-full", "tab-col-full", "tab-pad");
  private static final String CSS_RESPONSIVO = """
      <style>@media only screen and (max-width:425px){
      .tab-row-full{width:100%!important}.tab-col-full{display:block!important;width:100%!important}
      .tab-pad{padding:0!important}}</style>
      """;
  private static final Pattern PROPRIEDADE_CSS = Pattern.compile(
      "color|background-color|display|font-(family|size|style|weight)|line-height|letter-spacing|"
          + "text-(align|decoration|transform)|direction|vertical-align|object-fit|list-style-type|"
          + "(min-|max-)?(width|height)|(margin|padding)(-(top|bottom|left|right))?|"
          + "border(-(top|bottom|left|right))?(-(width|style|color|radius))?");
  private static final Pattern VALOR_CSS = Pattern.compile("[#a-zA-Z0-9.,% +\\-\"']{1,180}");
  private static final Set<String> BLOCOS = Set.of("doc", "paragraph", "text", "heading", "blockquote",
      "bulletList", "orderedList", "listItem", "hardBreak", "horizontalRule", "button", "image",
      "inlineImage", "logo", "spacer", "section", "columns", "column", "footer", "variable");
  private static final Set<String> MARCAS = Set.of("bold", "italic", "underline", "strike",
      "code", "textStyle", "link");
  private static final Modelo PADRAO = new Modelo("Seu código para acessar o documento", """
      <p>Código: <strong>{{codigo_verificacao}}</strong></p>
      <p>Documento: {{nome_documento}}</p>
      <p>O código vale por 10 minutos. Digite-o apenas na página do convite que você abriu.
      Não compartilhe o código. Se você não solicitou este acesso, ignore esta mensagem.</p>
      """);
  private static final String RESUMOS = """
      SELECT t.id, t.etapa, t.titulo, t.atualizado_em,
        CASE WHEN t.ativo THEN 'Ativo' WHEN t.padrao OR EXISTS (
          SELECT 1 FROM firma_email_assinatura_publicacao p WHERE p.template_id = t.id
        ) THEN 'Inativo' ELSE 'Rascunho' END AS status
      FROM firma_email_template t
      WHERE t.empresa_id = ? AND t.excluido_em IS NULL
      """;
  private static final String PUBLICACOES = """
      SELECT p.id, p.assunto, p.html, p.documento, p.publicado_em
      FROM firma_email_assinatura_publicacao p
      JOIN firma_email_template t ON t.id = p.template_id
      WHERE t.empresa_id = ? AND t.etapa = ? AND t.excluido_em IS NULL
      """;
  private final JdbcTemplate banco;
  private final ObjectMapper json;

  public ModelosEmailAssinatura(JdbcTemplate banco, ObjectMapper json) {
    this.banco = banco;
    this.json = json;
  }

  private Modelo lerModelo(java.sql.ResultSet linha, int indice) throws java.sql.SQLException {
    String documento = linha.getString("documento");
    return new Modelo(linha.getString("assunto"), linha.getString("html"),
        documento == null ? null : json.readTree(documento));
  }

  private String documento(Modelo modelo) {
    return modelo.documento() == null ? null : json.writeValueAsString(modelo.documento());
  }

  public List<Resumo> listar(long empresa) {
    garantirPadrao(empresa);
    return banco.query(RESUMOS + " ORDER BY t.ativo DESC, t.atualizado_em DESC, t.id DESC",
        (linha, indice) -> new Resumo(linha.getLong("id"), linha.getString("titulo"),
            linha.getString("etapa"), MomentoEmailAssinatura.de(linha.getString("etapa")).titulo(),
            linha.getString("status"), linha.getTimestamp("atualizado_em").toInstant()), empresa);
  }

  public Detalhe obter(long empresa, MomentoEmailAssinatura etapa, long id) {
    var template = template(empresa, etapa, id);
    var historico = banco.query(PUBLICACOES + " AND t.id = ? ORDER BY p.id DESC LIMIT 50",
        (linha, indice) -> new Versao(linha.getLong("id"), linha.getString("assunto"),
            linha.getTimestamp("publicado_em").toInstant()), empresa, etapa.id(), id);
    return new Detalhe(id, etapa.id(), template.titulo(), template.modelo(), historico);
  }

  public Detalhe novo(MomentoEmailAssinatura etapa) {
    Modelo modelo = etapa.verificacao() ? PADRAO : new Modelo(etapa.titulo() + " · {{nome_assinatura}}", etapa.html());
    return new Detalhe(null, etapa.id(), "", modelo, List.of());
  }

  public Modelo publicado(long empresa) {
    if (empresa <= 0) return PADRAO;
    return publicado(empresa, MomentoEmailAssinatura.VERIFICACAO).orElse(PADRAO);
  }

  public java.util.Optional<Modelo> publicado(long empresa, MomentoEmailAssinatura etapa) {
    return banco.query(PUBLICACOES + " AND t.ativo ORDER BY p.id DESC LIMIT 1",
        this::lerModelo, empresa, etapa.id()).stream().findFirst();
  }

  @Transactional
  public Detalhe salvar(long empresa, MomentoEmailAssinatura etapa, Long id, Entrada entrada) {
    if (id == null && etapa.verificacao()) garantirPadrao(empresa);
    return obter(empresa, etapa, gravar(empresa, etapa, id, titulo(entrada.titulo()), limpar(entrada.modelo(), etapa, false)));
  }

  @Transactional
  public Detalhe publicar(long empresa, MomentoEmailAssinatura etapa, Long id, String ator, Entrada entrada) {
    String titulo = titulo(entrada.titulo());
    Modelo modelo = limpar(entrada.modelo(), etapa, true);
    travarEmpresa(empresa);
    if (id == null && etapa.verificacao()) garantirPadrao(empresa);
    long template = gravar(empresa, etapa, id, titulo, modelo);
    banco.update("""
        UPDATE firma_email_template SET ativo = false, atualizado_em = now()
        WHERE empresa_id = ? AND etapa = ? AND ativo AND id <> ?
        """, empresa, etapa.id(), template);
    banco.update("UPDATE firma_email_template SET ativo = true WHERE id = ?", template);
    banco.update("""
        INSERT INTO firma_email_assinatura_publicacao (empresa_id, etapa, template_id, assunto, html, documento, publicado_por)
        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """, empresa, etapa.id(), template, modelo.assunto(), modelo.html(), documento(modelo), ator);
    return obter(empresa, etapa, template);
  }

  @Transactional
  public Detalhe duplicar(long empresa, MomentoEmailAssinatura etapa, long id) {
    Template origem = template(empresa, etapa, id);
    String copia = "Cópia de " + origem.titulo();
    return obter(empresa, etapa, gravar(empresa, etapa, null, copia.substring(0, Math.min(180, copia.length())), origem.modelo()));
  }

  @Transactional
  public Detalhe restaurar(long empresa, MomentoEmailAssinatura etapa, long id, long versao) {
    Modelo modelo = banco.query(PUBLICACOES + " AND t.id = ? AND p.id = ?",
        this::lerModelo, empresa, etapa.id(), id, versao).stream().findFirst().orElseThrow(ModelosEmailAssinatura::naoEncontrado);
    gravar(empresa, etapa, id, template(empresa, etapa, id).titulo(), modelo);
    return obter(empresa, etapa, id);
  }

  @Transactional
  public void excluir(long empresa, MomentoEmailAssinatura etapa, long id) {
    travarEmpresa(empresa);
    if (template(empresa, etapa, id).ativo())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Publique outro template antes de excluir o ativo.");
    banco.update("""
        UPDATE firma_email_template SET excluido_em = now()
        WHERE empresa_id = ? AND etapa = ? AND id = ?
        """, empresa, etapa.id(), id);
  }

  private void garantirPadrao(long empresa) {
    banco.update("""
        INSERT INTO firma_email_template (empresa_id, etapa, titulo, assunto, html, padrao, ativo)
        SELECT ?, ?, 'Código de verificação', ?, ?, true, true
        WHERE NOT EXISTS (SELECT 1 FROM firma_email_template WHERE empresa_id = ? AND etapa = ?)
        ON CONFLICT DO NOTHING
        """, empresa, ETAPA_VERIFICACAO, PADRAO.assunto(), PADRAO.html(), empresa, ETAPA_VERIFICACAO);
  }

  private void travarEmpresa(long empresa) {
    banco.queryForObject("SELECT \"EmpresaId\" FROM \"Empresas\" WHERE \"EmpresaId\" = ? FOR NO KEY UPDATE", Long.class, empresa);
  }

  private Template template(long empresa, MomentoEmailAssinatura etapa, long id) {
    return banco.query("""
        SELECT titulo, assunto, html, documento, ativo FROM firma_email_template
        WHERE empresa_id = ? AND etapa = ? AND id = ? AND excluido_em IS NULL
        """, (linha, indice) -> new Template(linha.getString("titulo"), lerModelo(linha, indice),
            linha.getBoolean("ativo")), empresa, etapa.id(), id).stream().findFirst()
        .orElseThrow(ModelosEmailAssinatura::naoEncontrado);
  }

  private long gravar(long empresa, MomentoEmailAssinatura etapa, Long id, String titulo, Modelo modelo) {
    if (id == null) return banco.queryForObject("""
        INSERT INTO firma_email_template (empresa_id, etapa, titulo, assunto, html, documento)
        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb)) RETURNING id
        """, Long.class, empresa, etapa.id(), titulo, modelo.assunto(), modelo.html(), documento(modelo));
    int alterados = banco.update("""
        UPDATE firma_email_template SET titulo = ?, assunto = ?, html = ?, documento = CAST(? AS jsonb), atualizado_em = now()
        WHERE empresa_id = ? AND etapa = ? AND id = ? AND excluido_em IS NULL
        """, titulo, modelo.assunto(), modelo.html(), documento(modelo), empresa, etapa.id(), id);
    if (alterados == 0) throw naoEncontrado();
    return id;
  }

  private static String titulo(String valor) {
    if (valor == null || valor.isBlank() || valor.length() > 180 || valor.matches("(?s).*\\p{Cc}.*"))
      throw invalido("Informe um título de até 180 caracteres.");
    return valor.strip();
  }

  private static ResponseStatusException naoEncontrado() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Template ou versão não encontrado.");
  }

  static Modelo limpar(Modelo modelo, boolean exigirCodigo) {
    return limpar(modelo, MomentoEmailAssinatura.VERIFICACAO, exigirCodigo);
  }

  static Modelo limpar(Modelo modelo, MomentoEmailAssinatura etapa, boolean publicar) {
    if (modelo == null || modelo.assunto() == null || modelo.assunto().length() > 180
        || modelo.assunto().isBlank() || modelo.assunto().matches("(?s).*\\p{Cc}.*")
        || modelo.html() == null || modelo.html().length() > 250000)
      throw invalido("Informe um assunto de até 180 caracteres e reduza o tamanho do e-mail.");
    if (modelo.documento() != null) validarDocumento(modelo.documento(), etapa);
    String html = modelo.documento() == null
        ? Jsoup.clean(modelo.html(), "", HTML_PERMITIDO, new Document.OutputSettings().prettyPrint(false))
        : limparMaily(modelo.html());
    validarVariaveis(modelo.assunto(), etapa, false);
    validarVariaveis(html, etapa, true);
    if (publicar && etapa.verificacao() && !Jsoup.parseBodyFragment(html).body().text().contains(CODIGO))
      throw invalido("Insira a variável Código de verificação no corpo do e-mail.");
    return new Modelo(modelo.assunto().strip(), html, modelo.documento());
  }

  private static String limparMaily(String html) {
    var limpo = new Cleaner(HTML_MAILY).clean(Jsoup.parseBodyFragment(html));
    limpo.outputSettings().prettyPrint(false);
    for (var elemento : limpo.body().getAllElements()) {
      for (var atributo : elemento.attributes().asList()) {
        if (atributo.getValue().contains("{{")) elemento.removeAttr(atributo.getKey());
      }
      if (elemento.hasAttr("class")) {
        var classes = elemento.classNames();
        classes.retainAll(CLASSES_MAILY);
        if (classes.isEmpty()) elemento.removeAttr("class");
        else elemento.classNames(classes);
      }
      if (elemento.hasAttr("style")) {
        var estilo = new StringBuilder();
        for (String regra : elemento.attr("style").split(";")) {
          String[] partes = regra.split(":", 2);
          if (partes.length != 2) continue;
          String chave = partes[0].strip();
          String valor = partes[1].strip();
          if (PROPRIEDADE_CSS.matcher(chave).matches() && VALOR_CSS.matcher(valor).matches()
              && !(chave.equals("display") && valor.equalsIgnoreCase("none")))
            estilo.append(chave).append(':').append(valor).append(';');
        }
        if (estilo.isEmpty()) elemento.removeAttr("style");
        else elemento.attr("style", estilo.toString());
      }
    }
    return limpo.body().html();
  }

  private static void validarDocumento(JsonNode documento, MomentoEmailAssinatura etapa) {
    if (!"doc".equals(documento.path("type").asText()) || documento.toString().length() > 150000)
      throw invalido("O documento do editor é inválido ou excede o tamanho permitido.");
    var fila = new ArrayDeque<JsonNode>();
    fila.add(documento);
    while (!fila.isEmpty()) {
      var bloco = fila.removeFirst();
      String tipo = bloco.path("type").asText();
      if (!BLOCOS.contains(tipo) || (tipo.equals("text") && bloco.path("text").asText().isEmpty()))
        throw invalido("O documento contém um bloco indisponível.");
      if (tipo.equals("variable")) {
        String id = bloco.path("attrs").path("id").asText();
        if (!VariaveisEmailAssinatura.permite(etapa, id, true))
          throw invalido("O documento contém uma variável indisponível.");
      }
      validarAtributos(bloco.path("attrs"));
      for (var marca : bloco.path("marks")) {
        if (!MARCAS.contains(marca.path("type").asText()))
          throw invalido("O documento contém uma formatação indisponível.");
        validarAtributos(marca.path("attrs"));
      }
      bloco.path("content").forEach(fila::add);
    }
  }

  private static void validarAtributos(JsonNode atributos) {
    for (String chave : List.of("src", "url", "href", "externalLink")) {
      String valor = atributos.path(chave).asText("");
      if (!valor.isBlank() && !valor.equals("#") && !valor.startsWith("https://") && !valor.startsWith("mailto:"))
        throw invalido("Use um endereço HTTPS em imagens e links, ou mailto em links de e-mail.");
    }
  }

  private static void validarVariaveis(String texto, MomentoEmailAssinatura etapa, boolean corpo) {
    var variaveis = VARIAVEL.matcher(texto);
    while (variaveis.find()) {
      String nome = variaveis.group(1);
      if (!VariaveisEmailAssinatura.permite(etapa, nome, corpo))
        throw invalido("Há uma variável indisponível neste campo.");
    }
  }

  public static Mensagem renderizar(Modelo modelo, String documento, String empresa, String codigo) {
    return renderizar(modelo, Map.of(
        "nome_documento", textoSeguro(documento), "nome_empresa", textoSeguro(empresa),
        VariaveisEmailAssinatura.CODIGO, textoSeguro(codigo)));
  }

  public static Mensagem renderizar(Modelo modelo, Map<String, String> dados) {
    var valores = new java.util.HashMap<String, String>();
    for (var variavel : VariaveisEmailAssinatura.catalogo())
      valores.put(variavel.nome(), textoSeguro(dados.get(variavel.nome())));
    String html = substituir(modelo.html(), valores, true);
    var texto = Jsoup.parseBodyFragment(html).body();
    texto.select("p,div,h1,h2,h3,li,blockquote,tr").forEach(bloco -> bloco.after("<br>"));
    return new Mensagem(substituir(modelo.assunto(), valores, false), texto.wholeText().strip(), modelo.documento() == null ? html : CSS_RESPONSIVO + html);
  }

  private static String textoSeguro(String valor) {
    return valor == null ? "" : valor.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ");
  }

  private static String substituir(String texto, Map<String, String> valores, boolean html) {
    return VARIAVEL.matcher(texto).replaceAll(variavel -> {
      String valor = valores.getOrDefault(variavel.group(1), variavel.group());
      return java.util.regex.Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(valor) : valor);
    });
  }

  private static ResponseStatusException invalido(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  public record Modelo(String assunto, String html, JsonNode documento) {
    public Modelo {
      if (documento != null && documento.isNull()) documento = null;
    }

    public Modelo(String assunto, String html) { this(assunto, html, null); }
  }
  public record Versao(long id, String assunto, Instant publicadoEm) {}
  private record Template(String titulo, Modelo modelo, boolean ativo) {}
  public record Resumo(long id, String titulo, String momento, String etapa, String status, Instant data) {}
  public record Entrada(String titulo, Modelo modelo) {}
  public record Detalhe(Long id, String momento, String titulo, Modelo rascunho, List<Versao> historico) {}
  public record Mensagem(String assunto, String texto, String html) {}
}
