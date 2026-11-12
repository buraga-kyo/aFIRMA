package com.afirma.assina;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.springframework.stereotype.Service;

@Service
public class ServicoCamposPdf {

  public record Marca(
    CampoAssinatura campo,
    SignatarioEnvelope signatario,
    byte[] imagem
  ) {}

  public int paginas(byte[] pdf) throws Exception {
    try (PDDocument documento = Loader.loadPDF(pdf)) {
      int quantidade = documento.getNumberOfPages();
      if (quantidade < 1 || quantidade > 100) {
        throw new IllegalArgumentException(
          "PDF deve ter entre 1 e 100 páginas"
        );
      }
      for (int indice = 0; indice < quantidade; indice++) {
        var tamanho = documento.getPage(indice).getCropBox();
        if (
          tamanho.getWidth() <= 0 ||
          tamanho.getHeight() <= 0 ||
          tamanho.getWidth() > 4000 ||
          tamanho.getHeight() > 4000
        ) {
          throw new IllegalArgumentException("Dimensão da página inválida");
        }
      }
      return quantidade;
    }
  }

  public byte[] previsualizar(byte[] pdf, int pagina) throws Exception {
    try (
      PDDocument documento = Loader.loadPDF(pdf);
      ByteArrayOutputStream saida = new ByteArrayOutputStream()
    ) {
      if (pagina < 1 || pagina > documento.getNumberOfPages()) {
        throw new IllegalArgumentException("Página inválida");
      }
      var tamanho = documento.getPage(pagina - 1).getCropBox();
      float maior = Math.max(tamanho.getWidth(), tamanho.getHeight());
      if (maior <= 0 || maior > 4000) throw new IllegalArgumentException(
        "Dimensão da página inválida"
      );
      float escala = Math.min(1.5f, 1800f / maior);
      BufferedImage imagem = new PDFRenderer(documento).renderImage(
        pagina - 1,
        escala
      );
      ImageIO.write(imagem, "png", saida);
      return saida.toByteArray();
    }
  }

  public byte[] aplicar(byte[] pdf, List<Marca> marcas) throws Exception {
    if (marcas.isEmpty()) return pdf;
    try (
      PdfReader leitor = new PdfReader(pdf);
      ByteArrayOutputStream saida = new ByteArrayOutputStream()
    ) {
      PdfStamper editor = new PdfStamper(leitor, saida, null, true);
      BaseFont fonte = BaseFont.createFont(
        BaseFont.HELVETICA_BOLD,
        BaseFont.CP1252,
        BaseFont.NOT_EMBEDDED
      );
      for (Marca marca : marcas) {
        CampoAssinatura campo = marca.campo();
        validar(campo, leitor.getNumberOfPages());
        if (campo.preenchivel()) {
          aplicarPreenchimento(campo, leitor, editor, fonte);
          continue;
        }
        Image imagem = Image.getInstance(marca.imagem());
        if (
          imagem.getOriginalType() != Image.ORIGINAL_PNG &&
          imagem.getOriginalType() != Image.ORIGINAL_JPEG
        ) {
          throw new IllegalArgumentException("Imagem de assinatura inválida");
        }
        Rectangle tamanho = leitor.getPageSizeWithRotation(campo.getPagina());
        float x = (float) (campo.getPosicaoX() * tamanho.getWidth());
        float largura = (float) (campo.getLargura() * tamanho.getWidth());
        float altura = (float) (campo.getAltura() * tamanho.getHeight());
        float y =
          tamanho.getHeight() -
          (float) (campo.getPosicaoY() * tamanho.getHeight()) -
          altura;
        PdfContentByte conteudo = editor.getOverContent(campo.getPagina());
        if (campo.getTipo() != CampoAssinatura.Tipo.SELFIE) {
          // A mesma caixa do editor, sem margens ou legenda sobre a marca.
          double angulo = Math.toRadians(-campo.getRotacao());
          float c = (float) Math.cos(angulo), s = (float) Math.sin(angulo);
          float w = campo.getRotacao() % 180 == 0 ? largura : altura;
          float h = campo.getRotacao() % 180 == 0 ? altura : largura;
          conteudo.addImage(imagem, w * c, w * s, -h * s, h * c,
            x + largura / 2 - w * c / 2 + h * s / 2,
            y + altura / 2 - w * s / 2 - h * c / 2);
          continue;
        }
        imagem.scaleToFit(largura - 6, altura - 18);
        imagem.setAbsolutePosition(x + (largura - imagem.getScaledWidth()) / 2,
          y + 14 + (altura - 18 - imagem.getScaledHeight()) / 2);
        conteudo.addImage(imagem);
        conteudo.setColorFill(new java.awt.Color(14, 33, 23));
        conteudo.rectangle(x, y, largura, 13);
        conteudo.fill();
        conteudo.beginText();
        conteudo.setColorFill(new java.awt.Color(201, 255, 61));
        conteudo.setFontAndSize(fonte, 7);
        conteudo.setTextMatrix(x + 4, y + 3);
        String legenda =
          marca.signatario().getNome() + " · " + campo.getTipo().name();
        while (
          fonte.getWidthPoint(legenda, 7) > largura - 8 && legenda.length() > 1
        ) {
          legenda = legenda.substring(0, legenda.length() - 1);
        }
        conteudo.showText(legenda);
        conteudo.endText();
      }
      editor.close();
      byte[] atualizado = saida.toByteArray();
      if (atualizado.length > 50 * 1024 * 1024) throw new ErroAssinatura(
        "PDF resultante excede 50 MB. Reduza o tamanho das imagens"
      );
      return atualizado;
    }
  }

  private void aplicarPreenchimento(
    CampoAssinatura campo,
    PdfReader leitor,
    PdfStamper editor,
    BaseFont fonte
  ) throws Exception {
    String valor = campo.getValor();
    if (valor == null || valor.isBlank()) return;
    if (campo.getTipo() == CampoAssinatura.Tipo.SELECAO) valor = valor.equals(
      "true"
    )
      ? "X"
      : "";
    Rectangle pagina = leitor.getPageSizeWithRotation(campo.getPagina());
    float x = (float) (campo.getPosicaoX() * pagina.getWidth()),
      largura = (float) (campo.getLargura() * pagina.getWidth()),
      altura = (float) (campo.getAltura() * pagina.getHeight());
    // A posição do editor parte do topo; o conteúdo PDF é desenhado a partir da base.
    float y =
      pagina.getHeight() -
      (float) (campo.getPosicaoY() * pagina.getHeight()) -
      altura;
    PdfContentByte conteudo = editor.getOverContent(campo.getPagina());
    // O recorte limita o texto ao campo e o estado gráfico é restaurado antes
    // de desenhar a próxima marca, para não afetar os demais elementos.
    conteudo.saveState();
    conteudo.rectangle(x, y, largura, altura);
    conteudo.clip();
    conteudo.newPath();
    conteudo.setColorFill(java.awt.Color.BLACK);
    conteudo.beginText();
    float tamanho = Math.min(12, Math.max(5, altura - 4));
    while (tamanho > 5 && fonte.getWidthPoint(valor, tamanho) > largura - 4)
      tamanho -= .5f;
    conteudo.setFontAndSize(fonte, tamanho);
    conteudo.setTextMatrix(x + 2, y + Math.max(2, (altura - tamanho) / 2));
    conteudo.showText(valor.replace('\n', ' ').replace('\r', ' '));
    conteudo.endText();
    conteudo.restoreState();
  }

  public void validar(CampoAssinatura campo, int paginas) {
    if (
      !java.util.Set.of(0, 90, 180, 270).contains(campo.getRotacao()) ||
      campo.getPagina() < 1 ||
      campo.getPagina() > paginas ||
      !dentro(campo.getPosicaoX(), 0, 1) ||
      !dentro(campo.getPosicaoY(), 0, 1) ||
      !dentro(campo.getLargura(), campo.preenchivel() || campo.getRotacao() % 180 != 0 ? 0.015 : 0.05, 1) ||
      !dentro(campo.getAltura(), campo.preenchivel() || campo.getRotacao() % 180 != 0 ? 0.015 : 0.03, 1) ||
      campo.getPosicaoX() + campo.getLargura() > 1.000001 ||
      campo.getPosicaoY() + campo.getAltura() > 1.000001
    ) {
      throw new IllegalArgumentException("Posição do campo inválida");
    }
  }

  private boolean dentro(double valor, double minimo, double maximo) {
    return Double.isFinite(valor) && valor >= minimo && valor <= maximo;
  }
}
