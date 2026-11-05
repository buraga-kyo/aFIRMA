package com.afirma.assina;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.*;

/** Desenha somente folhas adicionais. O PDF de origem é importado sem redesenhar seu conteúdo. */
public final class PaginaRegistroAssinaturas {
  public record Dados(String empresa,String documento,String identificador,String hash,String autenticidade,
      String emissao,List<Map<String,Object>> signatarios) {}
  private record Linha(String texto, boolean rotulo, boolean mono) {}
  private final Map<String,Object> modelo;
  private final Map<String,Object> cores, areas;
  private final BaseFont normal, forte, mono;
  private final float tamanho;
  private PdfContentByte c;
  private PaginaRegistroAssinaturas(Map<String,Object> modelo) throws Exception {
    this.modelo=TemplatesEmpresa.validarModelo(modelo); cores=TemplatesEmpresa.objeto(modelo.get("cores")); areas=TemplatesEmpresa.objeto(modelo.get("areas"));
    String familia=String.valueOf(modelo.get("fonte"));
    normal=BaseFont.createFont(familia.equals("serif")?BaseFont.TIMES_ROMAN:familia.equals("mono")?BaseFont.COURIER:BaseFont.HELVETICA,BaseFont.CP1252,false);
    forte=BaseFont.createFont(familia.equals("serif")?BaseFont.TIMES_BOLD:familia.equals("mono")?BaseFont.COURIER_BOLD:BaseFont.HELVETICA_BOLD,BaseFont.CP1252,false);
    mono=BaseFont.createFont(BaseFont.COURIER,BaseFont.CP1252,false); tamanho=((Number)modelo.get("tamanho")).floatValue();
  }
  public static Dados amostra(String empresa) {
    return new Dados(empresa,"Contrato de prestação de serviços","8d7f3c21-69a4-4b82-a7b1-56ed201f9e43",
      "a42d8e97c6513b80f74e2c96d8f03b61c59a78e24d861f093c72b1a6e0549f38",
      "https://exemplo.com/autenticidade/8d7f3c21","31/10/2026 · 14:32:08 -03:00",
      List.of(new LinkedHashMap<>(Map.ofEntries(Map.entry("SignatarioNome","Marina Costa Almeida"),Map.entry("SignatarioQualificacao","Representante legal"),
        Map.entry("SignatarioAssinou",true),Map.entry("SignatarioDataAssinatura","31/10/2026 · 14:30:42 -03:00"),Map.entry("SignatarioFirmadoVia","Assinatura na tela, código por e-mail"),
        Map.entry("SignatarioEmail","marina@exemplo.com"),Map.entry("SignatarioWhatsApp","+55 (11) 99999-0000"),Map.entry("SignatarioCPF","000.000.000-00"),Map.entry("SignatarioRG","00.000.000-0"),
        Map.entry("SignatarioIp","192.0.2.10"),Map.entry("SignatarioDispositivo","Chrome · Windows · computador"),Map.entry("SignatarioGeolocalizacao","Não informada")))));
  }
  public static byte[] gerar(Map<String,Object> modelo,Dados dados) throws Exception {
    return new PaginaRegistroAssinaturas(modelo).renderizar(dados);
  }
  public static byte[] acrescentar(byte[] original,Map<String,Object> modelo,Dados dados) throws Exception {
    byte[] folhas=gerar(modelo,dados);
    try(var leitor=new PdfReader(original);var anexo=new PdfReader(folhas);var saida=new ByteArrayOutputStream()) {
      var stamper=new PdfStamper(leitor,saida,null,true);
      int inicio=leitor.getNumberOfPages();
      for(int i=1;i<=anexo.getNumberOfPages();i++) {
        stamper.insertPage(inicio+i,PageSize.A4);
        stamper.getOverContent(inicio+i).addTemplate(stamper.getImportedPage(anexo,i),0,0);
        // Links precisam ser recriados: anotações não são parte do fluxo de desenho importado.
        if(dados.autenticidade()!=null && dados.autenticidade().matches("https?://[^\\s]+"))
          stamper.addAnnotation(PdfAnnotation.createLink(stamper.getWriter(),new Rectangle(35,18,560,38),PdfAnnotation.HIGHLIGHT_INVERT,new PdfAction(dados.autenticidade())),inicio+i);
      }
      stamper.close();return saida.toByteArray();
    }
  }
  private byte[] renderizar(Dados d) throws Exception {
    var saidaAtual=new ByteArrayOutputStream(); var pdf=new Document(PageSize.A4,0,0,0,0);var writer=PdfWriter.getInstance(pdf,saidaAtual);
    pdf.addTitle("Registro de assinaturas · "+d.empresa());pdf.addAuthor(d.empresa());pdf.open(); c=writer.getDirectContent();
    var areaDocumento=area("documento"); var sigArea=area("signatarios");
    List<Linha> documento=new ArrayList<>();
    campo(documento,"Documento",d.documento(),false,areaDocumento[2]-36);
    campo(documento,"Identificador",d.identificador(),true,areaDocumento[2]-36);
    campo(documento,"SHA-256 do PDF original",d.hash(),true,areaDocumento[2]-36);
    campo(documento,"Autenticidade",d.autenticidade(),true,areaDocumento[2]-36);
    int deslocamentoDocumento=0,pessoa=0,offset=0,pagina=0;
    do {
      if(pagina++>0) {pdf.newPage();c=writer.getDirectContent();}
      fundo(d,pagina); cabecalho(d);
      painel(areaDocumento); secao(areaDocumento,"01", "DADOS DO DOCUMENTO");
      float dy=areaDocumento[1]+40, limite=areaDocumento[1]+areaDocumento[3]-14;
      if(deslocamentoDocumento>=documento.size()) {
        List<Linha> resumo=new ArrayList<>();campo(resumo,"Documento",d.documento(),false,areaDocumento[2]-36);campo(resumo,"SHA-256 original",d.hash(),true,areaDocumento[2]-36);
        for(var l:resumo) {if(dy+12>limite) break;desenharLinha(l,areaDocumento[0]+18,dy);dy+=12;}
      } else while(deslocamentoDocumento<documento.size() && dy+12<=limite) {desenharLinha(documento.get(deslocamentoDocumento++),areaDocumento[0]+18,dy);dy+=12;}
      // Os participantes paginam dentro da área reservada; nenhuma evidência é descartada.
      boolean grade=Set.of("atlas","botanica","prisma").contains(modelo.get("design"));
      float sy=sigArea[1];
      if(d.signatarios().isEmpty()) {painel(sigArea);secao(sigArea,"02","DADOS DA ASSINATURA");txt("Nenhum signatário registrado.",normal,tamanho,cor("tinta"),sigArea[0]+18,sy+44);}
      while(pessoa<d.signatarios().size() && sy+65<sigArea[1]+sigArea[3]) {
        var p=d.signatarios().get(pessoa);var linhas=linhasPessoa(p,grade?(sigArea[2]-58)/2:sigArea[2]-58);
        float disponivel=sigArea[1]+sigArea[3]-sy;int capacidade=Math.max(1,(int)((disponivel-66)/14));
        int n=Math.min(linhas.size()-offset,capacidade*(grade?2:1)); int porColuna=grade?(n+1)/2:n;
        float h=59+porColuna*14;
        var a=new float[]{sigArea[0],sy,sigArea[2],h};painel(a);
        String design=modelo.get("design").toString();
        if(design.equals("editorial"))box(sigArea[0],sy,31,31,0,cor("destaque"));
        else if(design.equals("noir"))poligono(new float[]{sigArea[0]+22,sy+10,sigArea[0]+35,sy+23,sigArea[0]+22,sy+36,sigArea[0]+9,sy+23},cor("destaque"));
        else bolha(sigArea[0]+22,sy+23,11,cor("destaque"));
        txt(String.format("%02d",pessoa+1),forte,8,contraste(cor("destaque")),sigArea[0]+(design.equals("editorial")?9:16),sy+(design.equals("editorial")?9:18));
        String nome=valor(p,"SignatarioNome"); float fs=11;
        while(forte.getWidthPoint(nome,fs)>sigArea[2]-64 && fs>7) fs-=.5f;
        // O nome completo também está nas linhas, para nomes maiores que o cabeçalho.
        txt(forte.getWidthPoint(nome,fs)>sigArea[2]-64?"Participante "+(pessoa+1):nome,forte,fs,cor("tinta"),sigArea[0]+42,sy+16);
        if(offset>0) txt("CONTINUAÇÃO",forte,6,cor("destaque"),sigArea[0]+42,sy+30);
        linha(sigArea[0]+20,sy+40,sigArea[0]+sigArea[2]-20,sy+40,cor("secundaria"),.6f);
        if(grade)linha(sigArea[0]+sigArea[2]/2,sy+52,sigArea[0]+sigArea[2]/2,sy+h-12,cor("secundaria"),.5f);
        for(int i=0;i<n;i++) {
          int coluna=grade?i/porColuna:0, linha=grade?i%porColuna:i;
          desenharLinha(linhas.get(offset+i),sigArea[0]+20+coluna*(sigArea[2]/2),sy+53+linha*14);
        }
        offset+=n; sy+=h+12;
        if(offset>=linhas.size()) {offset=0;pessoa++;} else break;
      }
      integridade(d,pagina);
    } while(deslocamentoDocumento<documento.size() || pessoa<d.signatarios().size());
    // Imagens são evidências reais; acompanham os dados em folhas com a mesma identidade.
    for(var p:d.signatarios()) if(Boolean.TRUE.equals(p.get("SignatarioAssinou"))) {
      var imagens=new ArrayList<Map.Entry<String,byte[]>>();
      for(var entrada:List.of(Map.entry("SignatarioAssinaturaEscritaBase64","Assinatura manuscrita"),Map.entry("SignatarioSelfieBase64","Selfie"))) {
        String v=valor(p,entrada.getKey()); if(v.isBlank()) continue;
        byte[] bytes=new PdfAssineai(new ServicoCamposPdf()).imagem(v,false); if(bytes!=null)imagens.add(Map.entry(entrada.getValue(),bytes));
      }
      if(imagens.isEmpty()) continue;
      pdf.newPage();c=writer.getDirectContent();fundo(d,++pagina);cabecalho(d);
      int indice=0;
      for(var entrada:imagens) {
        var a=area(indice++==0?"documento":"signatarios");painel(a);
        txt(entrada.getKey()+" · "+valor(p,"SignatarioNome"),forte,8,cor("tinta"),a[0]+16,a[1]+13);
        var img=Image.getInstance(entrada.getValue());img.scaleToFit(a[2]-40,a[3]-56);
        img.setAbsolutePosition(a[0]+20,842-(a[1]+40)-img.getScaledHeight());c.addImage(img);
      }
      integridade(d,pagina);
    }
    pdf.close();return saidaAtual.toByteArray();
  }
  private List<Linha> linhasPessoa(Map<String,Object> p,float w) {
    var linhas=new ArrayList<Linha>();
    for(var f:List.of(new String[]{"Nome","SignatarioNome"},new String[]{"Qualificação","SignatarioQualificacao"},
      new String[]{"Situação","_situacao"},new String[]{"Assinou em","SignatarioDataAssinatura"},new String[]{"Métodos","SignatarioFirmadoVia"},
      new String[]{"E-mail","SignatarioEmail"},new String[]{"WhatsApp","SignatarioWhatsApp"},new String[]{"CPF","SignatarioCPF"},new String[]{"RG","SignatarioRG"},
      new String[]{"IP","SignatarioIp"},new String[]{"Dispositivo","SignatarioDispositivo"},new String[]{"Localização informada","SignatarioGeolocalizacao"})) {
      String v=f[1].equals("_situacao")?(Boolean.TRUE.equals(p.get("SignatarioAssinou"))?"Assinado":"Assinatura pendente"):valor(p,f[1]);
      for(String l:quebrar(f[0]+": "+(v.isBlank()?"Não informado":v),normal,tamanho,w)) linhas.add(new Linha(l,false,false));
    }
    return linhas;
  }
  private void campo(List<Linha> linhas,String rotulo,String valor,boolean codigo,float w) {
    linhas.add(new Linha(rotulo.toUpperCase(Locale.ROOT),true,false));
    for(String linha:quebrar(valor==null || valor.isBlank()?"Não informado":valor,codigo?mono:normal,codigo?7.5f:tamanho,w)) linhas.add(new Linha(linha,false,codigo));
  }
  private void desenharLinha(Linha l,float x,float y) {txt(l.texto(),l.rotulo()?forte:l.mono()?mono:normal,l.rotulo()?6.7f:l.mono()?7.5f:tamanho,l.rotulo()?cor("destaque"):cor("tinta"),x,y);}
  private void cabecalho(Dados d) throws Exception {
    var a=area("cabecalho"); float x=a[0],y=a[1],w=a[2];
    String design=modelo.get("design").toString();boolean escuro=design.equals("noir");
    Color corTexto=escuro?cor("tinta"):cor("tinta");
    String logo=modelo.get("logo").toString();float logoW=0;
    if(!logo.isEmpty()) {var img=Image.getInstance(Base64.getDecoder().decode(logo.substring(logo.indexOf(',')+1)));img.scaleToFit(92,36);img.setAbsolutePosition(x+w-img.getScaledWidth(),842-y-img.getScaledHeight());c.addImage(img);logoW=106;}
    float tamanhoNome=9;var nomes=quebrar(d.empresa(),forte,tamanhoNome,w-logoW-20);
    for(int i=0;i<nomes.size();i++)txt(nomes.get(i),forte,tamanhoNome,cor("destaque"),x,y+i*11);
    float ty=y+Math.max(34,nomes.size()*11+12);
    float fs=design.equals("editorial")?34:design.equals("noir")?32:design.equals("prisma")?31:27;
    String textoTitulo=modelo.get("titulo").toString();
    if(Set.of("noir","prisma","editorial").contains(design) && textoTitulo.equals("Registro de assinaturas"))textoTitulo="Registro de\nassinaturas";
    var titulo=quebrar(textoTitulo,design.equals("prisma")?forte:normal,fs,w-24);
    while(titulo.size()*fs*1.05> y+a[3]-32-ty && fs>12) {fs--;titulo=quebrar(textoTitulo,design.equals("prisma")?forte:normal,fs,w-24);}
    for(String l:titulo) {float transacoes=design.equals("noir")?x+(w-normal.getWidthPoint(l,fs))/2:x;txt(l,design.equals("prisma")?forte:normal,fs,corTexto,transacoes,ty);ty+=fs*1.05f;}
    txt(modelo.get("subtitulo").toString(),forte,6.2f,cor("destaque"),x,y+a[3]-18);
    linha(x,y+a[3],x+w,y+a[3],cor("destaque"),design.equals("editorial")?3:1);
  }
  private void integridade(Dados d,int pagina) {
    var a=area("integridade");
    linha(a[0],a[1],a[0]+a[2],a[1],cor("destaque"),1);
    txt("INTEGRIDADE & RASTREABILIDADE",forte,7.5f,cor("destaque"),a[0],a[1]+12);
    txt("Emitido em "+d.emissao(),normal,7,cor("tinta"),a[0],a[1]+27);
    txt("Confira os dados e a assinatura criptográfica no seu leitor de PDF.",normal,7,cor("tinta"),a[0],a[1]+39);
    float ry=a[1]+54;
    for(String l:quebrar(modelo.get("rodape").toString(),normal,6.5f,a[2]-40)){txt(l,normal,6.5f,cor("destaque"),a[0],ry);ry+=8;}
    txt(String.format("%02d",pagina),forte,12,cor("destaque"),a[0]+a[2]-20,a[1]+17);
    if(d.autenticidade()!=null && !d.autenticidade().isBlank())txt("VERIFICAR AUTENTICIDADE →",forte,6.5f,cor("destaque"),a[0],816);
  }
  private void secao(float[] a,String numero,String titulo) {
    String design=modelo.get("design").toString();
    if(design.equals("botanica")){box(a[0]+14,a[1]+10,a[2]-28,22,11,cor("secundaria"));}
    if(design.equals("atlas")){box(a[0],a[1],a[2],31,0,cor("secundaria"));}
    if(design.equals("prisma")){poligono(new float[]{a[0]+a[2]-48,a[1],a[0]+a[2],a[1],a[0]+a[2],a[1]+48},cor("secundaria"));}
    txt(numero,forte,9,cor("destaque"),a[0]+18,a[1]+16);txt(titulo,forte,8,cor("tinta"),a[0]+42,a[1]+16);
    linha(a[0]+18,a[1]+31,a[0]+a[2]-18,a[1]+31,cor("secundaria"),.5f);
  }
  private void painel(float[] a) {box(a[0],a[1],a[2],a[3],((Number)modelo.get("raio")).floatValue(),cor("papel"));
    if(modelo.get("design").equals("editorial"))box(a[0],a[1],3,a[3],0,cor("destaque"));
    if(modelo.get("design").equals("noir")) {c.setColorStroke(cor("secundaria"));c.setLineWidth(.5f);c.rectangle(a[0],842-a[1]-a[3],a[2],a[3]);c.stroke();}
  }
  private void fundo(Dados dados,int pagina) {
    box(0,0,595,842,0,cor("fundo"));String design=modelo.get("design").toString();
    String fundoImagem=Objects.toString(modelo.get("fundoImagem"),"");
    if(!fundoImagem.isEmpty()) {
      try {
        var imagem=Image.getInstance(Base64.getDecoder().decode(fundoImagem.substring(fundoImagem.indexOf(',')+1)));
        imagem.scaleToFit(595,842);imagem.setAbsolutePosition((595-imagem.getScaledWidth())/2,(842-imagem.getScaledHeight())/2);
        var estado=new PdfGState();estado.setFillOpacity(((Number)modelo.getOrDefault("opacidade",0.08)).floatValue());
        c.saveState();c.setGState(estado);c.addImage(imagem);c.restoreState();
      } catch(Exception e) {throw new IllegalArgumentException("Imagem de fundo inválida",e);}
    }
    switch(design) {
      case "atlas" -> {
        for(int x=0;x<595;x+=22)linha(x,0,x,186,cor("secundaria"),.3f);
        for(int y=0;y<186;y+=22)linha(0,y,595,y,cor("secundaria"),.3f);
        poligono(new float[]{440,0,595,0,595,185,556,150},cor("secundaria"));
        poligono(new float[]{570,0,595,0,595,112,570,87},cor("destaque"));
        linha(18,24,18,818,cor("destaque"),1.5f);
        for(int y=26;y<820;y+=14)linha(15,y,y%28==0?25:20,y,cor("destaque"),.5f);
      }
      case "botanica" -> {
        for(int i=0;i<9;i++) {float x=570-i*10,y=20+i*14;folha(x,y,35,15,-i*5,cor("secundaria"));}
        for(int i=0;i<7;i++)folha(12+i*8,800-i*13,28,13,i*6,cor("secundaria"));
        linha(564,20,500,158,cor("destaque"),.6f);linha(20,817,63,718,cor("destaque"),.6f);
        circulo(510,92,80,cor("secundaria"));circulo(510,92,64,cor("fundo"));
      }
      case "noir" -> {
        c.setLineWidth(.7f);c.setColorStroke(cor("destaque"));c.rectangle(18,18,559,806);c.stroke();c.rectangle(23,23,549,796);c.stroke();
        for(int side:new int[]{0,1}) for(int i=0;i<5;i++) {float x=side==0?18:577;float dir=side==0?1:-1;linha(x,18+i*6,x+dir*(72-i*12),18,cor("destaque"),1);linha(x,824-i*6,x+dir*(72-i*12),824,cor("destaque"),1);}
        poligono(new float[]{475,75,498,98,475,121,452,98},cor("destaque"));
        poligono(new float[]{475,83,490,98,475,113,460,98},cor("fundo"));
      }
      case "prisma" -> {
        poligono(new float[]{0,0,595,0,595,24,120,24,0,40},cor("destaque"));
        poligono(new float[]{595,24,595,195,460,24},cor("secundaria"));
        poligono(new float[]{0,842,595,842,595,826,505,810,415,826,0,826},cor("destaque"));
        for(int i=0;i<8;i++)poligono(new float[]{557-i*8,111+i*5,570-i*8,111+i*5,580-i*8,121+i*5,567-i*8,121+i*5},cor("secundaria"));
      }
      case "editorial" -> {
        box(0,0,14,842,0,cor("destaque"));
        txt("R",normal,174,cor("secundaria"),430,28);
        txt("REGISTRO / "+String.format("%02d",pagina),mono,7,cor("destaque"),42,20);
        for(int i=0;i<6;i++)linha(544+i*4,786,544+i*4,816-i*2,cor("destaque"),i%2==0?1:2);
      }
      case "linho" -> {
        for(int y=0;y<842;y+=4)linha(0,y,595,y,misturar(cor("fundo"),cor("secundaria"),.14f),.2f);
        for(int x=0;x<595;x+=8)linha(x,0,x,842,misturar(cor("fundo"),cor("secundaria"),.1f),.2f);
        circulo(508,78,29,cor("secundaria"));
        String iniciais=Arrays.stream(dados.empresa().split("\\s+")).filter(s->!s.isBlank()).limit(2).map(s->s.substring(0,1)).reduce("",String::concat);
        txt(iniciais.toUpperCase(Locale.ROOT),normal,19,cor("destaque"),490,66);
        linha(30,30,565,30,cor("destaque"),.6f);linha(30,812,565,812,cor("destaque"),.6f);
      }
    }
  }
  private float[] area(String chave) {var a=TemplatesEmpresa.objeto(areas.get(chave));return new float[]{((Number)a.get("x")).floatValue(),((Number)a.get("y")).floatValue(),((Number)a.get("w")).floatValue(),((Number)a.get("h")).floatValue()};}
  private Color cor(String chave) {return Color.decode(cores.get(chave).toString());}
  private static Color contraste(Color c) {return c.getRed()*.299+c.getGreen()*.587+c.getBlue()*.114>145?new Color(20,25,30):Color.WHITE;}
  private static Color misturar(Color a,Color b,float p) {return new Color((int)(a.getRed()*(1-p)+b.getRed()*p),(int)(a.getGreen()*(1-p)+b.getGreen()*p),(int)(a.getBlue()*(1-p)+b.getBlue()*p));}
  private void txt(String s,BaseFont f,float tamanhoAtual,Color corAtual,float x,float topo) {c.beginText();c.setFontAndSize(f,tamanhoAtual);c.setColorFill(corAtual);c.setTextMatrix(x,842-topo-tamanhoAtual);c.showText(limpar(s));c.endText();}
  private void box(float x,float y,float w,float h,float r,Color corAtual) {c.setColorFill(corAtual);c.roundRectangle(x,842-y-h,w,h,r);c.fill();}
  private void linha(float x,float y,float xx,float yy,Color corAtual,float width) {c.setColorStroke(corAtual);c.setLineWidth(width);c.moveTo(x,842-y);c.lineTo(xx,842-yy);c.stroke();}
  private void circulo(float x,float y,float raio,Color corAtual) {c.setColorFill(corAtual);c.circle(x,842-y,raio);c.fill();}
  private void bolha(float x,float y,float raio,Color corAtual) {circulo(x,y,raio,corAtual);}
  private void folha(float x,float y,float w,float h,float angulo,Color corAtual) {c.saveState();double a=Math.toRadians(angulo);c.concatCTM((float)Math.cos(a),(float)Math.sin(a),(float)-Math.sin(a),(float)Math.cos(a),x,842-y);c.setColorFill(corAtual);c.moveTo(0,0);c.curveTo(w*.1f,h,w*.8f,h,w,0);c.curveTo(w*.8f,-h,w*.1f,-h,0,0);c.fill();c.restoreState();}
  private void poligono(float[] p,Color corAtual) {c.setColorFill(corAtual);c.moveTo(p[0],842-p[1]);for(int i=2;i<p.length;i+=2)c.lineTo(p[i],842-p[i+1]);c.closePath();c.fill();}
  private static String valor(Map<String,Object> m,String chave) {return Objects.toString(m.get(chave),"");}
  private static String limpar(String s) {return s==null?"":s.replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");}
  private static List<String> quebrar(String s,BaseFont fonte,float tamanhoAtual,float largura) {
    var linhas=new ArrayList<String>();
    for(String paragrafo:limpar(s).split("\\n",-1)) {
      String resto=paragrafo.strip();if(resto.isEmpty()){linhas.add("");continue;}
      while(!resto.isEmpty()) {int fim=resto.length();while(fim>1 && fonte.getWidthPoint(resto.substring(0,fim),tamanhoAtual)>largura)fim--;
        if(fim<resto.length()){int espaco=resto.lastIndexOf(' ',fim);if(espaco>fim/2)fim=espaco;}
        linhas.add(resto.substring(0,fim).strip());resto=resto.substring(fim).stripLeading();
      }
    }return linhas;
  }
}
