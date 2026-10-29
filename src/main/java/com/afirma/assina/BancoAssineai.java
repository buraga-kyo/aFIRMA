package com.afirma.assina;

import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class BancoAssineai {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  public BancoAssineai(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Map<String, Object>> listar(String sql, Map<String, ?> params) {
    var linhas = jdbc.queryForList(sql, params);
    for (var linha : linhas)
      for (var entrada : linha.entrySet()) {
        try {
          if (entrada.getValue() instanceof Array a) entrada.setValue(
            Arrays.asList((Object[]) a.getArray())
          );
          else if (
            entrada.getValue() instanceof org.postgresql.util.PGobject o &&
            Set.of("json", "jsonb").contains(o.getType())
          ) entrada.setValue(json.readValue(o.getValue(), Object.class));
        } catch (Exception e) {
          throw new IllegalStateException("Dado legado inválido", e);
        }
      }
    return linhas;
  }

  public Map<String, Object> um(String sql, Map<String, ?> params) {
    var itens = listar(sql, params);
    return itens.isEmpty() ? null : itens.getFirst();
  }

  public int executar(String sql, Map<String, ?> params) {
    return jdbc.update(sql, params);
  }

  public long inserir(
    String tabela,
    String chave,
    Map<String, Object> valores
  ) {
    validarTabela(tabela);
    var dados = new LinkedHashMap<>(valores);
    dados.putIfAbsent("createdAt", java.time.OffsetDateTime.now());
    dados.putIfAbsent("updatedAt", java.time.OffsetDateTime.now());
    var params = new HashMap<String, Object>();
    var colunas = new ArrayList<String>();
    var expressoes = new ArrayList<String>();
    int i = 0;
    for (var item : dados.entrySet()) {
      String nome = "v" + i++;
      colunas.add(citar(item.getKey()));
      expressoes.add(valor(item, nome, params));
    }
    return jdbc.queryForObject(
      "INSERT INTO " +
        citar(tabela) +
        " (" +
        String.join(",", colunas) +
        ") VALUES (" +
        String.join(",", expressoes) +
        ") RETURNING " +
        citar(chave),
      params,
      Long.class
    );
  }

  public void atualizar(
    String tabela,
    String chave,
    long id,
    Map<String, Object> valores
  ) {
    validarTabela(tabela);
    var dados = new LinkedHashMap<>(valores);
    dados.put("updatedAt", java.time.OffsetDateTime.now());
    var params = new HashMap<String, Object>();
    params.put("id", id);
    var partes = new ArrayList<String>();
    int i = 0;
    for (var item : dados.entrySet()) {
      String nome = "v" + i++;
      partes.add(citar(item.getKey()) + "=" + valor(item, nome, params));
    }
    executar(
      "UPDATE " +
        citar(tabela) +
        " SET " +
        String.join(",", partes) +
        " WHERE " +
        citar(chave) +
        "=:id",
      params
    );
  }

  private String valor(
    Map.Entry<String, Object> item,
    String nome,
    Map<String, Object> params
  ) {
    Object valor = item.getValue();
    if (item.getKey().equals("DocumentoColecaoDeDivArrastavel")) {
      params.put(nome, json.writeValueAsString(valor));
      return "CAST(:" + nome + " AS JSON)";
    }
    if (valor instanceof List<?> lista) {
      params.put(
        nome,
        lista
          .stream()
          .map(
            v ->
              "\"" +
              v.toString().replace("\\", "\\\\").replace("\"", "\\\"") +
              "\""
          )
          .collect(Collectors.joining(",", "{", "}"))
      );
      return "CAST(:" + nome + " AS VARCHAR[])";
    }
    params.put(nome, valor);
    return ":" + nome;
  }

  private void validarTabela(String tabela) {
    if (
      !Set.of(
        "Assinaturas",
        "AssinaturaDocumentos",
        "AssinaturaSignatarios",
        "SignatarioHistoricos",
        "Empresas"
      ).contains(tabela)
    ) throw new IllegalArgumentException("Tabela não permitida");
  }

  private String citar(String nome) {
    if (
      !nome.matches("[A-Za-z][A-Za-z0-9]*")
    ) throw new IllegalArgumentException("Coluna inválida");
    return "\"" + nome + "\"";
  }

  public static long id(Map<String, Object> item, String chave) {
    return ((Number) item.get(chave)).longValue();
  }

  public static String texto(Map<String, Object> item, String chave) {
    Object v = item.get(chave);
    return v == null ? "" : v.toString();
  }
}
