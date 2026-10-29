package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Service;

@Service
public class PdfAssineai {

  private final ServicoCamposPdf campos;

  public PdfAssineai(ServicoCamposPdf campos) {
    this.campos = campos;
  }

  public int paginasDocumento(byte[] dados) throws Exception {
    try (var documento = Loader.loadPDF(dados)) {
      return documento.getNumberOfPages();
    }
  }

  public Map<String, String> prova(byte[] original) throws Exception {
    var gerador = KeyPairGenerator.getInstance("RSA");
    gerador.initialize(2048);
    var chaves = gerador.generateKeyPair();
    var assinatura = Signature.getInstance("SHA256withRSA");
    assinatura.initSign(chaves.getPrivate());
    assinatura.update(original);
    return Map.of(
      "DocumentoHashDoPDFOriginal",
      hash(original),
      "DocumentoCriptografiaChavePublica",
      Base64.getEncoder().encodeToString(chaves.getPublic().getEncoded()),
      "DocumentoCriptografiaAssinatura",
      Base64.getEncoder().encodeToString(assinatura.sign())
    );
  }

  public boolean verificar(Map<String, Object> documento) {
    try {
      byte[] original = (byte[]) documento.get("DocumentoBuffer");
      var chave = KeyFactory.getInstance("RSA").generatePublic(
        new X509EncodedKeySpec(
          Base64.getDecoder().decode(
            BancoAssineai.texto(documento, "DocumentoCriptografiaChavePublica")
          )
        )
      );
      var assinatura = Signature.getInstance("SHA256withRSA");
      assinatura.initVerify(chave);
      assinatura.update(original);
      return (
        hash(original).equals(
          BancoAssineai.texto(documento, "DocumentoHashDoPDFOriginal")
        ) &&
        assinatura.verify(
          Base64.getDecoder().decode(
            BancoAssineai.texto(documento, "DocumentoCriptografiaAssinatura")
          )
        )
      );
    } catch (Exception e) {
      return false;
    }
  }

  public String hash(byte[] dados) throws Exception {
    return HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256").digest(dados)
    );
  }

  public Map<String, Object> normalizar(
    Map<String, Object> campo,
    byte[] original
  ) throws Exception {
    var item = new LinkedHashMap<>(campo);
    try (var pdf = Loader.loadPDF(original)) {
      int pagina = ((Number) campo.getOrDefault("pagina", 1)).intValue();
      double x, y, w, h;
      if (campo.containsKey("posicaoX")) {
        x = numero(campo, "posicaoX", 0);
        y = numero(campo, "posicaoY", 0);
        w = numero(campo, "largura", .3);
        h = numero(campo, "altura", .1);
      } else {
        double absoluto = numero(campo, "y", 0);
        pagina = 1;
        while (
          pagina < pdf.getNumberOfPages() &&
          absoluto >= alturaVisual(pdf.getPage(pagina - 1))
        ) {
          absoluto -= alturaVisual(pdf.getPage(pagina - 1));
          pagina++;
        }
        var folha = pdf.getPage(pagina - 1);
        double larguraVisual =
          folha.getRotation() % 180 != 0
            ? folha.getCropBox().getHeight()
            : folha.getCropBox().getWidth();
        x = numero(campo, "x", 0) / larguraVisual;
        y = absoluto / alturaVisual(folha);
        w = Math.min(.5, 180 / larguraVisual);
        h = Math.min(.2, 65 / alturaVisual(folha));
        x = Math.max(0, Math.min(1 - w, x));
        y = Math.max(0, Math.min(1 - h, y));
      }
      String tipo = Objects.toString(campo.getOrDefault("tipo", "ASSINATURA"));
      var entidade = new CampoAssinatura(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CampoAssinatura.Tipo.valueOf(tipo),
        pagina,
        x,
        y,
        w,
        h
      );
      campos.validar(entidade, pdf.getNumberOfPages());
      item.putAll(
        Map.of(
          "tipo",
          tipo,
          "pagina",
          pagina,
          "posicaoX",
          x,
          "posicaoY",
          y,
          "largura",
          w,
          "altura",
          h
        )
      );
      return item;
    }
  }

  private double alturaVisual(org.apache.pdfbox.pdmodel.PDPage pagina) {
    return pagina.getRotation() % 180 != 0
      ? pagina.getCropBox().getWidth()
      : pagina.getCropBox().getHeight();
  }

  private double numero(Map<String, Object> item, String chave, double padrao) {
    Object v = item.get(chave);
    return v instanceof Number n ? n.doubleValue() : padrao;
  }

  public byte[] aplicar(
    Map<String, Object> documento,
    List<Map<String, Object>> pessoas,
    List<Map<String, Object>> posicoes,
    Map<Long, byte[]> rubricas
  ) throws Exception {
    byte[] original = (byte[]) documento.get("DocumentoBuffer");
    var marcas = new ArrayList<ServicoCamposPdf.Marca>();
    for (var pessoa : pessoas) {
      if (!Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"))) continue;
      long id = BancoAssineai.id(pessoa, "SignatarioId");
      var signatario = new SignatarioEnvelope(
        UUID.randomUUID(),
        UUID.randomUUID(),
        BancoAssineai.texto(pessoa, "SignatarioNome"),
        BancoAssineai.texto(pessoa, "SignatarioEmail"),
        ""
      );
      for (var bruto : posicoes) {
        if (
          !Objects.equals(
            Objects.toString(bruto.get("GUID"), ""),
            BancoAssineai.texto(pessoa, "SignatarioTokenLinkAssinatura")
          )
        ) continue;
        var campo = normalizar(bruto, original);
        var tipo = CampoAssinatura.Tipo.valueOf(campo.get("tipo").toString());
        byte[] imagem =
          tipo == CampoAssinatura.Tipo.RUBRICA
            ? rubricas.get(id)
            : imagem(
                pessoa.get(
                  tipo == CampoAssinatura.Tipo.SELFIE
                    ? "SignatarioSelfieBase64"
                    : "SignatarioAssinaturaEscritaBase64"
                ),
                false
              );
        if (imagem != null) marcas.add(
          new ServicoCamposPdf.Marca(
            new CampoAssinatura(
              UUID.randomUUID(),
              signatario.getId(),
              tipo,
              ((Number) campo.get("pagina")).intValue(),
              numero(campo, "posicaoX", 0),
              numero(campo, "posicaoY", 0),
              numero(campo, "largura", .3),
              numero(campo, "altura", .1)
            ),
            signatario,
            imagem
          )
        );
      }
    }
    byte[] marcado = campos.aplicar(original, marcas);
    try (
      var leitor = new PdfReader(marcado);
      var saida = new ByteArrayOutputStream()
    ) {
      var editor = new PdfStamper(leitor, saida, null, true);
      int pagina = leitor.getNumberOfPages();
      float y = 0;
      var fonte = BaseFont.createFont(
        BaseFont.HELVETICA,
        BaseFont.CP1252,
        BaseFont.NOT_EMBEDDED
      );
      List<String> linhas = new ArrayList<>();
      linhas.add("aFIRMA. / REGISTRO DE ASSINATURAS");
      linhas.add(BancoAssineai.texto(documento, "DocumentoTitulo"));
      linhas.add(
        "Documento: " + BancoAssineai.texto(documento, "DocumentoToken")
      );
      linhas.add(
        "SHA-256 original: " +
          BancoAssineai.texto(documento, "DocumentoHashDoPDFOriginal")
      );
      linhas.add(
        "Autenticidade: " +
          BancoAssineai.texto(documento, "DocumentoLinkAutenticacao")
      );
      for (var pessoa : pessoas) {
        linhas.add(" ");
        linhas.add(
          BancoAssineai.texto(pessoa, "SignatarioNome") +
            " / " +
            BancoAssineai.texto(pessoa, "SignatarioQualificacao")
        );
        linhas.add(
          Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"))
            ? "Assinou em " +
                BancoAssineai.texto(pessoa, "SignatarioDataAssinatura")
            : "Assinatura pendente"
        );
        if (Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"))) {
          linhas.add(
            "Metodos: " +
              BancoAssineai.texto(pessoa, "SignatarioAutenticadoVia")
          );
          for (String chave : java.util.List.of(
            "SignatarioEmail",
            "SignatarioWhatsApp",
            "SignatarioCPF",
            "SignatarioRG"
          ))
            if (!BancoAssineai.texto(pessoa, chave).isBlank()) linhas.add(
              chave.substring(10) + ": " + BancoAssineai.texto(pessoa, chave)
            );
          linhas.add("IP: " + BancoAssineai.texto(pessoa, "SignatarioIp"));
          linhas.add(
            "Dispositivo: " +
              BancoAssineai.texto(pessoa, "SignatarioDispositivo")
          );
          if (
            !BancoAssineai.texto(pessoa, "SignatarioGeolocalizacao").isBlank()
          ) linhas.add(
            "Localizacao informada: " +
              BancoAssineai.texto(pessoa, "SignatarioGeolocalizacao")
          );
        }
      }
      for (String linha : linhas)
        for (int i = 0; i < Math.max(1, linha.length()); i += 95) {
          if (y < 55) {
            editor.insertPage(++pagina, new Rectangle(595, 842));
            var c = editor.getOverContent(pagina);
            c.setColorFill(new java.awt.Color(14, 33, 23));
            c.rectangle(0, 0, 595, 842);
            c.fill();
            y = 795;
          }
          var c = editor.getOverContent(pagina);
          c.beginText();
          c.setFontAndSize(fonte, 10);
          c.setColorFill(new java.awt.Color(201, 255, 61));
          c.setTextMatrix(30, y);
          c.showText(linha.substring(i, Math.min(i + 95, linha.length())));
          c.endText();
          y -= 17;
        }
      // As imagens de assinatura e selfie também acompanham o relatório de evidências.
      for (var pessoa : pessoas)
        if (Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"))) {
          var imagens = new ArrayList<byte[]>();
          for (String chave : List.of(
            "SignatarioAssinaturaEscritaBase64",
            "SignatarioSelfieBase64"
          )) {
            byte[] b = imagem(pessoa.get(chave), false);
            if (b != null) imagens.add(b);
          }
          if (imagens.isEmpty()) continue;
          editor.insertPage(++pagina, new Rectangle(595, 842));
          var c = editor.getOverContent(pagina);
          c.beginText();
          c.setFontAndSize(fonte, 13);
          c.setTextMatrix(30, 800);
          c.showText(BancoAssineai.texto(pessoa, "SignatarioNome"));
          c.endText();
          float topo = 760;
          for (byte[] b : imagens) {
            var img = Image.getInstance(b);
            img.scaleToFit(500, 310);
            img.setAbsolutePosition(35, topo - img.getScaledHeight());
            c.addImage(img);
            topo -= img.getScaledHeight() + 30;
          }
        }
      editor.close();
      return saida.toByteArray();
    }
  }

  public byte[] imagem(Object valor, boolean obrigatoria) throws Exception {
    if (valor == null || valor.toString().isBlank()) {
      if (obrigatoria) throw new IllegalArgumentException(
        "Envie as marcas solicitadas"
      );
      return null;
    }
    String texto = valor.toString();
    if (texto.length() > 3_000_000) throw new IllegalArgumentException(
      "Cada imagem deve ter ate 2 MB"
    );
    byte[] bytes = Base64.getDecoder().decode(
      texto.contains(",") ? texto.substring(texto.indexOf(',') + 1) : texto
    );
    if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException(
      "Cada imagem deve ter ate 2 MB"
    );
    try (
      var in = javax.imageio.ImageIO.createImageInputStream(
        new java.io.ByteArrayInputStream(bytes)
      )
    ) {
      var leitores = javax.imageio.ImageIO.getImageReaders(in);
      if (!leitores.hasNext()) throw new IllegalArgumentException(
        "Imagem invalida"
      );
      var leitor = leitores.next();
      try {
        leitor.setInput(in);
        String formato = leitor.getFormatName();
        long largura = leitor.getWidth(0),
          altura = leitor.getHeight(0);
        if (
          !Set.of("png", "jpeg", "jpg").contains(
            formato.toLowerCase(Locale.ROOT)
          ) ||
          largura > 2500 ||
          altura > 2500 ||
          largura * altura > 6_000_000
        ) throw new IllegalArgumentException("Imagem invalida");
      } finally {
        leitor.dispose();
      }
    }
    return bytes;
  }
}
