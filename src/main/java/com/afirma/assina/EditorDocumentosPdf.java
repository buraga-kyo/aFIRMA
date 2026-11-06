package com.afirma.assina;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Processamento nativo da bancada. Coordenadas relativas ao topo esquerdo da página exibida. */
@Service
public class EditorDocumentosPdf {

  public record Caixa(double x, double y, double w, double h) {}

  public record Pagina(int origem, int rotacao, Caixa recorte, List<Caixa> ocultacoes) {}

  public record Pedido(List<Pagina> paginas) {}

  private static ResponseStatusException erro(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  public byte[] validar(byte[] pdf) {
    if (pdf.length == 0 || pdf.length > 20_000_000) throw erro("Use PDF de até 20 MB.");
    try (var doc = Loader.loadPDF(pdf)) {
      if (doc.isEncrypted()) throw erro("Remova a proteção do PDF antes de editar.");
      if (doc.getNumberOfPages() < 1 || doc.getNumberOfPages() > 100) throw erro(
        "Use de 1 a 100 páginas."
      );
      for (var p : doc.getPages()) {
        var b = p.getCropBox();
        if (
          !Float.isFinite(b.getWidth()) ||
          !Float.isFinite(b.getHeight()) ||
          b.getWidth() < 1 ||
          b.getHeight() < 1 ||
          b.getWidth() > 4000 ||
          b.getHeight() > 4000
        ) throw erro("Dimensão de página inválida.");
      }
      return pdf;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw erro("PDF inválido ou protegido.");
    }
  }

  public List<Map<String, Object>> paginas(byte[] pdf) throws Exception {
    try (var doc = Loader.loadPDF(validar(pdf))) {
      var lista = new ArrayList<Map<String, Object>>();
      int numero = 0;
      for (var p : doc.getPages()) {
        var b = p.getCropBox();
        boolean girada = Math.floorMod(p.getRotation(), 180) != 0;
        lista.add(
          Map.of(
            "pagina",
            ++numero,
            "width",
            girada ? b.getHeight() : b.getWidth(),
            "height",
            girada ? b.getWidth() : b.getHeight()
          )
        );
      }
      return lista;
    }
  }

  public List<Map<String, Object>> detectar(byte[] pdf) throws Exception {
    try (var doc = Loader.loadPDF(validar(pdf))) {
      var formulario = doc.getDocumentCatalog().getAcroForm();
      var campos = new ArrayList<Map<String, Object>>();
      if (formulario == null) return campos;
      for (var campo : formulario.getFieldTree())
        for (var widget : campo.getWidgets()) {
          var r = widget.getRectangle();
          if (r == null) continue;
          int numero = 0;
          for (var p : doc.getPages()) {
            numero++;
            if (
              widget.getPage() != null
                ? !p.getCOSObject().equals(widget.getPage().getCOSObject())
                : !p.getAnnotations().contains(widget)
            ) continue;
            // AcroForm usa a origem inferior esquerda. A bancada usa a superior
            // esquerda, relativa à área visível e à rotação da página.
            var b = p.getCropBox();
            double x = (r.getLowerLeftX() - b.getLowerLeftX()) / b.getWidth(),
              y = 1 - (r.getUpperRightY() - b.getLowerLeftY()) / b.getHeight(),
              w = r.getWidth() / b.getWidth(),
              h = r.getHeight() / b.getHeight();
            int rotacao = Math.floorMod(p.getRotation(), 360);
            for (int i = 0; i < rotacao / 90; i++) {
              double anterior = x;
              x = 1 - y - h;
              y = anterior;
              double largura = w;
              w = h;
              h = largura;
            }
            if (
              x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > 1.000001 || y + h > 1.000001
            ) continue;
            String tipo =
              campo instanceof org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
                ? "signature"
                : campo instanceof org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
                  ? "checkbox"
                  : "text";
            campos.add(
              Map.of(
                "id",
                UUID.randomUUID().toString(),
                "name",
                Objects.toString(campo.getFullyQualifiedName(), "Campo").substring(
                  0,
                  Math.min(80, Objects.toString(campo.getFullyQualifiedName(), "Campo").length())
                ),
                "type",
                tipo,
                "role",
                "Signatário",
                "required",
                campo.isRequired(),
                "page",
                numero,
                "x",
                x,
                "y",
                y,
                "w",
                w,
                "h",
                h
              )
            );
            if (campos.size() > 200) throw erro("Limite de 200 campos detectados.");
            break;
          }
        }
      return campos;
    }
  }

  public byte[] editar(byte[] pdf, Pedido pedido) throws Exception {
    validar(pdf);
    if (
      pedido == null ||
      pedido.paginas() == null ||
      pedido.paginas().isEmpty() ||
      pedido.paginas().size() > 100
    ) throw erro("Preserve entre 1 e 100 páginas.");
    try (var fonte = Loader.loadPDF(pdf); var destino = new PDDocument()) {
      if (!fonte.getSignatureDictionaries().isEmpty()) throw erro(
        "O PDF já tem assinatura digital. Edite uma cópia original sem assinatura."
      );
      if (fonte.getDocumentCatalog().getAcroForm() != null) fonte
        .getDocumentCatalog()
        .getAcroForm()
        .flatten();
      PDFRenderer renderizador = new PDFRenderer(fonte);
      for (var item : pedido.paginas()) {
        if (
          item == null ||
          item.origem() < 1 ||
          item.origem() > fonte.getNumberOfPages() ||
          !Set.of(0, 90, 180, 270).contains(item.rotacao())
        ) throw erro("Página ou rotação inválida.");
        var original = fonte.getPage(item.origem() - 1);
        if (item.ocultacoes() != null && item.ocultacoes().size() > 100) throw erro(
          "Limite de 100 ocultações por página."
        );
        if (item.recorte() != null) validarCaixa(item.recorte());
        if (item.ocultacoes() != null) item.ocultacoes().forEach(EditorDocumentosPdf::validarCaixa);
        if (item.recorte() != null || (item.ocultacoes() != null && !item.ocultacoes().isEmpty())) {
          reconstruirPagina(destino, original, renderizador, item);
        } else {
          copiarPagina(destino, original, item.rotacao());
        }
      }
      return salvar(destino);
    }
  }

  private void copiarPagina(PDDocument destino, PDPage original, int rotacao) throws Exception {
    var pagina = destino.importPage(original);
    pagina.setRotation(Math.floorMod(original.getRotation() + rotacao, 360));
    pagina.setAnnotations(new ArrayList<>());
  }

  private void reconstruirPagina(
    PDDocument destino,
    PDPage original,
    PDFRenderer renderizador,
    Pagina operacao
  ) throws Exception {
    // Uma página nova contém apenas os pixels restantes. Importar a página antiga
    // manteria texto e objetos recuperáveis por baixo das áreas ocultadas.
    var caixa = original.getCropBox();
    float escala = Math.min(2, 2400 / Math.max(caixa.getWidth(), caixa.getHeight()));
    BufferedImage imagem = renderizador.renderImage(operacao.origem() - 1, escala);
    ocultarAreas(imagem, operacao.ocultacoes());

    // O renderizador já aplica a rotação original. As medidas devem seguir a imagem
    // exibida; a rotação solicitada pelo usuário é aplicada ao final.
    boolean girada = Math.floorMod(original.getRotation(), 180) != 0;
    double largura = girada ? caixa.getHeight() : caixa.getWidth();
    double altura = girada ? caixa.getWidth() : caixa.getHeight();
    if (operacao.recorte() != null) {
      imagem = recortarImagem(imagem, operacao.recorte());
      largura *= operacao.recorte().w();
      altura *= operacao.recorte().h();
    }
    var pagina = new PDPage(new PDRectangle((float) largura, (float) altura));
    destino.addPage(pagina);
    try (var conteudo = new PDPageContentStream(destino, pagina)) {
      conteudo.drawImage(
        LosslessFactory.createFromImage(destino, imagem),
        0,
        0,
        (float) largura,
        (float) altura
      );
    }
    pagina.setRotation(operacao.rotacao());
  }

  private void ocultarAreas(BufferedImage imagem, List<Caixa> areas) {
    var desenho = imagem.createGraphics();
    desenho.setColor(Color.BLACK);
    if (areas != null) {
      for (var area : areas) {
        int x = (int) Math.floor(area.x() * imagem.getWidth());
        int y = (int) Math.floor(area.y() * imagem.getHeight());
        int largura = (int) Math.ceil((area.x() + area.w()) * imagem.getWidth()) - x;
        int altura = (int) Math.ceil((area.y() + area.h()) * imagem.getHeight()) - y;
        desenho.fillRect(x, y, largura, altura);
      }
    }
    desenho.dispose();
  }

  private BufferedImage recortarImagem(BufferedImage imagem, Caixa recorte) {
    int x = (int) Math.floor(recorte.x() * imagem.getWidth());
    int y = (int) Math.floor(recorte.y() * imagem.getHeight());
    // Arredondamos para fora para preservar pixels na borda e limitamos o resultado
    // às dimensões da imagem, inclusive quando há imprecisão de ponto flutuante.
    int largura = Math.min(
      imagem.getWidth() - x,
      Math.max(1, (int) Math.ceil((recorte.x() + recorte.w()) * imagem.getWidth()) - x)
    );
    int altura = Math.min(
      imagem.getHeight() - y,
      Math.max(1, (int) Math.ceil((recorte.y() + recorte.h()) * imagem.getHeight()) - y)
    );
    return imagem.getSubimage(x, y, largura, altura);
  }

  public byte[] combinar(List<byte[]> arquivos) throws Exception {
    if (
      arquivos == null ||
      arquivos.isEmpty() ||
      arquivos.size() > 10 ||
      arquivos
        .stream()
        .mapToLong(a -> a.length)
        .sum() > 20_000_000
    ) throw erro("Combine até 10 PDFs, total de 20 MB.");
    try (var destino = new PDDocument()) {
      for (var bytes : arquivos)
        try (var fonte = Loader.loadPDF(validar(bytes))) {
          if (!fonte.getSignatureDictionaries().isEmpty()) throw erro(
            "Combine os originais sem assinatura digital."
          );
          if (fonte.getDocumentCatalog().getAcroForm() != null) fonte
            .getDocumentCatalog()
            .getAcroForm()
            .flatten();
          if (destino.getNumberOfPages() + fonte.getNumberOfPages() > 100) throw erro(
            "Limite de 100 páginas combinadas."
          );
          new org.apache.pdfbox.multipdf.PDFMergerUtility().appendDocument(destino, fonte);
          for (var pagina : destino.getPages()) pagina.setAnnotations(new ArrayList<>());
        }
      return salvar(destino);
    }
  }

  private static byte[] salvar(PDDocument doc) throws Exception {
    var saida = new ByteArrayOutputStream();
    doc.save(saida);
    if (saida.size() > 20_000_000) throw erro(
      "Resultado excede 20 MB. Reduza o número de páginas."
    );
    return saida.toByteArray();
  }

  private static void validarCaixa(Caixa c) {
    if (
      !Double.isFinite(c.x()) ||
      !Double.isFinite(c.y()) ||
      !Double.isFinite(c.w()) ||
      !Double.isFinite(c.h()) ||
      c.x() < 0 ||
      c.y() < 0 ||
      c.w() <= 0 ||
      c.h() <= 0 ||
      c.x() + c.w() > 1.00000001 ||
      c.y() + c.h() > 1.00000001
    ) throw erro("Área fora dos limites da página.");
  }
}
