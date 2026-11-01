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
    var m=copia(TemplatesEmpresa.padrao());m.put("design","livre");m.put("fundoImagem","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()));m.put("opacidade",0.12);
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
    try(var pdf=Loader.loadPDF(PaginaRegistroAssinaturas.gerar(TemplatesEmpresa.catalogo().get(2),dados))) {
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
    assertEquals("atlas",TemplatesEmpresa.ativo(Map.of("versao",1)).get("id"));
  }
}
