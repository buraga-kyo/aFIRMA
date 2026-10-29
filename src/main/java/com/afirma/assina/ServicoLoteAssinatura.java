package com.afirma.assina;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoLoteAssinatura {
    private final ServicoEnvelopes envelopes;
    private final ValidadorPedidoAssinatura validador;
    private final ServicoCamposPdf pdfs;
    public ServicoLoteAssinatura(ServicoEnvelopes envelopes, ValidadorPedidoAssinatura validador,
            ServicoCamposPdf pdfs) {
        this.envelopes = envelopes; this.validador = validador; this.pdfs = pdfs;
    }
    @Transactional(rollbackFor = Exception.class)
    public ServicoEnvelopes.Criado criar(List<MultipartFile> arquivos, Pedido pedido, String criador) throws Exception {
        if (pedido == null || pedido.documentos() == null || arquivos == null || arquivos.isEmpty()
                || arquivos.size() > 10 || arquivos.size() != pedido.documentos().size()
                || pedido.signatarios() == null || pedido.signatarios().isEmpty()
                || pedido.nomeAssinatura() == null || pedido.nomeAssinatura().isBlank() || pedido.nomeAssinatura().length() > 255) throw erro("Informe de 1 a 10 documentos e os signatários");
        List<byte[]> originais = new ArrayList<>();
        List<Integer> inicios = new ArrayList<>();
        List<Integer> quantidades = new ArrayList<>();
        int total = 0;
        long bytes = 0;
        for (int i = 0; i < arquivos.size(); i++) {
            DocumentoNovo item = pedido.documentos().get(i);
            if (item == null || item.nome() == null || item.nome().isBlank() || item.nome().length() > 255)
                throw erro("Informe o nome de cada documento (até 255 caracteres)");
            byte[] pdf = validador.validarPdf(arquivos.get(i));
            bytes += pdf.length;
            if (bytes > 20L * 1024 * 1024) throw erro("O conjunto de documentos deve ter até 20 MB");
            int paginas = pdfs.paginas(pdf);
            inicios.add(total + 1); quantidades.add(paginas); total += paginas;
            if (total > 100) throw erro("O conjunto de documentos deve ter até 100 páginas");
            originais.add(pdf);
        }
        boolean[] usados = new boolean[arquivos.size()];
        List<ServicoEnvelopes.SignatarioNovo> pessoas = new ArrayList<>();
        for (var pessoa : pedido.signatarios()) {
            if (pessoa == null || pessoa.campos() == null) throw erro("Signatário inválido");
            List<ServicoEnvelopes.CampoNovo> campos = new ArrayList<>();
            for (var campo : pessoa.campos()) {
                if (campo == null || campo.documento() == null || campo.documento() < 0 || campo.documento() >= arquivos.size())
                    throw erro("Documento do campo inválido");
                int indice = campo.documento();
                if (campo.pagina() < 1 || campo.pagina() > quantidades.get(indice)) throw erro("Página do documento inválida");
                usados[indice] = true;
                campos.add(new ServicoEnvelopes.CampoNovo(campo.tipo(), inicios.get(indice) + campo.pagina() - 1,
                    campo.posicaoX(), campo.posicaoY(), campo.largura(), campo.altura()));
            }
            pessoas.add(new ServicoEnvelopes.SignatarioNovo(pessoa.nome(), pessoa.email(), pessoa.cpf(), pessoa.whatsapp(),
                pessoa.rg(), pessoa.qualificacao(), campos, pessoa.formaDeAutenticacao(), pessoa.formaDeNotificacao()));
        }
        for (boolean usado : usados) if (!usado) throw erro("Cada documento precisa de ao menos um campo de assinatura");
        byte[] conjunto;
        try (PDDocument destino = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (byte[] pdf : originais) {
                try (PDDocument origem = Loader.loadPDF(pdf)) { merger.appendDocument(destino, origem); }
            }
            destino.save(saida); conjunto = saida.toByteArray();
        }
        List<DocumentoAssinatura> fontes = new ArrayList<>();
        for (int i = 0; i < arquivos.size(); i++) {
            var documento = new DocumentoAssinatura(UUID.randomUUID(), null, pedido.documentos().get(i).nome().strip());
            documento.setBuffer(originais.get(i));
            documento.setPaginas(inicios.get(i), inicios.get(i) + quantidades.get(i) - 1);
            documento.setHashDoPdfOriginal(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(originais.get(i))));
            fontes.add(documento);
        }
        return envelopes.criarComDocumentos(new Pdf(conjunto, pedido.nomeAssinatura().strip()),
            new ServicoEnvelopes.PedidoCriacao(pedido.nomeEmpresa(), pedido.nomeAssinatura(), pessoas), criador, fontes);
    }
    private ResponseStatusException erro(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    public record Pedido(String nomeEmpresa, String nomeAssinatura, List<ServicoEnvelopes.SignatarioNovo> signatarios, List<DocumentoNovo> documentos) {}
    public record DocumentoNovo(String nome) {}
    private record Pdf(byte[] conteudo, String nome) implements MultipartFile {
        public String getName() { return "pdf"; }
        public String getOriginalFilename() { return (nome.length() > 250 ? nome.substring(0, 250) : nome) + ".pdf"; }
        public String getContentType() { return "application/pdf"; }
        public boolean isEmpty() { return conteudo.length == 0; }
        public long getSize() { return conteudo.length; }
        public byte[] getBytes() { return conteudo; }
        public InputStream getInputStream() { return new ByteArrayInputStream(conteudo); }
        public void transferTo(File destino) throws IOException { Files.write(destino.toPath(), conteudo); }
    }
}
