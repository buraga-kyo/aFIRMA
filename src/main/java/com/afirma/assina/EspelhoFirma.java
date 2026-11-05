package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Depois de salvar o cadastro, atualiza as tabelas que a ponte e as rotinas do chat consultam. */
@Component
public class EspelhoFirma {
  private final BancoAssineai banco;
  private final EspelhoOperacaoFirma operacao;

  public EspelhoFirma(BancoAssineai banco, EspelhoOperacaoFirma operacao) {
    this.banco = banco;
    this.operacao = operacao;
  }

  private long legadoCliente(long cliente) {
    var valor =
        banco.um(
            "SELECT origem_id FROM firma_relacionamento_vinculo WHERE destino='Cliente' AND destino_id=:id AND origem='empresa'",
            Map.of("id", cliente));
    if (valor != null) return Long.parseLong(valor.get("origem_id").toString());
    long id =
        ((Number)
                banco
                    .um("SELECT nextval(pg_get_serial_sequence('empresa','id')) AS id", Map.of())
                    .get("id"))
            .longValue();
    banco.executar(
        "INSERT INTO firma_relacionamento_vinculo VALUES('empresa',:id,'Cliente',:c,:c,'{}')",
        Map.of("id", Long.toString(id), "c", cliente));
    banco.executar(
        "INSERT INTO empresa(id,nome,ativa) SELECT :id,left(\"ClienteNome\",120),COALESCE(\"ClienteStatus\",true) FROM firma.\"Cliente\" WHERE id=:c",
        Map.of("id", id, "c", cliente));
    return id;
  }

  public Object id(String destino, Object id) {
    if (id == null) return null;
    var valor =
        banco.um(
            "SELECT origem_id FROM firma_relacionamento_vinculo WHERE destino=:t AND destino_id=:id",
            Map.of("t", destino, "id", ((Number) id).longValue()));
    return valor == null ? null : valor.get("origem_id");
  }

  @Transactional
  public void salvar(String tabela, Map<String, Object> registro, ServicoFirma.Contexto contexto) {
    String origem =
        switch (tabela) {
          case "Cliente" -> "empresa";
          case "Canal" -> "chat_canal";
          case "Contato" -> "chat_contato";
          case "Setor" -> "chat_setor";
          case "Atendente" -> "chat_atendente";
          case "Atendimento" -> "chat_atendimento";
          case "Mensagem" -> "chat_mensagem";
          default -> null;
        };
    if (origem == null) {
      operacao.salvar(tabela, registro, contexto, legadoCliente(contexto.cliente()));
      return;
    }
    banco.um("SELECT set_config('firma.relacionamento_direcao','canonico',true)", Map.of());
    long empresa = legadoCliente(contexto.cliente());
    var valores = new LinkedHashMap<String, Object>();
    valores.put("empresa_id", empresa);
    if (tabela.equals("Cliente")) {
      banco.executar(
          "UPDATE \"Empresas\" SET \"EmpresaNome\"=:nome,\"EmpresaEmail\"=:email,\"EmpresaCNPJ\"=:doc,\"updatedAt\"=now() WHERE \"EmpresaId\"=:id",
          valores(
              registro,
              Map.of("nome", "ClienteNome", "email", "ClienteEmail", "doc", "ClienteCpfCnpj"),
              Map.of("id", contexto.empresa())));
      banco.executar(
          "UPDATE empresa SET nome=left(:nome,120),email=:email,documento=:doc WHERE id=:id",
          valores(
              registro,
              Map.of("nome", "ClienteNome", "email", "ClienteEmail", "doc", "ClienteCpfCnpj"),
              Map.of("id", empresa)));
      return;
    }
    switch (tabela) {
      case "Canal" -> {
        copiar(
            valores,
            registro,
            Map.of(
                "nome", "CanalNome", "telefone", "CanalNumeroCelular", "ativo", "CanalStatusUso"));
        valores.put("nome", Objects.toString(valores.get("nome"), "Canal"));
        valores.put("telefone", Objects.toString(valores.get("telefone"), ""));
        valores.put("ativo", valores.get("ativo") == null ? true : valores.get("ativo"));
        valores.put("referencia_segredo_meta", "FIRMA:" + contexto.cliente());
        valores.put(
            "finalizar_por_inatividade",
            Boolean.TRUE.equals(registro.get("CanalFinalizaAtendimentoPorTempoInatividade")));
        valores.put("mensagem_inatividade", registro.get("CanalMsgFinalizarTempoInatividade"));
        valores.put("identificador_meta", registro.get("CanalMetaNumeroIdentificador"));
        valores.put("identificador_conta_meta", registro.get("CanalMetaWAID"));
        valores.put(
            "inatividade_minutos",
            Boolean.TRUE.equals(registro.get("CanalFinalizaAtendimentoPorTempoInatividade"))
                ? registro.get("CanalAtendimentoTempoInatividade")
                : null);
        valores.put("mensagem_encerramento", registro.get("CanalMsgEncerramentoAtendimento"));
        valores.put(
            "provedor", "Meta".equals(registro.get("CanalProvedorAPI")) ? "META" : "BAILEYS");
      }
      case "Contato" ->
          copiar(
              valores,
              registro,
              Map.of("nome", "ContatoNome", "telefone", "ContatoNumero", "email", "ContatoEmail"));
      case "Setor" -> {
        copiar(valores, registro, Map.of("nome", "SetorNome", "ativo", "SetorStatus"));
        valores.putIfAbsent("ativo", true);
        if (valores.get("ativo") == null) valores.put("ativo", true);
      }
      case "Atendente" -> {
        copiar(valores, registro, Map.of("nome", "AtendenteNome", "ativo", "AtendenteStatus"));
        if (valores.get("ativo") == null) valores.put("ativo", true);
        valores.put("identidade", "firma:" + registro.get("id"));
      }
      case "Atendimento" -> {
        valores.put("bot_externo", Boolean.TRUE.equals(registro.get("AtendimentoBOTExterno")));
        valores.put(
            "mensagens_nao_lidas",
            registro.get("AtendimentoQtdMensagensNaoLidas") == null
                ? 0
                : registro.get("AtendimentoQtdMensagensNaoLidas"));
        valores.put("avaliacao", registro.get("AtendimentoAvaliacao"));
        valores.put(
            "ultima_mensagem_cliente_em", registro.get("AtendimentoDataUltimaMensagemCliente"));
        valores.put("canal_id", UUID.fromString(id("Canal", registro.get("CanalId")).toString()));
        valores.put(
            "contato_id", Long.valueOf(id("Contato", registro.get("ContatoId")).toString()));
        Object setor = id("Setor", registro.get("SetorId")),
            atendente = id("Atendente", registro.get("AtendenteId"));
        valores.put("setor_id", setor == null ? null : Long.valueOf(setor.toString()));
        valores.put("atendente_id", atendente == null ? null : Long.valueOf(atendente.toString()));
        valores.put(
            "estado",
            switch (Objects.toString(registro.get("AtendimentoStatus"), "pendente")) {
              case "emandamento" -> "EM_ANDAMENTO";
              case "finalizado" -> "FINALIZADO";
              default -> "PENDENTE";
            });
      }
      case "Mensagem" -> {
        Object atendimento = id("Atendimento", registro.get("AtendimentoId"));
        if (atendimento == null) return;
        valores.put("atendimento_id", UUID.fromString(atendimento.toString()));
        valores.put("canal_id", UUID.fromString(id("Canal", registro.get("CanalId")).toString()));
        valores.put("texto", registro.get("MensagemTexto"));
        valores.put(
            "sentido", Boolean.TRUE.equals(registro.get("MensagemFromMe")) ? "SAIDA" : "ENTRADA");
        valores.put(
            "estado", Boolean.TRUE.equals(registro.get("MensagemFromMe")) ? "ENVIADA" : "RECEBIDA");
        valores.put("identificador_externo", registro.get("MensagemIdWhatsApp"));
      }
    }
    var vinculo =
        banco.um(
            "SELECT origem_id FROM firma_relacionamento_vinculo WHERE destino=:t AND destino_id=:id",
            Map.of("t", tabela, "id", registro.get("id")));
    Object identificador;
    if (vinculo == null) {
      identificador =
          Set.of("Canal", "Atendimento").contains(tabela)
              ? (tabela.equals("Canal") ? registro.get("CanalGUID") : UUID.randomUUID())
              : banco
                  .um(
                      "SELECT nextval(pg_get_serial_sequence('" + origem + "','id')) AS id",
                      Map.of())
                  .get("id");
      banco.executar(
          "INSERT INTO firma_relacionamento_vinculo VALUES(:origem,:oid,:destino,:did,:cliente,'{}')",
          Map.of(
              "origem",
              origem,
              "oid",
              identificador.toString(),
              "destino",
              tabela,
              "did",
              registro.get("id"),
              "cliente",
              contexto.cliente()));
    } else
      identificador =
          Set.of("Canal", "Atendimento").contains(tabela)
              ? UUID.fromString(vinculo.get("origem_id").toString())
              : Long.valueOf(vinculo.get("origem_id").toString());
    valores.put("id", identificador);
    var campos = new ArrayList<String>();
    var expressao = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    int indice = 0;
    for (var item : valores.entrySet()) {
      campos.add(item.getKey());
      String chave = "p" + indice++;
      expressao.add(":" + chave);
      parametros.put(chave, item.getValue());
    }
    banco.executar(
        "INSERT INTO "
            + origem
            + " ("
            + String.join(",", campos)
            + ") VALUES("
            + String.join(",", expressao)
            + ") ON CONFLICT(id) DO UPDATE SET "
            + String.join(
                ",",
                campos.stream()
                    .filter(valorAtual -> !valorAtual.equals("id"))
                    .map(valorAtual -> valorAtual + "=EXCLUDED." + valorAtual)
                    .toList()),
        parametros);
  }

  private Map<String, Object> valores(
      Map<String, Object> registroAtual, Map<String, String> campos, Map<String, Object> extras) {
    var dados = new HashMap<>(extras);
    copiar(dados, registroAtual, campos);
    return dados;
  }

  private void copiar(
      Map<String, Object> destino, Map<String, Object> registroAtual, Map<String, String> campos) {
    campos.forEach((itemAtual, opcao) -> destino.put(itemAtual, registroAtual.get(opcao)));
  }
}
