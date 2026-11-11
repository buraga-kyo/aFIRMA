package com.afirma.assina;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;

/** Mantém a composição do gerador AssineAi de produção, com continuação para dados extensos. */
final class PaginaRelatorioAssineai {
    private static final float ALTURA = 840;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final Map<String, Object> modelo;
    private final PaginaRegistroAssinaturas.Dados dados;
    private final BaseFont normal, negrito;
    private final Color tinta, cinza, linha;
    private final float tamanho;
    private Document documento;
    private PdfWriter gravador;
    private PdfContentByte desenho;
    private float posicao;

    private PaginaRelatorioAssineai(Map<String, Object> modelo, PaginaRegistroAssinaturas.Dados dados) throws Exception {
        this.modelo = TemplatesEmpresa.validarModelo(modelo); this.dados = dados;
        String familia = Objects.toString(modelo.get("fonte"));
        normal = BaseFont.createFont(familia.equals("serif") ? BaseFont.TIMES_ROMAN : familia.equals("mono") ? BaseFont.COURIER : BaseFont.HELVETICA, BaseFont.CP1252, false);
        negrito = BaseFont.createFont(familia.equals("serif") ? BaseFont.TIMES_BOLD : familia.equals("mono") ? BaseFont.COURIER_BOLD : BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
        var cores = TemplatesEmpresa.objeto(modelo.get("cores"));
        tinta = Color.decode(cores.get("tinta").toString()); cinza = Color.decode(cores.get("destaque").toString());
        linha = Color.decode(cores.get("secundaria").toString()); tamanho = ((Number) modelo.get("tamanho")).floatValue();
    }
    static byte[] gerar(Map<String, Object> modelo, PaginaRegistroAssinaturas.Dados dados) throws Exception {
        return new PaginaRelatorioAssineai(modelo, dados).gerar();
    }
    private byte[] gerar() throws Exception {
        var saida = new ByteArrayOutputStream();
        documento = new Document(new Rectangle(595, ALTURA), 0, 0, 0, 0);
        gravador = PdfWriter.getInstance(documento, saida);
        documento.addTitle("Relatório de Assinaturas"); documento.addAuthor(dados.empresa()); documento.open();
        novaFolha(false);
        for (var pessoa : dados.signatarios()) participante(pessoa);
        rodape();
        selfies();
        documento.close(); return saida.toByteArray();
    }
    private Map<String, Object> area(String nome) { return TemplatesEmpresa.objeto(TemplatesEmpresa.objeto(modelo.get("areas")).get(nome)); }
    private float medida(String area, String nome) { return ((Number) area(area).get(nome)).floatValue(); }
    private void novaFolha(boolean continuar) throws Exception {
        if (continuar) documento.newPage();
        desenho = gravador.getDirectContent();
        var cores = TemplatesEmpresa.objeto(modelo.get("cores"));
        desenho.setColorFill(Color.decode(cores.get("fundo").toString())); desenho.rectangle(0, 0, 595, ALTURA); desenho.fill();
        String fundo = Objects.toString(modelo.get("fundoImagem"), "");
        if (!fundo.isBlank()) {
            desenho.saveState(); var transparencia = new PdfGState();
            transparencia.setFillOpacity(((Number) modelo.getOrDefault("opacidade", .08)).floatValue()); desenho.setGState(transparencia);
            imagem(Base64.getDecoder().decode(fundo.substring(fundo.indexOf(',') + 1)), 0, 0, 595, ALTURA); desenho.restoreState();
        }
        identificacaoRodape();
        cabecalho(); float fimDocumento = dadosDocumento();
        float esquerda = medida("signatarios", "x"), topo = Math.max(medida("signatarios", "y"), fimDocumento + 5);
        escrever("Assinaturas", esquerda, topo + 15, 15, true, tinta);
        filete(esquerda + 94, topo + 10, esquerda + medida("signatarios", "w"));
        posicao = topo + 50;
    }
    private void cabecalho() {
        float esquerda = medida("cabecalho", "x"), topo = medida("cabecalho", "y"), largura = medida("cabecalho", "w");
        String titulo = modelo.get("titulo").toString(); float pontos = 20;
        while (pontos > 8 && negrito.getWidthPoint(titulo, pontos) > largura - 115) pontos -= .5f;
        escrever(titulo, esquerda, topo + 20, pontos, true, tinta);
        escrever("Gerado: " + data(dados.emissao()), esquerda + largura - 100, topo + 10, 7, false, cinza);
        escrever(modelo.get("subtitulo").toString(), esquerda + largura - 100, topo + 20, 7, false, cinza);
        filete(esquerda, topo + medida("cabecalho", "h"), esquerda + largura);
    }
    private float dadosDocumento() throws Exception {
        float esquerda = medida("documento", "x"), topo = medida("documento", "y"), largura = medida("documento", "w");
        String titulo = dados.documento().toLowerCase(Locale.ROOT).endsWith(".pdf") ? dados.documento() : dados.documento() + ".pdf";
        float linhaTitulo = topo + 20;
        for (String trecho : quebrar(titulo, negrito, 12, largura - 75)) {
            escrever(trecho, esquerda, linhaTitulo, 12, true, tinta); linhaTitulo += 14;
        }
        float linhaDados = Math.max(topo + 33, linhaTitulo - 1);
        linhaDados = campoDocumento("Identificação do documento:", dados.identificador(), esquerda, linhaDados, largura - 75);
        float fim = campoDocumento("Hash do documento original (SHA256):", dados.hash(), esquerda, linhaDados, largura - 75);
        if (dados.autenticidade() != null && dados.autenticidade().matches("https?://[^\\s]+")) {
            var matriz = new com.google.zxing.qrcode.QRCodeWriter().encode(dados.autenticidade(), com.google.zxing.BarcodeFormat.QR_CODE,
                    240, 240, Map.of(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H));
            var qr = new java.awt.image.BufferedImage(240, 240, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 240; y++) for (int x = 0; x < 240; x++) qr.setRGB(x, y, matriz.get(x, y) ? 0xff000000 : 0xffffffff);
            var figura = Image.getInstance(qr, null); figura.scaleAbsolute(60, 60);
            float x = esquerda + largura - 57, y = ALTURA - topo - 61;
            figura.setAbsolutePosition(x, y); desenho.addImage(figura);
            var link = PdfAnnotation.createLink(gravador, new Rectangle(x, y, x + 60, y + 60), PdfAnnotation.HIGHLIGHT_INVERT, new PdfAction(dados.autenticidade()));
            link.setBorder(new PdfBorderArray(0, 0, 0)); gravador.addAnnotation(link);
        }
        return fim;
    }
    private float campoDocumento(String rotulo, String valor, float x, float topo, float largura) {
        float inicio = Math.max(rotulo.startsWith("Identificação") ? 111 : 151, negrito.getWidthPoint(rotulo, 8) + 3);
        var trechos = quebrar(valor, normal, 8, largura - inicio);
        escrever(rotulo, x, topo, 8, true, cinza);
        for (String trecho : trechos) { escrever(trecho, x + inicio, topo, 8, false, cinza); topo += 10; }
        return topo;
    }
    private void participante(Map<String, Object> pessoa) throws Exception {
        boolean assinou = Boolean.TRUE.equals(pessoa.get("SignatarioAssinou"));
        var linhas = new ArrayList<String>();
        String qualificacao = valor(pessoa, "SignatarioQualificacao");
        linhas.add(assinou ? "Documento assinado" + (qualificacao.isBlank() ? "" : " como " + qualificacao) : "Assinatura pendente");
        if (assinou) {
            adicionar(linhas, "Responsável pela empresa: ", valor(pessoa, "SignatarioResponsavel"));
            adicionar(linhas, "CNPJ: ", valor(pessoa, "SignatarioCNPJ"));
            adicionar(linhas, "E-mail da empresa: ", valor(pessoa, "SignatarioEmailEmpresa"));
            linhas.add("Pontos de autenticação:");
            adicionar(linhas, "IP: ", valor(pessoa, "SignatarioIp"));
            if ("NAVEGADOR_INFORMADO".equals(pessoa.get("SignatarioOrigemIp"))) linhas.add("IP público obtido pelo navegador");
            String localizacao = valor(pessoa, "SignatarioGeolocalizacao");
            if (!localizacao.isBlank()) linhas.add("Localização aproximada: " + ColetaDadosAssinatura.descreverLocalizacao(localizacao));
            adicionar(linhas, "Contexto do acesso: ", valor(pessoa, "SignatarioContextoAcesso"));
            adicionar(linhas, "Dispositivo: ", valor(pessoa, "SignatarioDispositivo"));
            adicionar(linhas, "Assinado em ", data(valor(pessoa, "SignatarioDataAssinatura")));
            String metodos = valor(pessoa, "SignatarioFirmadoVia");
            String email = valor(pessoa, "SignatarioEmail"), celular = valor(pessoa, "SignatarioWhatsApp");
            if (!email.isBlank()) linhas.add("Email: " + email + (canal(metodos, "email") ? " (autenticado com código enviado exclusivamente a este e-mail)" : ""));
            if (!celular.isBlank()) linhas.add("Celular: " + celular + (canal(metodos, "whatsapp") ? " (autenticado com código enviado exclusivamente a este WhatsApp)" : ""));
            adicionar(linhas, "CPF: ", valor(pessoa, "SignatarioCPF"));
            adicionar(linhas, "RG: ", valor(pessoa, "SignatarioRG"));
            adicionar(linhas, "Representado: ", valor(pessoa, "SignatarioRepresentado"));
            adicionar(linhas, "Fundamento da representação: ", valor(pessoa, "SignatarioFundamentoRepresentacao"));
            adicionar(linhas, "Métodos: ", metodos(valor(pessoa, "SignatarioFirmadoVia")));
        }
        float esquerda = medida("signatarios", "x"), largura = medida("signatarios", "w") - 21;
        String nome = valor(pessoa, "SignatarioNome");
        float alturaNome = quebrar(nome, negrito, 10, largura).size() * 11;
        if (posicao + Math.max(40, alturaNome + 10) > limiteParticipantes()) novaFolha(true);
        desenharNome(nome, esquerda, largura, assinou);
        for (String linhaPessoa : linhas) for (String trecho : quebrar(linhaPessoa, normal, tamanho, largura)) {
            if (posicao + 10 > limiteParticipantes()) {
                novaFolha(true); desenharNome(nome + " (continuação)", esquerda, largura, assinou);
            }
            escrever(trecho, esquerda + 21, posicao + 10, tamanho, false, cinza); posicao += 10;
        }
        posicao += assinou ? 50 : 40;
    }
    private float limiteParticipantes() { return Math.min(760, medida("signatarios", "y") + medida("signatarios", "h")); }
    private void desenharNome(String nome, float esquerda, float largura, boolean assinou) throws Exception {
        imagem(recurso(assinou ? "assinado.png" : "pendente.png"), esquerda - 1, posicao - 9, assinou ? 16 : 15, assinou ? 16 : 15);
        for (String trecho : quebrar(nome, negrito, 10, largura)) {
            escrever(trecho, esquerda + 21, posicao, 10, true, tinta); posicao += 11;
        }
        posicao -= 11;
    }
    private void rodape() throws Exception {
        float esquerda = medida("integridade", "x"), largura = medida("integridade", "w");
        var linhas = new ArrayList<String>();
        linhas.addAll(quebrar("Integridade e assinaturas da empresa: " + dados.empresa(), normal, 9, largura - 44));
        linhas.addAll(quebrar("Este log é exclusivo e integra o documento " + dados.identificador(), normal, 9, largura - 44));
        linhas.addAll(quebrar(modelo.get("rodape").toString(), normal, 9, largura - 44));
        float topo = Math.min(posicao + 7, medida("integridade", "y"));
        if (topo + linhas.size() * 12 + 35 > 815 || topo < posicao - 40) { novaFolha(true); topo = posicao + 20; }
        String logo = Objects.toString(modelo.get("logo"), "");
        byte[] figura = logo.isBlank() ? recurso("ICP.png") : Base64.getDecoder().decode(logo.substring(logo.indexOf(',') + 1));
        var imagem = Image.getInstance(figura); imagem.scaleToFit(35.58f, 45.54f);
        imagem.setAbsolutePosition(esquerda, ALTURA - topo - imagem.getScaledHeight()); desenho.addImage(imagem);
        escrever("Registro de assinaturas do documento", esquerda + 44, topo + 4, 9, true, cinza);
        float linhaRodape = topo + 17;
        for (String trecho : linhas) { escrever(trecho, esquerda + 44, linhaRodape, 9, false, cinza); linhaRodape += 12; }
        filete(esquerda, linhaRodape + 10, esquerda + largura);
    }
    private void selfies() throws Exception {
        var pessoas = dados.signatarios().stream().filter(pessoa -> !valor(pessoa, "SignatarioSelfieBase64").isBlank()).toList();
        int indice = 0;
        for (var pessoa : pessoas) {
            if (indice % 3 == 0) {
                documento.newPage(); desenho = gravador.getDirectContent(); identificacaoRodape();
                escrever("Selfies dos Assinantes", 27, 42, 20, true, tinta); filete(27, 55, 570);
            }
            float topo = 100 + (indice++ % 3) * 200;
            desenho.setColorStroke(linha); desenho.setLineWidth(.1f); desenho.rectangle(27, ALTURA - topo - 180, 543, 180); desenho.stroke();
            desenho.moveTo(285, ALTURA - topo); desenho.lineTo(285, ALTURA - topo - 180); desenho.stroke();
            byte[] foto = new PdfAssineai(new ServicoCamposPdf()).imagem(pessoa.get("SignatarioSelfieBase64"), false);
            var imagem = Image.getInstance(foto); imagem.scaleToFit(220, 150); imagem.setAbsolutePosition(27 + (258 - imagem.getScaledWidth()) / 2, ALTURA - topo - 15 - imagem.getScaledHeight()); desenho.addImage(imagem);
            float linhaFoto = topo + 30;
            for (String trecho : quebrar("Foto do rosto de " + valor(pessoa, "SignatarioNome"), normal, 10, 250)) {
                escrever(trecho, 300, linhaFoto, 10, false, tinta); linhaFoto += 12;
            }
            escrever(data(valor(pessoa, "SignatarioDataAssinatura")), 300, linhaFoto + 12, 10, false, tinta);
            desenho.saveState(); var transparencia = new PdfGState(); transparencia.setFillOpacity(.3f); desenho.setGState(transparencia);
            escrever("CONFIDENCIAL", 40, topo + 100, 30, true, cinza); desenho.restoreState();
        }
    }
    private void identificacaoRodape() {
        escrever(dados.empresa() + " " + dados.identificador(), 26, 835, 6, false, cinza);
    }
    private void imagem(byte[] bytes, float x, float topo, float largura, float altura) throws Exception {
        var figura = Image.getInstance(bytes); figura.scaleAbsolute(largura, altura); figura.setAbsolutePosition(x, ALTURA - topo - altura); desenho.addImage(figura);
    }
    private static byte[] recurso(String nome) throws Exception {
        try (var arquivo = PaginaRelatorioAssineai.class.getResourceAsStream("/static/img/relatorio-assineai/" + nome)) {
            if (arquivo == null) throw new IllegalStateException("Imagem do relatório AssineAi indisponível");
            return arquivo.readAllBytes();
        }
    }
    private void escrever(String texto, float x, float topo, float pontos, boolean forte, Color cor) {
        desenho.beginText(); desenho.setFontAndSize(forte ? negrito : normal, pontos); desenho.setColorFill(cor);
        desenho.setTextMatrix(x, ALTURA - topo); desenho.showText(limpar(texto)); desenho.endText();
    }
    private void filete(float x, float topo, float fim) {
        desenho.setColorStroke(linha); desenho.setLineWidth(.5f); desenho.moveTo(x, ALTURA - topo); desenho.lineTo(fim, ALTURA - topo); desenho.stroke();
    }
    private static String valor(Map<String, Object> mapa, String campo) { return Objects.toString(mapa.get(campo), ""); }
    private static void adicionar(java.util.List<String> linhas, String rotulo, String valor) { if (!valor.isBlank()) linhas.add(rotulo + valor); }
    private static String limpar(String texto) { return texto == null ? "" : texto.replaceAll("[\\p{Cntrl}]", " "); }
    private static java.util.List<String> quebrar(String texto, BaseFont fonte, float pontos, float largura) {
        var linhas = new ArrayList<String>(); String restante = limpar(texto).strip();
        while (!restante.isEmpty()) {
            int fim = restante.length();
            while (fim > 1 && fonte.getWidthPoint(restante.substring(0, fim), pontos) > largura) fim--;
            if (fim < restante.length()) { int espaco = restante.lastIndexOf(' ', fim); if (espaco > fim / 2) fim = espaco; }
            linhas.add(restante.substring(0, fim).strip()); restante = restante.substring(fim).stripLeading();
        }
        return linhas;
    }
    private static String data(String valor) {
        try { return DATA.format(Instant.parse(valor)); } catch (RuntimeException ignorado) { }
        try { return DATA.format(OffsetDateTime.parse(valor)); } catch (RuntimeException ignorado) { }
        try {
            return DATA.format(OffsetDateTime.parse(valor.replace(" · ", " "), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")));
        } catch (RuntimeException ignorado) { return Objects.toString(valor, ""); }
    }
    private static boolean canal(String metodos, String canal) {
        return Arrays.stream(metodos.split(",")).map(String::strip).anyMatch(canal::equalsIgnoreCase);
    }
    private static String metodos(String valor) {
        return Arrays.stream(valor.split(",")).map(String::strip).map(metodo -> switch (metodo) {
            case "email" -> "Código por e-mail"; case "whatsapp" -> "Código por WhatsApp";
            case "tela" -> "Assinatura na tela"; case "rubrica" -> "Rubrica"; case "selfie" -> "Selfie";
            default -> metodo;
        }).collect(java.util.stream.Collectors.joining(", "));
    }
}
