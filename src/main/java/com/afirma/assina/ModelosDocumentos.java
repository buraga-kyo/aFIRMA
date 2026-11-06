package com.afirma.assina;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ModelosDocumentos {

  private static final List<String> PROPRIEDADES_CAMPO = List.of(
    "name",
    "role",
    "type",
    "required",
    "page",
    "x",
    "y",
    "w",
    "h"
  );

  private final BancoAssineai banco;
  private final ObjectMapper json;
  private final EditorDocumentosPdf editor;

  public ModelosDocumentos(BancoAssineai banco, ObjectMapper json, EditorDocumentosPdf editor) {
    this.banco = banco;
    this.json = json;
    this.editor = editor;
  }

  public List<Map<String, Object>> listar(String usuario) {
    return banco.listar(
      """
      SELECT id, nome, versao, atualizado_em
      FROM firma_modelo_documento
      WHERE proprietario = :u
      ORDER BY atualizado_em DESC
      LIMIT 200
      """,
      Map.of("u", usuario)
    );
  }

  public List<Map<String, Object>> versoes(UUID id, String usuario) {
    // A consulta das revisões só ocorre depois de confirmar o proprietário do modelo.
    obter(id, usuario, null);
    return banco.listar(
      """
      SELECT versao, nome, sha256, criado_em
      FROM firma_modelo_documento_versao
      WHERE modelo_id = :id
      ORDER BY versao DESC
      """,
      Map.of("id", id)
    );
  }

  public Map<String, Object> obter(UUID id, String usuario, Integer versao) {
    var parametros = new HashMap<String, Object>(Map.of("id", id, "u", usuario));
    if (versao != null) parametros.put("v", versao);
    String versaoSelecionada = versao == null ? "m.versao" : ":v";
    String consulta = """
    SELECT m.id, v.nome, v.versao, v.campos, v.sha256, v.criado_em
    FROM firma_modelo_documento m
    JOIN firma_modelo_documento_versao v
      ON v.modelo_id = m.id AND v.versao = %s
    WHERE m.id = :id AND m.proprietario = :u
    """.formatted(versaoSelecionada);
    var modelo = banco.um(consulta, parametros);
    if (modelo == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modelo não encontrado.");
    }
    return modelo;
  }

  public byte[] pdf(UUID id, String usuario, Integer versao) {
    var modelo = obter(id, usuario, versao);
    var revisao = banco.um(
      """
      SELECT pdf
      FROM firma_modelo_documento_versao
      WHERE modelo_id = :id AND versao = :v
      """,
      Map.of("id", id, "v", modelo.get("versao"))
    );
    return (byte[]) revisao.get("pdf");
  }

  @Transactional(rollbackFor = Exception.class)
  public Object salvar(
    UUID id,
    Integer versao,
    String nome,
    byte[] pdf,
    Object valor,
    String usuario
  ) throws Exception {
    if (nome == null || nome.isBlank() || nome.length() > 120) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Informe nome de até 120 caracteres."
      );
    }
    editor.validar(pdf);
    var paginas = editor.paginas(pdf);
    var campos = prepararCampos(valor, paginas.size());

    int novaVersao = 1;
    if (id == null) {
      id = criarModelo(nome, usuario);
    } else {
      novaVersao = avancarVersao(id, versao, nome, usuario);
    }
    gravarRevisao(id, novaVersao, nome, pdf, campos);
    return obter(id, usuario, null);
  }

  private List<Map<String, Object>> prepararCampos(Object valor, int paginas) {
    if (!(valor instanceof List<?> lista) || lista.size() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de campos inválida.");
    }
    // Um modelo pode começar sem campos. Campos presentes seguem o contrato do editor.
    if (!lista.isEmpty()) CamposEditorDocumentos.validar(lista, paginas);
    var campos = new ArrayList<Map<String, Object>>();
    for (var objeto : lista) {
      var campo = (Map<?, ?>) objeto;
      var propriedades = new HashMap<String, Object>();
      for (var chave : PROPRIEDADES_CAMPO) {
        propriedades.put(chave, campo.get(chave));
      }
      propriedades.put("id", UUID.randomUUID().toString());
      campos.add(propriedades);
    }
    return campos;
  }

  private UUID criarModelo(String nome, String usuario) {
    UUID id = UUID.randomUUID();
    banco.executar(
      """
      INSERT INTO firma_modelo_documento(id, proprietario, nome, versao)
      VALUES (:id, :u, :nome, 1)
      """,
      Map.of("id", id, "u", usuario, "nome", nome.strip())
    );
    return id;
  }

  private int avancarVersao(UUID id, Integer versao, String nome, String usuario) {
    if (versao == null || versao < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a versão em edição.");
    }
    obter(id, usuario, null);
    // A comparação e o incremento são feitos pelo mesmo UPDATE. Duas sessões
    // partindo da mesma versão não podem sobrescrever o trabalho uma da outra.
    int alterados = banco.executar(
      """
      UPDATE firma_modelo_documento
      SET versao = versao + 1, nome = :nome, atualizado_em = now()
      WHERE id = :id AND proprietario = :u AND versao = :v
      """,
      Map.of("id", id, "u", usuario, "nome", nome.strip(), "v", versao)
    );
    if (alterados != 1) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "O modelo foi alterado em outra sessão. Abra a versão atual antes de salvar."
      );
    }
    return versao + 1;
  }

  private void gravarRevisao(
    UUID id,
    int versao,
    String nome,
    byte[] pdf,
    List<Map<String, Object>> campos
  ) throws Exception {
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
    // Esta inserção e o avanço da versão pertencem à transação de salvar().
    // Se a gravação do PDF falhar, o cabeçalho também volta à versão anterior.
    banco.executar(
      """
      INSERT INTO firma_modelo_documento_versao(modelo_id, versao, nome, pdf, campos, sha256)
      VALUES (:id, :v, :nome, :pdf, CAST(:campos AS jsonb), :hash)
      """,
      Map.of(
        "id",
        id,
        "v",
        versao,
        "nome",
        nome.strip(),
        "pdf",
        pdf,
        "campos",
        json.writeValueAsString(campos),
        "hash",
        hash
      )
    );
  }
}
