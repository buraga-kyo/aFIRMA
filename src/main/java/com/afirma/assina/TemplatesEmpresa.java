package com.afirma.assina;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Layouts da folha de evidências acrescentada ao PDF. Os campos de prova são fixos. */
public final class TemplatesEmpresa {
  public static final Set<String> AREAS = Set.of("cabecalho", "documento", "signatarios", "integridade");
  private TemplatesEmpresa() {}
  public static List<Map<String,Object>> catalogo() {
    return List.of(
      assineai(),
      classico(),
      modelo("atlas", "Atlas / Engenharia da confiança", "Grade técnica, recortes diagonais e azul profundo.", "sans", "#f0f4f8", "#ffffff", "#172e46", "#16647b", "#d6e8ee", 8, 42, 40, 140, 202, 180, 404, 306),
      modelo("botanica", "Botânica / Assinatura orgânica", "Folhagem vetorial, verdes minerais e respiro editorial.", "serif", "#f5f5eb", "#ffffff", "#293e33", "#537b53", "#dce7cf", 18, 52, 42, 144, 206, 180, 406, 304),
      modelo("noir", "Noir / Edição de prestígio", "Moldura art déco, contraste noturno e detalhes em ouro.", "serif", "#171e28", "#222c39", "#f7f1e4", "#d7b87a", "#45515d", 0, 42, 38, 184, 242, 170, 432, 278),
      modelo("prisma", "Prisma / Movimento e precisão", "Faixas em camadas, cantos angulares e violeta intenso.", "sans", "#f5f3fc", "#ffffff", "#292143", "#6544a5", "#ded5f2", 12, 42, 40, 148, 208, 178, 406, 304),
      modelo("editorial", "Editorial / Registro em destaque", "Tipografia de revista, vermelho terracota e grande numeração.", "serif", "#fbf4ea", "#fffcf6", "#432c28", "#af4c36", "#edcebd", 2, 42, 48, 142, 212, 174, 406, 304),
      modelo("linho", "Linho / Papel de correspondência", "Monograma, filetes finos e composição de papel timbrado.", "serif", "#f9f8f3", "#ffffff", "#343b49", "#4d647e", "#e3e7eb", 4, 52, 44, 142, 206, 180, 406, 304)
    );
  }
  private static Map<String,Object> assineai() {
    var modelo = modelo("assineai_producao", "AssineAi / Modelo original de produção",
        "Relatório original: Helvetica, posições, filetes, QR e ícones de assinado e pendente.",
        "sans", "#ffffff", "#ffffff", "#242424", "#757575", "#c7c7c7",
        0, 26, 20, 35, 70, 70, 140, 550);
    modelo.put("design", "assineai");
    modelo.put("titulo", "Relatório de Assinaturas");
    modelo.put("subtitulo", "Datas e Horários em UTC-0300");
    modelo.put("rodape", "Confira as assinaturas digitais no seu leitor de PDF.");
    modelo.put("tamanho", 7);
    modelo.put("areas", Map.of("cabecalho", area(26,20,544,35), "documento", area(26,70,544,70),
        "signatarios", area(26,140,544,550), "integridade", area(26,710,544,90)));
    return modelo;
  }
  private static Map<String,Object> classico() {
    var modelo = modelo("classico", "daFIRMA / Relatório de assinaturas",
        "Folha branca, linhas finas, QR de autenticidade e assinantes em lista.",
        "sans", "#ffffff", "#ffffff", "#242424", "#757575", "#c7c7c7",
        0, 26, 26, 40, 78, 130, 220, 492);
    modelo.put("titulo", "Relatório de Assinaturas");
    modelo.put("subtitulo", "Datas e horários em UTC-0300");
    modelo.put("rodape", "Este registro integra o documento e identifica suas assinaturas.");
    modelo.put("tamanho", 8);
    return modelo;
  }
  private static Map<String,Object> modelo(String id, String nome, String descricao, String fonte,
      String fundo,String papel,String tinta,String destaque,String secundaria,int raio,
      int x,int y,int h,int dy,int dh,int sy,int sh) {
    var m = new LinkedHashMap<String,Object>();
    m.putAll(Map.of("id",id,"nome",nome,"descricao",descricao,"design",id,"fonte",fonte,
      "titulo","Registro de assinaturas","subtitulo","IDENTIDADE • INTEGRIDADE • RASTREABILIDADE",
      "rodape","Documento vinculado à identidade da nossa empresa.","logo","","tamanho",9));
    m.put("raio",raio); m.put("cores",Map.of("fundo",fundo,"papel",papel,"tinta",tinta,"destaque",destaque,"secundaria",secundaria));
    int w = 595 - 2*x;
    m.put("areas",Map.of("cabecalho",area(x,y,w,h),"documento",area(x,dy,w,dh),
      "signatarios",area(x,sy,w,sh),"integridade",area(x,730,w,72)));
    return m;
  }
  private static Map<String,Object> area(int x,int y,int w,int h) { return Map.of("x",x,"y",y,"w",w,"h",h); }
  public static Map<String,Object> padrao() { return catalogo().getFirst(); }
  public static Map<String,Object> validar(Object valor) {
    var raiz = objeto(valor);
    if (!Objects.equals(raiz.get("versao"),2) || !Set.of("versao","ativo","modelos").containsAll(raiz.keySet())) throw invalido();
    String ativo = texto(raiz,"ativo",80);
    if (!(raiz.get("modelos") instanceof List<?> modelos) || modelos.isEmpty() || modelos.size()>24) throw invalido();
    var ids = new HashSet<String>(); long tamanho = 0;
    for (Object item:modelos) {
      var m = objeto(item);
      if (!Set.of("id","nome","descricao","design","fonte","titulo","subtitulo","rodape","logo","tamanho","raio","cores","areas","fundoImagem","opacidade").containsAll(m.keySet())) throw invalido();
      String id=texto(m,"id",80); if (!id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw invalido();
      texto(m,"nome",120); texto(m,"descricao",200); texto(m,"titulo",80); texto(m,"subtitulo",120); texto(m,"rodape",160);
      if (!Set.of("assineai","classico","atlas","botanica","noir","prisma","editorial","linho","livre").contains(texto(m,"design",20)) || !Set.of("sans","serif","mono").contains(texto(m,"fonte",10))) throw invalido();
      numero(m,"tamanho",m.get("design").equals("assineai")?7:8,12); numero(m,"raio",0,26);
      var cores=objeto(m.get("cores"));
      if (!cores.keySet().equals(Set.of("fundo","papel","tinta","destaque","secundaria"))) throw invalido();
      for(String cor:cores.keySet()) if(!texto(cores,cor,7).matches("#[0-9a-fA-F]{6}")) throw invalido();
      if(m.containsKey("fundoImagem")) imagem(texto(m,"fundoImagem",700000));
      if(m.containsKey("opacidade")) numero(m,"opacidade",0,0.35);
      imagem(texto(m,"logo",700000)); tamanho+=m.toString().length(); if(tamanho>4000000) throw invalido();
      var areas=objeto(m.get("areas")); if(!areas.keySet().equals(AREAS)) throw invalido();
      for(String chave:AREAS) {
        var a=objeto(areas.get(chave)); if(!a.keySet().equals(Set.of("x","y","w","h"))) throw invalido();
        double x=numero(a,"x",20,175), y=numero(a,"y",20,800), w=numero(a,"w",400,555), h=numero(a,"h",chave.equals("signatarios")?180:chave.equals("documento")?(m.get("design").equals("assineai")?70:130):chave.equals("cabecalho")?(m.get("design").equals("assineai")?35:m.get("design").equals("classico")?40:110):60,650);
        if(x+w>575 || y+h>815) throw invalido();
      }
      var posicoes=new ArrayList<>(areas.values());
      for(int i=0;i<posicoes.size();i++) for(int j=i+1;j<posicoes.size();j++) {
        var a=objeto(posicoes.get(i)); var b=objeto(posicoes.get(j));
        if(n(a,"x")<n(b,"x")+n(b,"w") && n(b,"x")<n(a,"x")+n(a,"w") && n(a,"y")<n(b,"y")+n(b,"h") && n(b,"y")<n(a,"y")+n(a,"h")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Os blocos de dados não podem se sobrepor.");
      }
    }
    if(!ids.contains(ativo)) throw invalido(); return raiz;
  }
  public static Map<String,Object> ativo(Object valor) {
    if(!(valor instanceof Map<?,?> raiz) || !Objects.equals(raiz.get("versao"),2)) return padrao();
    var v=biblioteca(valor);
    return ((List<?>)v.get("modelos")).stream().map(TemplatesEmpresa::objeto).filter(m->Objects.equals(m.get("id"),v.get("ativo"))).findFirst().orElseThrow();
  }
  public static Map<String,Object> biblioteca(Object valor) {
    if (!(valor instanceof Map<?,?> raiz) || !Objects.equals(raiz.get("versao"), 2))
      return Map.of("versao", 2, "ativo", "assineai_producao", "modelos", catalogo());
    var existente = validar(valor);
    var modelos = new ArrayList<Object>((List<?>) existente.get("modelos"));
    boolean contemOriginal = modelos.stream().map(TemplatesEmpresa::objeto)
        .anyMatch(modelo -> "assineai_producao".equals(modelo.get("id")));
    String ativo = existente.get("ativo").toString();
    boolean padraoAnterior = modelos.stream().map(TemplatesEmpresa::objeto)
        .anyMatch(modelo -> ativo.equals(modelo.get("id")) && modelo.equals(classico()));
    boolean adicionarOriginal = !contemOriginal && modelos.size() < 24;
    if (adicionarOriginal) modelos.addFirst(assineai());
    var atualizada = new LinkedHashMap<>(existente);
    atualizada.put("modelos", modelos);
    // Somente o padrão intacto é substituído; uma escolha ou edição da empresa é preservada.
    if (padraoAnterior && (contemOriginal || adicionarOriginal)) atualizada.put("ativo", "assineai_producao");
    return atualizada;
  }
  public static Map<String,Object> lerModelo(String valor) {
    try { return validarModelo(new tools.jackson.databind.ObjectMapper().readValue(valor,Map.class)); }
    catch(ResponseStatusException e) {throw e;}
    catch(Exception e) {throw invalido();}
  }
  public static Map<String,Object> validarModelo(Object m) {
    var modelo=objeto(m); validar(Map.of("versao",2,"ativo",modelo.getOrDefault("id",""),"modelos",List.of(modelo))); return modelo;
  }
  public static double n(Map<String,Object> m,String chave) {return ((Number)m.get(chave)).doubleValue();}
  private static double numero(Map<String,Object> m,String chave,double menorValor,double maiorValor) {
    if(!(m.get(chave) instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<menorValor || n.doubleValue()>maiorValor) throw invalido(); return n.doubleValue();
  }
  public static void imagem(String valor) {
    if(valor.isEmpty()) return;
    if(!valor.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+")) throw invalido();
    try {
      byte[] b=Base64.getDecoder().decode(valor.substring(valor.indexOf(',')+1)); if(b.length>500000) throw invalido();
      try(var in=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(b))) {
        var leitores=javax.imageio.ImageIO.getImageReaders(in); if(!leitores.hasNext()) throw invalido();
        var leitor=leitores.next(); try {leitor.setInput(in); long w=leitor.getWidth(0),h=leitor.getHeight(0);
          if(w>4000 || h>4000 || w*h>6000000 || !valor.startsWith("data:image/"+leitor.getFormatName().toLowerCase(Locale.ROOT)+";")) throw invalido();
        } finally {leitor.dispose();}
      }
    } catch(Exception e) {throw invalido();}
  }
  public static String texto(Map<String,Object> m,String chave,int maiorValor) {
    if(!(m.get(chave) instanceof String s) || s.length()>maiorValor) throw invalido(); return s;
  }
  @SuppressWarnings("unchecked") public static Map<String,Object> objeto(Object v) {if(!(v instanceof Map<?,?>)) throw invalido();return (Map<String,Object>)v;}
  private static ResponseStatusException invalido() {return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Modelo inválido. Preserve os quatro blocos de evidências, seus limites e os campos fixos.");}
}
