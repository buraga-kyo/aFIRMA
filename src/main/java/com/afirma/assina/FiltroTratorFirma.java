package com.afirma.assina;

import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Recebe os filtros da tabela e monta a consulta usando só as colunas liberadas pelo cadastro. */
final class FiltroTratorFirma {
  record Consulta(String where, String ordem) {}

  static Consulta preparar(
      ObjectMapper json,
      Map<String, String> filtros,
      List<String> colunas,
      List<Map<String, Object>> campos,
      Map<String, Object> parametros) {
    try {
      String ordem = "t.id DESC", where = "";
      var opcao = json.readTree(filtros.getOrDefault("ordem", "null"));
      if (opcao != null && !opcao.isNull()) {
        String id = opcao.path("id").asText(), dir = opcao.path("dir").asText();
        if (!colunas.contains(id) || !Set.of("asc", "desc").contains(dir))
          throw new IllegalArgumentException();
        ordem = "t.\"" + id + "\" " + dir + " NULLS LAST" + (id.equals("id") ? "" : ",t.id DESC");
      }
      String bruto = filtros.getOrDefault("filtros", "{}");
      if (bruto.length() > 8000) throw new IllegalArgumentException();
      var campoAtual = json.readTree(bruto);
      if (!campoAtual.isObject() || campoAtual.size() > 20) throw new IllegalArgumentException();
      int indice = 0;
      for (var item : campoAtual.properties()) {
        String id = item.getKey();
        if (!colunas.contains(id)) throw new IllegalArgumentException();
        String campo = "t.\"" + id + "\"", parametrosAtuais = "trator" + indice++;
        var valorAtual = item.getValue();
        if (valorAtual.isArray()) {
          if (valorAtual.size() > 100) throw new IllegalArgumentException();
          var valores = new ArrayList<String>();
          valorAtual.forEach(valorDoRegistro -> valores.add(valorDoRegistro.asText()));
          if (!valores.isEmpty()) {
            parametros.put(parametrosAtuais, valores);
            where += " AND CAST(" + campo + " AS text) IN (:" + parametrosAtuais + ")";
          }
        } else if (valorAtual.isObject()) {
          String tipo =
              campos.stream()
                  .filter(itemAtual -> id.equals(itemAtual.get("nome")))
                  .map(itemAtual -> itemAtual.get("tipo").toString())
                  .findFirst()
                  .orElse("INTEGER");
          boolean data = tipo.contains("TIMESTAMP");
          boolean numero =
              tipo.contains("INTEGER")
                  || tipo.contains("DECIMAL")
                  || tipo.equals("BIGINT")
                  || tipo.contains("NUMERIC")
                  || tipo.contains("FLOAT")
                  || tipo.contains("DOUBLE");
          if (!data && !numero) throw new IllegalArgumentException();
          for (String limite : List.of("from", "to")) {
            String valor = valorAtual.path(limite).asText("");
            if (valor.isBlank()) continue;
            String chave = parametrosAtuais + limite;
            if (data) {
              var dia = java.time.LocalDate.parse(valor);
              parametros.put(
                  chave,
                  (limite.equals("to") ? dia.plusDays(1) : dia)
                      .atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo"))
                      .toOffsetDateTime());
            } else parametros.put(chave, new java.math.BigDecimal(valor));
            where +=
                " AND " + campo + (limite.equals("from") ? ">=" : data ? "<" : "<=") + ":" + chave;
          }
        } else if (valorAtual.isTextual()) {
          parametros.put(
              parametrosAtuais,
              "%"
                  + valorAtual
                      .asText()
                      .replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_")
                  + "%");
          where += " AND CAST(" + campo + " AS text) ILIKE :" + parametrosAtuais;
        } else throw new IllegalArgumentException();
      }
      return new Consulta(where, ordem);
    } catch (Exception erro) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Filtro ou ordenação do Trator inválido");
    }
  }
}
