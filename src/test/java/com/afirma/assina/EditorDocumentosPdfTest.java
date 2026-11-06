package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EditorDocumentosPdfTest {
  final EditorDocumentosPdf editor=new EditorDocumentosPdf();
  byte[] pdf(String texto) throws Exception {
    try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
      var pagina=new PDPage(new PDRectangle(600,800));doc.addPage(pagina);
      try(var stream=new PDPageContentStream(doc,pagina)) {stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),14);stream.newLineAtOffset(60,700);stream.showText(texto);stream.endText();}
      doc.save(out);return out.toByteArray();
    }
  }
  @Test void combinaReordenaDuplicaEGiraSemPerderConteudo() throws Exception {
    byte[] combinado=editor.combinar(List.of(pdf("PRIMEIRO"),pdf("SEGUNDO")));
    byte[] editado=editor.editar(combinado,new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(2,90,null,null),new EditorDocumentosPdf.Pagina(1,0,null,null),new EditorDocumentosPdf.Pagina(2,0,null,null))));
    try(var doc=Loader.loadPDF(editado)) {
      assertEquals(3,doc.getNumberOfPages());assertEquals(90,doc.getPage(0).getRotation());
      var texto=new PDFTextStripper();assertTrue(texto.getText(doc).contains("PRIMEIRO"));assertTrue(texto.getText(doc).contains("SEGUNDO"));
      texto.setEndPage(1);assertTrue(texto.getText(doc).replaceAll("\\s","").contains("SEGUNDO"));
    }
    assertEquals(800f,editor.paginas(editado).getFirst().get("width"));
  }
  @Test void ocultacaoRemoveTextoDoArquivoERetornaPixelsPretos() throws Exception {
    byte[] editado=editor.editar(pdf("SEGREDO"),new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(1,0,null,List.of(new EditorDocumentosPdf.Caixa(0,0,1,.3))))));
    try(var doc=Loader.loadPDF(editado)) {
      assertFalse(new PDFTextStripper().getText(doc).contains("SEGREDO"));
      var imagem=new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0);
      assertEquals(0,imagem.getRGB(65,100)&0xffffff);
      assertEquals(0xffffff,imagem.getRGB(300,600)&0xffffff);
    }
  }
  @Test void recorteReconstruiPaginaSemConteudoForaDaArea() throws Exception {
    var resultado=editor.editar(pdf("SEGREDO"),new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(1,0,new EditorDocumentosPdf.Caixa(.5,.5,.5,.5),null))));
    try(var doc=Loader.loadPDF(resultado)){assertEquals(300,doc.getPage(0).getCropBox().getWidth());assertEquals(400,doc.getPage(0).getCropBox().getHeight());assertEquals("",new PDFTextStripper().getText(doc).strip());}
  }
  @Test void rejeitaOperacoesForaDosLimites() throws Exception {
    var bytes=pdf("TESTE");
    assertThrows(ResponseStatusException.class,()->editor.editar(bytes,new EditorDocumentosPdf.Pedido(List.of())));
    assertThrows(ResponseStatusException.class,()->editor.editar(bytes,new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(2,0,null,null)))));
    assertThrows(ResponseStatusException.class,()->editor.editar(bytes,new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(1,45,null,null)))));
    assertThrows(ResponseStatusException.class,()->editor.editar(bytes,new EditorDocumentosPdf.Pedido(List.of(new EditorDocumentosPdf.Pagina(1,0,new EditorDocumentosPdf.Caixa(.9,0,.5,.1),null)))));
  }
  @Test void detectaCamposDoFormularioComCoordenadasRelativas() throws Exception {
    try(var doc=Loader.loadPDF(pdf("FORMULARIO"));var out=new ByteArrayOutputStream()) {
      var form=new PDAcroForm(doc);doc.getDocumentCatalog().setAcroForm(form);var campo=new PDTextField(form);campo.setPartialName("Nome");
      var widget=campo.getWidgets().getFirst();widget.setRectangle(new PDRectangle(60,640,180,40));widget.setPage(doc.getPage(0));doc.getPage(0).getAnnotations().add(widget);form.getFields().add(campo);doc.save(out);
      var campos=editor.detectar(out.toByteArray());assertEquals(1,campos.size());assertEquals("Nome",campos.getFirst().get("name"));assertEquals(.1,(Double)campos.getFirst().get("x"),.00001);assertEquals(.15,(Double)campos.getFirst().get("y"),.00001);
    }
  }
  @Test void aplicaTextoPreenchidoNoPdf() throws Exception {
    var campo=new CampoAssinatura(UUID.randomUUID(),UUID.randomUUID(),CampoAssinatura.Tipo.TEXTO,1,.1,.2,.5,.05);campo.configurar("Nome",true);campo.preencher("Ana Silva");
    var pessoa=new SignatarioEnvelope(campo.getSignatarioId(),UUID.randomUUID(),"Ana","","a".repeat(64));
    var resultado=new ServicoCamposPdf().aplicar(pdf("CONTRATO"),List.of(new ServicoCamposPdf.Marca(campo,pessoa,null)));
    try(var doc=Loader.loadPDF(resultado)){String texto=new PDFTextStripper().getText(doc);assertTrue(texto.contains("CONTRATO"));assertTrue(texto.contains("Ana Silva"));}
  }
}
