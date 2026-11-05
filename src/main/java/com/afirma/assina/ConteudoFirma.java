package com.afirma.assina;

import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Lê os PDFs e os sites escolhidos para montar o texto usado pelo assistente. */
@Service
public class ConteudoFirma {
  private final ServicoFirma dados;
  private final HttpClient clienteHttp =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public ConteudoFirma(ServicoFirma dados) {
    this.dados = dados;
  }

  public String pdf(List<MultipartFile> arquivos, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "ia", "update");
    RegrasFirma.exigir(!arquivos.isEmpty() && arquivos.size() <= 10, "Envie de 1 a 10 PDFs");
    var texto = new StringBuilder();
    for (var arquivo : arquivos) {
      RegrasFirma.exigir(arquivo.getSize() <= 15 * 1024 * 1024, "PDF maior que 15 MB");
      try (var documento = Loader.loadPDF(arquivo.getBytes())) {
        RegrasFirma.exigir(documento.getNumberOfPages() <= 1000, "PDF com muitas páginas");
        texto.append(new PDFTextStripper().getText(documento)).append('\n');
      } catch (IOException erro) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF inválido");
      }
      RegrasFirma.exigir(texto.length() <= 1000000, "Texto extraído muito grande");
    }
    return texto.toString().replaceAll("(?m)^\\s*\\n", "").strip();
  }

  public String links(List<String> links, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "ia", "update");
    RegrasFirma.exigir(!links.isEmpty() && links.size() <= 10, "Informe de 1 a 10 links");
    var texto = new StringBuilder();
    for (String link : links) {
      ServicoWebhooks.verificarDestino(link);
      try {
        var resposta =
            clienteHttp.send(
                HttpRequest.newBuilder(URI.create(link))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (var corpo = resposta.body()) {
          RegrasFirma.exigir(
              resposta.statusCode() >= 200 && resposta.statusCode() < 300,
              "O site recusou a leitura");
          byte[] bytes = corpo.readNBytes(2 * 1024 * 1024 + 1);
          RegrasFirma.exigir(bytes.length <= 2 * 1024 * 1024, "Página muito grande");
          String html =
              new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                  .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "");
          var leitor = new javax.swing.text.html.parser.ParserDelegator();
          leitor.parse(
              new StringReader(html),
              new javax.swing.text.html.HTMLEditorKit.ParserCallback() {
                @Override
                public void handleText(char[] data, int posicao) {
                  texto.append(data).append(' ');
                }
              },
              true);
          texto.append('\n');
        }
      } catch (InterruptedException erro) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Leitura interrompida");
      } catch (IOException erro) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível ler o site");
      }
    }
    RegrasFirma.exigir(texto.length() <= 1000000, "Texto extraído muito grande");
    return texto.toString().strip();
  }
}
