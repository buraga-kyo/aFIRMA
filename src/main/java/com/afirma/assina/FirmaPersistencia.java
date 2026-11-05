package com.afirma.assina;

import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** As rotinas gravam por aqui. As tabelas e colunas permitidas vêm de relacionamento/esquema.json. */
@Component
public class FirmaPersistencia {
  private final BancoAssineai banco;
  private final ObjectMapper json;
  private final Map<String, Map<String, Object>> tabelas = new HashMap<>();

  @SuppressWarnings("unchecked")
  public FirmaPersistencia(BancoAssineai banco, ObjectMapper json) throws Exception {
    this.banco = banco;
    this.json = json;
    var item =
        json.readValue(
            new ClassPathResource("relacionamento/esquema.json").getInputStream(), Map.class);
    for (var tabelaAtual : (List<Map<String, Object>>) item.get("tabelas"))
      tabelas.put(tabelaAtual.get("nome").toString(), tabelaAtual);
  }

  public Map<String, Object> gravar(String tabela, Long id, Map<String, Object> valores) {
    return gravar(tabela, id, valores, false);
  }

  public Map<String, Object> inserirHistorico(Map<String, Object> valores) {
    return gravar("Mensagem", null, valores, true);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> gravar(
      String tabela, Long id, Map<String, Object> valores, boolean apenasInserir) {
    var tabelaAtual = tabelas.get(tabela);
    if (tabelaAtual == null) throw new IllegalArgumentException();
    var tipos = new HashMap<String, String>();
    for (var itemAtual : (List<Map<String, Object>>) tabelaAtual.get("colunas"))
      tipos.put(itemAtual.get("nome").toString(), itemAtual.get("tipo").toString());
    var dados = new LinkedHashMap<>(valores);
    dados.put("updatedAt", OffsetDateTime.now());
    if (id == null) dados.put("createdAt", OffsetDateTime.now());
    var parametros = new HashMap<String, Object>();
    var dadosAtuais = new LinkedHashMap<String, String>();
    int indice = 0;
    for (var item : dados.entrySet()) {
      String tipo = tipos.get(item.getKey());
      if (tipo == null) throw new IllegalArgumentException("Coluna inválida: " + item.getKey());
      String chave = "p" + indice++;
      boolean js = tipo.equals("JSON") || tipo.equals("JSONB");
      parametros.put(
          chave,
          js && item.getValue() != null
              ? json.writeValueAsString(item.getValue())
              : item.getValue());
      dadosAtuais.put(
          "\"" + item.getKey() + "\"",
          js
              ? "CAST(:" + chave + " AS " + tipo + ")"
              : tipo.equals("TIMESTAMP WITH TIME ZONE")
                  ? "CAST(:" + chave + " AS timestamptz)"
                  : tipo.equals("UUID") ? "CAST(:" + chave + " AS uuid)" : ":" + chave);
    }
    String nome = "\"" + tabelaAtual.get("schema") + "\".\"" + tabela + "\"";
    String conflito =
        tabela.equals("Mensagem") && dados.get("MensagemIdWhatsApp") != null
            ? " ON CONFLICT (\"MensagemIdWhatsApp\") DO UPDATE SET "
                + String.join(
                    ",",
                    dadosAtuais.keySet().stream()
                        .filter(chave -> !chave.equals("\"createdAt\""))
                        .map(chave -> chave + "=EXCLUDED." + chave)
                        .toList())
                + " WHERE "
                + nome
                + ".\"CanalId\"=EXCLUDED.\"CanalId\""
            : "";
    if (apenasInserir) conflito = " ON CONFLICT (\"MensagemIdWhatsApp\") DO NOTHING";
    if (id == null)
      return banco.um(
          "INSERT INTO "
              + nome
              + " ("
              + String.join(",", dadosAtuais.keySet())
              + ") VALUES("
              + String.join(",", dadosAtuais.values())
              + ")"
              + conflito
              + " RETURNING *",
          parametros);
    parametros.put("id", id);
    return banco.um(
        "UPDATE "
            + nome
            + " SET "
            + String.join(
                ",",
                dadosAtuais.entrySet().stream()
                    .map(item -> item.getKey() + "=" + item.getValue())
                    .toList())
            + " WHERE id=:id RETURNING *",
        parametros);
  }
}
