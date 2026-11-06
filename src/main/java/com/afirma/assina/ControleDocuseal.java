package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assineai/docuseal")
public class ControleDocuseal {

  private final IdentidadeAssineai identidade;
  private final IntegracaoDocuseal docuseal;
  private final ObjectMapper json;
  private final ServicoCamposPdf renderizador;

  public ControleDocuseal(
    IdentidadeAssineai identidade,
    IntegracaoDocuseal docuseal,
    ObjectMapper json,
    ServicoCamposPdf renderizador
  ) {
    this.identidade = identidade;
    this.docuseal = docuseal;
    this.json = json;
    this.renderizador = renderizador;
  }

  private byte[] ler(MultipartFile arquivo) throws Exception {
    if (arquivo.isEmpty() || arquivo.getSize() > 20_000_000) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Use um PDF de até 20 MB."
    );
    byte[] bytes = arquivo.getBytes();
    try {
      renderizador.paginas(bytes);
    } catch (Exception e) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "PDF inválido, protegido ou fora dos limites de 100 páginas e 4000 pontos por lado."
      );
    }
    return bytes;
  }

  @PostMapping("/paginas")
  public Object paginas(@RequestParam MultipartFile pdf, HttpServletRequest r) throws Exception {
    identidade.autenticar(r);
    try (var documento = Loader.loadPDF(ler(pdf))) {
      var paginas = new ArrayList<Map<String, Object>>();
      for (var pagina : documento.getPages()) {
        var caixa = pagina.getCropBox();
        boolean girada = Math.floorMod(pagina.getRotation(), 180) != 0;
        paginas.add(
          Map.of(
            "width",
            girada ? caixa.getHeight() : caixa.getWidth(),
            "height",
            girada ? caixa.getWidth() : caixa.getHeight()
          )
        );
      }
      return Map.of("paginas", paginas);
    }
  }

  @PostMapping("/previa")
  public ResponseEntity<byte[]> previa(
    @RequestParam MultipartFile pdf,
    @RequestParam int pagina,
    HttpServletRequest r
  ) throws Exception {
    identidade.autenticar(r);
    var bytes = ler(pdf);
    if (pagina < 1 || pagina > renderizador.paginas(bytes)) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Página inválida."
    );
    return ResponseEntity.ok()
      .contentType(MediaType.IMAGE_PNG)
      .header("Cache-Control", "no-store")
      .body(renderizador.previsualizar(bytes, pagina));
  }

  @PostMapping("/templates")
  public Object criar(
    @RequestParam MultipartFile pdf,
    @RequestParam String campos,
    HttpServletRequest r
  ) throws Exception {
    identidade.autenticar(r);
    var bytes = ler(pdf);
    Object lista;
    try {
      if (campos.length() > 200000) throw new IllegalArgumentException();
      lista = json.readValue(campos, List.class);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de campos inválida.");
    }
    var validados = IntegracaoDocuseal.validarCampos(lista, renderizador.paginas(bytes));
    String nome = Objects.toString(pdf.getOriginalFilename(), "Documento da FIRMA").replaceAll(
      "[\\\\/\\p{Cntrl}]",
      "_"
    );
    return docuseal.criar(bytes, nome.substring(0, Math.min(nome.length(), 120)), validados);
  }
}
