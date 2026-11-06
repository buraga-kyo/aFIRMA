package com.afirma.assina;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validação de tipos, responsáveis e áreas dos campos do documento. */
public final class CamposEditorDocumentos {

  private CamposEditorDocumentos() {}

  public static List<Map<String, Object>> validar(Object valor, int paginas) {
    if (!(valor instanceof List<?> lista) || lista.isEmpty() || lista.size() > 200) throw erro(
      HttpStatus.BAD_REQUEST,
      "Adicione entre 1 e 200 campos ao documento."
    );
    var resultado = new ArrayList<Map<String, Object>>();
    var nomes = new HashSet<String>();
    for (Object item : lista) {
      if (!(item instanceof Map<?, ?> campo)) throw erro(HttpStatus.BAD_REQUEST, "Campo inválido.");
      String nome = texto(campo.get("name")),
        papel = texto(campo.get("role"));
      if (!nomes.add(papel + "\u0000" + nome)) throw erro(
        HttpStatus.BAD_REQUEST,
        "Use nomes distintos para os campos de cada signatário."
      );
      if (
        !(campo.get("type") instanceof String tipo) ||
        !Set.of("signature", "initials", "text", "date", "checkbox", "number", "file").contains(
          tipo
        )
      ) throw erro(HttpStatus.BAD_REQUEST, "Tipo de campo inválido.");
      if (!(campo.get("required") instanceof Boolean obrigatorio)) throw erro(
        HttpStatus.BAD_REQUEST,
        "Obrigatoriedade inválida."
      );
      double pagina = numero(campo.get("page")),
        x = numero(campo.get("x")),
        y = numero(campo.get("y")),
        w = numero(campo.get("w")),
        h = numero(campo.get("h"));
      if (
        pagina != Math.rint(pagina) ||
        pagina < 1 ||
        pagina > paginas ||
        x < 0 ||
        y < 0 ||
        w <= 0 ||
        h <= 0 ||
        x + w > 1.00000001 ||
        y + h > 1.00000001
      ) throw erro(HttpStatus.BAD_REQUEST, "O campo deve caber em uma página existente do PDF.");
      resultado.add(
        Map.of(
          "name",
          nome,
          "role",
          papel,
          "type",
          tipo,
          "required",
          obrigatorio,
          "areas",
          List.of(Map.of("page", (int) pagina, "x", x, "y", y, "w", w, "h", h))
        )
      );
    }
    return resultado;
  }

  private static String texto(Object valor) {
    if (!(valor instanceof String s) || s.isBlank() || s.length() > 80) throw erro(
      HttpStatus.BAD_REQUEST,
      "Informe nome e signatário de cada campo (até 80 caracteres)."
    );
    return s.trim();
  }

  private static double numero(Object valor) {
    if (!(valor instanceof Number n) || !Double.isFinite(n.doubleValue())) throw erro(
      HttpStatus.BAD_REQUEST,
      "Posição de campo inválida."
    );
    return n.doubleValue();
  }

  private static ResponseStatusException erro(HttpStatus status, String mensagem) {
    return new ResponseStatusException(status, mensagem);
  }
}
