package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class TemplatesEmpresaTest {
  private Map<String,Object> copia(Map<String,Object> m) {return new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(m),Map.class);}
  @Test void qrDoRelatorioAbreOEnderecoDeAutenticidade() throws Exception {
    var dados=PaginaRegistroAssinaturas.amostra("Empresa Teste");
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(TemplatesEmpresa.padrao(),dados))) {
      var pagina=new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,144);
      var qr=pagina.getSubimage(505*2,78*2,128,128);
      var pixels=new byte[qr.getWidth()*qr.getHeight()];
      for(int y=0;y<qr.getHeight();y++)for(int x=0;x<qr.getWidth();x++) {
        int cor=qr.getRGB(x,y);
        pixels[y*qr.getWidth()+x]=(byte)((77*((cor>>16)&255)+150*((cor>>8)&255)+29*(cor&255))>>8);
      }
      var luminancia=new com.google.zxing.LuminanceSource(qr.getWidth(),qr.getHeight()) {
        public byte[] getRow(int y,byte[] linha) {
          if(linha==null || linha.length<getWidth())linha=new byte[getWidth()];
          System.arraycopy(pixels,y*getWidth(),linha,0,getWidth());return linha;
        }
        public byte[] getMatrix() {return pixels.clone();}
      };
      var leitura=new com.google.zxing.qrcode.QRCodeReader().decode(new com.google.zxing.BinaryBitmap(
          new com.google.zxing.common.HybridBinarizer(luminancia)));
      assertEquals(dados.autenticidade(),leitura.getText());
    }
  }
  @Test void relatorioDaFirmaVemPrimeiroSemAlterarEscolhasSalvas() throws Exception {
    var modelo=TemplatesEmpresa.padrao();
    assertEquals("classico",modelo.get("id"));
    assertEquals("Relatório de Assinaturas",modelo.get("titulo"));
    var atlas=TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("atlas")).findFirst().orElseThrow();
    assertEquals("atlas",TemplatesEmpresa.ativo(Map.of("versao",2,"ativo","atlas","modelos",List.of(atlas))).get("id"));
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(modelo,PaginaRegistroAssinaturas.amostra("Empresa Teste")))) {
      assertEquals(1,pdf.getNumberOfPages());
      String texto=new PDFTextStripper().getText(pdf);
      assertTrue(texto.contains("Relatório de Assinaturas"));
      assertTrue(texto.contains("Datas e horários em UTC-0300"));
      assertTrue(texto.contains(modelo.get("rodape").toString()));
      assertEquals("Empresa Teste",pdf.getDocumentInformation().getAuthor());
      var imagem=new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,72);
      assertEquals(0xffffff,imagem.getRGB(0,0)&0xffffff);
      assertTrue(pdf.getPage(0).getAnnotations().stream().anyMatch(anotacao -> anotacao instanceof org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink));
      assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
    }
  }
  @Test void todosOsDesignsPreservamOsMesmosDadosReais() throws Exception {
    var pasta=Path.of(System.getProperty("java.io.tmpdir"),"firma-registro-designs");Files.createDirectories(pasta);
    var dados=PaginaRegistroAssinaturas.amostra("Horizonte & Co.");
    Set<String> comum=null;
    for(var modelo:TemplatesEmpresa.catalogo()) {
      TemplatesEmpresa.validarModelo(modelo);
      byte[] bytes=PaginaRegistroAssinaturas.gerar(modelo,dados);Files.write(pasta.resolve(modelo.get("id")+".pdf"),bytes);
      try(var pdf=Loader.loadPDF(bytes)) {
        assertEquals(1,pdf.getNumberOfPages(),modelo.get("id").toString());
        String texto=new PDFTextStripper().getText(pdf);
        for(String esperado:List.of("Horizonte & Co.",dados.identificador(),dados.hash(),"Marina Costa Almeida","marina@exemplo.com","192.0.2.10","000.000.000-00","RG:","Dispositivo:","Localização informada:"))
          assertTrue(texto.replace("\n","").contains(esperado),modelo.get("id")+" perdeu "+esperado);
        assertFalse(texto.contains("aFIRMA"));
        var campos=new HashSet<>(Arrays.asList(texto.split("\\n")));campos.removeIf(v->v.contains("REGISTRO /")||v.isBlank());
        if(comum==null)comum=campos;
      }
    }
  }
  @Test void naoApagaEvidenciasComMuitosSignatariosOuDadosLongos() throws Exception {
    var amostra=PaginaRegistroAssinaturas.amostra("Empresa Teste");var ps=new ArrayList<Map<String,Object>>();
    for(int i=0;i<25;i++){var p=new HashMap<>(amostra.signatarios().getFirst());p.put("SignatarioNome","Pessoa "+i);p.put("SignatarioDispositivo","dispositivo com detalhes ".repeat(44)+"FIM-DISPOSITIVO-"+i);ps.add(p);}
    var dados=new PaginaRegistroAssinaturas.Dados(amostra.empresa(),amostra.documento(),amostra.identificador(),amostra.hash(),amostra.autenticidade(),amostra.emissao(),ps);
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(TemplatesEmpresa.padrao(),dados))) {
      assertTrue(pdf.getNumberOfPages()>1);String texto=new PDFTextStripper().getText(pdf).replace("\n","");
      for(int i=0;i<25;i++)assertTrue(texto.contains("FIM-DISPOSITIVO-"+i));
    }
  }
  @Test void preservaConteudoOriginalAoAcrescentarFolha() throws Exception {
    byte[] original;try(var doc=new org.openpdf.text.Document();var out=new java.io.ByteArrayOutputStream()){
      org.openpdf.text.pdf.PdfWriter.getInstance(doc,out);doc.open();doc.add(new org.openpdf.text.Paragraph("CONTEUDO ORIGINAL INALTERADO"));doc.close();original=out.toByteArray();}
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.acrescentar(original,TemplatesEmpresa.padrao(),PaginaRegistroAssinaturas.amostra("Empresa")))) {
      assertEquals(2,pdf.getNumberOfPages());var stripper=new PDFTextStripper();stripper.setStartPage(1);stripper.setEndPage(1);assertEquals("CONTEUDO ORIGINAL INALTERADO",stripper.getText(pdf).strip());
      assertTrue(pdf.getPage(1).getAnnotations().size()>0);
    }
  }
  @Test void composicaoDoZeroAceitaMarcaDaguaSemSubstituirOsDados() throws Exception {
    var img=new java.awt.image.BufferedImage(24,24,java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",bytes);
    var m=copia(TemplatesEmpresa.catalogo().stream().filter(modelo -> modelo.get("id").equals("atlas")).findFirst().orElseThrow());m.put("design","livre");m.put("fundoImagem","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()));m.put("opacidade",0.12);
    TemplatesEmpresa.validarModelo(m);
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(m,PaginaRegistroAssinaturas.amostra("Minha composição")))) {
      assertEquals(1,pdf.getNumberOfPages());String texto=new PDFTextStripper().getText(pdf);
      assertTrue(texto.contains("Minha composição"));assertTrue(texto.contains("Marina Costa Almeida"));
      assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
    }
  }
  @Test void imagensReaisAcompanhamORegistroEmFolhaComAMesmaIdentidade() throws Exception {
    var img=new java.awt.image.BufferedImage(24,16,java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",bytes);
    var amostra=PaginaRegistroAssinaturas.amostra("Empresa com evidências");var p=new LinkedHashMap<>(amostra.signatarios().getFirst());
    p.put("SignatarioAssinaturaEscritaBase64",Base64.getEncoder().encodeToString(bytes.toByteArray()));
    p.put("SignatarioSelfieBase64",Base64.getEncoder().encodeToString(bytes.toByteArray()));
    var dados=new PaginaRegistroAssinaturas.Dados(amostra.empresa(),amostra.documento(),amostra.identificador(),amostra.hash(),amostra.autenticidade(),amostra.emissao(),List.of(p));
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(TemplatesEmpresa.catalogo().stream().filter(m -> m.get("id").equals("noir")).findFirst().orElseThrow(),dados))) {
      assertEquals(2,pdf.getNumberOfPages());String texto=new PDFTextStripper().getText(pdf);
      assertTrue(texto.contains("Assinatura manuscrita"));assertTrue(texto.contains("Selfie"));
      assertTrue(pdf.getPage(1).getResources().getXObjectNames().iterator().hasNext());
    }
  }
  @Test void impedeRemoverBlocosOuSobreporEvidencias() {
    var m=copia(TemplatesEmpresa.padrao());TemplatesEmpresa.objeto(m.get("areas")).remove("signatarios");
    assertThrows(ResponseStatusException.class,()->TemplatesEmpresa.validarModelo(m));
    var outro=copia(TemplatesEmpresa.padrao());TemplatesEmpresa.objeto(TemplatesEmpresa.objeto(outro.get("areas")).get("documento")).put("y",40);
    assertThrows(ResponseStatusException.class,()->TemplatesEmpresa.validarModelo(outro));
  }
  @Test void rejeitaRecursosExecutaveisEImagensExternas() {
    var m=copia(TemplatesEmpresa.padrao());m.put("logo","data:image/svg+xml;base64,PHN2Zz4=");assertThrows(ResponseStatusException.class,()->TemplatesEmpresa.validarModelo(m));
    var outro=copia(TemplatesEmpresa.padrao());TemplatesEmpresa.objeto(outro.get("cores")).put("tinta","url(javascript:1)");assertThrows(ResponseStatusException.class,()->TemplatesEmpresa.validarModelo(outro));
    assertEquals("classico",TemplatesEmpresa.ativo(Map.of("versao",1)).get("id"));
  }
}
