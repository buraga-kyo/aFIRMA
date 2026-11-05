package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Os botões do chat chamam estas operações para abrir, assumir, transferir e encerrar o atendimento. */
@Service
public class OperacaoFirma {
  private final ServicoFirma dados;
  private final BancoAssineai banco;
  private final EspelhoFirma espelho;
  private final ChatTransporte transporte;
  private final ArquivosFirma arquivos;
  private final ServicoCanalOficial oficial;
  private final FirmaPersistencia persistencia;

  public OperacaoFirma(
      ServicoFirma dados,
      BancoAssineai banco,
      EspelhoFirma espelho,
      ChatTransporte transporte,
      ArquivosFirma arquivos,
      ServicoCanalOficial oficial,
      FirmaPersistencia persistencia) {
    this.dados = dados;
    this.banco = banco;
    this.espelho = espelho;
    this.transporte = transporte;
    this.arquivos = arquivos;
    this.oficial = oficial;
    this.persistencia = persistencia;
  }

  static long numero(Map<String, Object> dadosDoPedido, String chave) {
    if (!(dadosDoPedido.get(chave) instanceof Number valor) || valor.longValue() < 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione " + chave);
    return valor.longValue();
  }

  private Map<String, Object> bloquear(long id, ServicoFirma.Contexto contextoAtual) {
    dados.autorizar(contextoAtual, "atendimentos", "read");
    dados.registro("Atendimento", id, contextoAtual);
    return banco.um(
        "SELECT * FROM firma.\"Atendimento\" WHERE id=:id FOR UPDATE", Map.of("id", id));
  }

  public Map<String, Object> conversa(long id, ServicoFirma.Contexto contextoAtual) {
    dados.autorizar(contextoAtual, "atendimentos", "read");
    var atendimentoAtual = new LinkedHashMap<>(dados.registro("Atendimento", id, contextoAtual));
    atendimentoAtual.put(
        "contato",
        dados.registro(
            "Contato", ((Number) atendimentoAtual.get("ContatoId")).longValue(), contextoAtual));
    var canal =
        new LinkedHashMap<>(
            dados.registro(
                "Canal", ((Number) atendimentoAtual.get("CanalId")).longValue(), contextoAtual));
    canal.remove("CanalValorCredsWhatsApp");
    atendimentoAtual.put("canal", canal);
    atendimentoAtual.put("mensagens", mensagens(id, contextoAtual, 0));
    atendimentoAtual.put(
        "acoes",
        banco.listar(
            "SELECT * FROM firma.\"AtendimentoAcao\" WHERE \"AtendimentoId\"=:id ORDER BY id DESC LIMIT 100",
            Map.of("id", id)));
    atendimentoAtual.put(
        "tags",
        banco.listar(
            "SELECT tag.* FROM firma.\"AtendimentoTag\" atag JOIN firma.\"Tag\" tag ON tag.id=atag.\"TagId\" WHERE atag.\"AtendimentoId\"=:id",
            Map.of("id", id)));
    return atendimentoAtual;
  }

  public List<Map<String, Object>> mensagens(
      long id, ServicoFirma.Contexto contextoAtual, long antes) {
    dados.autorizar(contextoAtual, "atendimentos", "read");
    dados.registro("Atendimento", id, contextoAtual);
    return banco.listar(
        "SELECT id,\"MensagemTexto\",\"MensagemTipo\",\"MensagemFromMe\",\"MensagemACK\",\"MensagemDataHoraWhatsApp\",\"MensagemDeletada\",\"MensagemEditada\",\"MensagemRespostaId\",\"MensagemReacao\",\"MensagemInterativa\",\"ArquivoId\",\"createdAt\" FROM firma.\"Mensagem\" WHERE \"AtendimentoId\"=:id AND (:antes=0 OR (COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\"),id)<(SELECT COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\"),id FROM firma.\"Mensagem\" WHERE id=:antes AND \"AtendimentoId\"=:id)) ORDER BY COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\") DESC,id DESC LIMIT 100",
        Map.of("id", id, "antes", antes));
  }

  @Transactional
  public Map<String, Object> nova(Map<String, Object> pedido, ServicoFirma.Contexto contextoAtual) {
    // O cadastro e a primeira mensagem ficam na mesma transação. Se o envio falhar, a abertura é
    // desfeita.

    dados.autorizar(contextoAtual, "atendimentos", "read");
    long canalId = numero(pedido, "CanalId"), contatoId = numero(pedido, "ContatoId");
    var canal = dados.registro("Canal", canalId, contextoAtual);
    dados.registro("Contato", contatoId, contextoAtual);
    if (Boolean.FALSE.equals(canal.get("CanalStatusUso")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Canal desativado");
    Long setor = pedido.get("SetorId") instanceof Number valor ? valor.longValue() : null;
    if (setor != null) conferirSetor(setor, canalId, contextoAtual);
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of("k", "atendimento:" + canalId + ":" + contatoId));
    var parametrosAtuais =
        new HashMap<String, Object>(
            Map.of("cliente", contextoAtual.cliente(), "canal", canalId, "contato", contatoId));
    parametrosAtuais.put("setor", setor);
    var existente =
        banco.um(
            "SELECT * FROM firma.\"Atendimento\" WHERE \"ClienteId\"=:cliente AND \"CanalId\"=:canal AND \"ContatoId\"=:contato AND \"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY id DESC LIMIT 1",
            parametrosAtuais);
    if (existente != null) {
      dados.registro("Atendimento", ((Number) existente.get("id")).longValue(), contextoAtual);
      return existente;
    }
    var atendimentoAtual =
        banco.um(
            "INSERT INTO firma.\"Atendimento\"(\"ClienteId\",\"CanalId\",\"ContatoId\",\"SetorId\",\"AtendimentoStatus\",\"AtendimentoProtocolo\",\"AtendimentoReceptivo\",\"AtendimentoQtdMensagensNaoLidas\",\"createdAt\",\"updatedAt\") VALUES(:cliente,:canal,:contato,:setor,'pendente',left(replace(gen_random_uuid()::text,'-',''),16),false,0,now(),now()) RETURNING *",
            parametrosAtuais);
    long id = ((Number) atendimentoAtual.get("id")).longValue();
    registrar(id, "Iniciar", "Atendimento iniciado");
    espelho.salvar("Atendimento", atendimentoAtual, contextoAtual);
    if (pedido.get("AtendenteId") != null) {
      assumirInicio(id, atendimentoAtual.get("SetorId"), pedido, contextoAtual);
      atendimentoAtual = dados.registro("Atendimento", id, contextoAtual);
      espelho.salvar("Atendimento", atendimentoAtual, contextoAtual);
    }
    if (temMensagem(pedido)) enviar(id, pedido, contextoAtual, "OPERADOR");
    return atendimentoAtual;
  }

  private static boolean temMensagem(Map<String, Object> pedido) {
    return !Objects.toString(pedido.get("texto"), "").isBlank()
        || pedido.get("MensagemTemplateId") != null
        || pedido.get("ArquivoId") != null;
  }

  private void assumirInicio(
      long id, Object setor, Map<String, Object> pedido, ServicoFirma.Contexto contexto) {
    long atendente = numero(pedido, "AtendenteId");
    conferirAtendente(atendente, setor, contexto);
    banco.executar(
        """
        UPDATE firma."Atendimento" SET "AtendenteId"=:atendente,"AtendimentoStatus"='emandamento',
        "AtendimentoBOTExterno"=false,"updatedAt"=now() WHERE id=:id
        """,
        Map.of("id", id, "atendente", atendente));
    registrar(id, "Assumir", "Atendimento iniciado pelo operador");
  }

  private void conferirSetor(long id, long canal, ServicoFirma.Contexto contextoAtual) {
    var setorAtual = dados.registro("Setor", id, contextoAtual);
    if (Boolean.FALSE.equals(setorAtual.get("SetorStatus"))
        || setorAtual.get("CanalId") != null
            && ((Number) setorAtual.get("CanalId")).longValue() != canal)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "O setor está inativo ou pertence a outro canal");
  }

  private void conferirAtendente(long id, Object setor, ServicoFirma.Contexto contextoAtual) {
    var atendenteAtual = dados.registro("Atendente", id, contextoAtual);
    if (Boolean.FALSE.equals(atendenteAtual.get("AtendenteStatus")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendente desativado");
    if (setor != null
        && banco.um(
                "SELECT id FROM firma.\"AtendenteSetor\" WHERE \"AtendenteId\"=:a AND \"SetorId\"=:s",
                Map.of("a", id, "s", setor))
            == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Atendente não está vinculado ao setor");
  }

  private void registrar(long id, String tipo, String texto) {
    banco.executar(
        "INSERT INTO firma.\"AtendimentoAcao\"(\"AtendimentoId\",\"AtendimentoAcaoTipo\",\"AtendimentoAcaoTexto\",\"createdAt\",\"updatedAt\") VALUES(:id,:tipo,:texto,now(),now())",
        Map.of("id", id, "tipo", tipo, "texto", texto));
  }

  @Transactional
  public Map<String, Object> acao(
      long id, Map<String, Object> pedido, ServicoFirma.Contexto contextoAtual, String identidade) {
    // Os botões do chat chegam aqui. A reabertura usa o mesmo envio de mensagem do atendimento.

    var atendimentoAtual = bloquear(id, contextoAtual);
    String acao = Objects.toString(pedido.get("acao"), "");
    var parametrosAtuais = new HashMap<String, Object>(Map.of("id", id));
    boolean finalizado = "finalizado".equals(atendimentoAtual.get("AtendimentoStatus"));
    if (finalizado && !Set.of("reativar", "avaliar", "lidas").contains(acao))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O atendimento está finalizado");
    switch (acao) {
      case "assumir" -> {
        long atendente = numero(pedido, "AtendenteId");
        conferirAtendente(atendente, atendimentoAtual.get("SetorId"), contextoAtual);
        if (atendimentoAtual.get("AtendenteId") != null
            && ((Number) atendimentoAtual.get("AtendenteId")).longValue() != atendente)
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "O atendimento já foi assumido; use a transferência");
        parametrosAtuais.put("a", atendente);
        registrar(id, "Assumir", identidade + " assumiu o atendimento");
        banco.executar(
            "UPDATE firma.\"Atendimento\" SET \"AtendenteId\"=:a,\"AtendimentoStatus\"='emandamento',\"AtendimentoBOTExterno\"=false,\"updatedAt\"=now() WHERE id=:id",
            parametrosAtuais);
        cancelarBot(atendimentoAtual);
      }
      case "transferir" -> {
        long setor = numero(pedido, "SetorId");
        conferirSetor(setor, ((Number) atendimentoAtual.get("CanalId")).longValue(), contextoAtual);
        Long atendente =
            pedido.get("AtendenteId") instanceof Number valor && valor.longValue() > 0
                ? valor.longValue()
                : null;
        if (atendente != null) conferirAtendente(atendente, setor, contextoAtual);
        parametrosAtuais.put("s", setor);
        parametrosAtuais.put("a", atendente);
        parametrosAtuais.put("status", atendente == null ? "pendente" : "emandamento");
        registrar(id, "Transferir", identidade + " transferiu o atendimento");
        banco.executar(
            "UPDATE firma.\"Atendimento\" SET \"SetorId\"=:s,\"AtendenteId\"=:a,\"AtendimentoStatus\"=:status,\"AtendimentoQtdMensagensNaoLidas\"=1,\"updatedAt\"=now() WHERE id=:id",
            parametrosAtuais);
      }
      case "finalizar" -> {
        var canal =
            dados.registro(
                "Canal", ((Number) atendimentoAtual.get("CanalId")).longValue(), contextoAtual);
        if (!"API".equals(identidade))
          enfileirarAviso(
              atendimentoAtual, Objects.toString(canal.get("CanalMsgEncerramentoAtendimento"), ""));
        registrar(id, "Finalizar", identidade + " finalizou o atendimento");
        banco.executar(
            "UPDATE firma.\"Atendimento\" SET \"AtendimentoStatus\"='finalizado',\"AtendimentoDataFinalizado\"=now(),\"AtendimentoContextoIA\"=NULL,\"AtendimentoQtdMensagensNaoLidas\"=0,\"updatedAt\"=now() WHERE id=:id",
            parametrosAtuais);
        cancelarBot(atendimentoAtual);
      }
      case "reativar" -> {
        if (!finalizado)
          throw new ResponseStatusException(HttpStatus.CONFLICT, "O atendimento já está aberto");
        banco.um(
            "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
            Map.of(
                "k",
                "atendimento:"
                    + atendimentoAtual.get("CanalId")
                    + ":"
                    + atendimentoAtual.get("ContatoId")));
        if (banco.um(
                "SELECT id FROM firma.\"Atendimento\" WHERE \"CanalId\"=:canal AND \"ContatoId\"=:contato AND \"AtendimentoStatus\" IN ('pendente','emandamento')",
                Map.of(
                    "canal",
                    atendimentoAtual.get("CanalId"),
                    "contato",
                    atendimentoAtual.get("ContatoId")))
            != null)
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "O contato já possui atendimento aberto neste canal");
        registrar(id, "Iniciar", identidade + " reativou o atendimento");
        banco.executar(
            "UPDATE firma.\"Atendimento\" SET \"AtendimentoStatus\"='pendente',\"AtendimentoDataFinalizado\"=NULL,\"AtendenteId\"=NULL,\"AtendimentoQtdMensagensNaoLidas\"=1,\"updatedAt\"=now() WHERE id=:id",
            parametrosAtuais);
      }
      case "lidas" -> confirmarLeitura(atendimentoAtual, contextoAtual);
      case "avaliar" -> {
        long nota = numero(pedido, "nota");
        RegrasFirma.exigir(nota <= 5, "A avaliação deve ser de 1 a 5");
        parametrosAtuais.put("nota", nota);
        banco.executar(
            "UPDATE firma.\"Atendimento\" SET \"AtendimentoAvaliacao\"=:nota WHERE id=:id",
            parametrosAtuais);
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação inválida");
    }
    if (acao.equals("reativar")) {
      if (pedido.get("AtendenteId") != null)
        assumirInicio(id, atendimentoAtual.get("SetorId"), pedido, contextoAtual);
      if (temMensagem(pedido)) enviar(id, pedido, contextoAtual, identidade);
    }
    var resultado = dados.registro("Atendimento", id, contextoAtual);
    espelho.salvar("Atendimento", resultado, contextoAtual);
    return resultado;
  }

  private void confirmarLeitura(Map<String, Object> atendimento, ServicoFirma.Contexto contexto) {
    // A ponte ou a Meta precisam confirmar a leitura antes de zerar as mensagens não lidas no
    // banco.

    long canalId = ((Number) atendimento.get("CanalId")).longValue();
    var canal = dados.registro("Canal", canalId, contexto);
    var contato =
        dados.registro("Contato", ((Number) atendimento.get("ContatoId")).longValue(), contexto);
    var parametros = Map.<String, Object>of("id", atendimento.get("id"));
    var mensagens =
        banco.listar(
            """
            SELECT id,"MensagemIdWhatsApp" FROM firma."Mensagem"
            WHERE "AtendimentoId"=:id AND NOT COALESCE("MensagemFromMe",false)
            AND COALESCE("MensagemACK",0)<3 AND NULLIF("MensagemIdWhatsApp",'') IS NOT NULL
            ORDER BY id
            """,
            parametros);
    for (var mensagem : mensagens) {
      transporte.mensagem(
          UUID.fromString(canal.get("CanalGUID").toString()),
          Objects.toString(contato.get("ContatoNumero"), ""),
          mensagem.get("MensagemIdWhatsApp").toString(),
          false,
          "lida");
      banco.executar(
          """
          UPDATE firma."Mensagem" SET "MensagemACK"=3,"updatedAt"=now()
          WHERE id=:id AND "AtendimentoId"=:atendimento
          """,
          Map.of("id", mensagem.get("id"), "atendimento", atendimento.get("id")));
    }
    banco.executar(
        """
        UPDATE firma."Atendimento" SET "AtendimentoQtdMensagensNaoLidas"=0
        WHERE id=:id
        """,
        parametros);
  }

  private void cancelarBot(Map<String, Object> atendimentoAtual) {
    Object canal = espelho.id("Canal", atendimentoAtual.get("CanalId")),
        contato = espelho.id("Contato", atendimentoAtual.get("ContatoId"));
    if (canal != null && contato != null)
      banco.executar(
          "UPDATE chat_bot_sessao SET estado='CANCELADA',atualizada_em=now() WHERE canal_id=CAST(:canal AS uuid) AND contato_id=:contato AND estado='ATIVA'",
          Map.of("canal", canal.toString(), "contato", Long.parseLong(contato.toString())));
  }

  void enfileirarAviso(Map<String, Object> atendimentoAtual, String texto) {
    if (texto.isBlank()) return;
    var contato =
        banco.um(
            "SELECT \"ContatoNumero\" FROM firma.\"Contato\" WHERE id=:id",
            Map.of("id", atendimentoAtual.get("ContatoId")));
    banco.executar(
        "INSERT INTO firma_relacionamento_envio(cliente_id,atendimento_id,canal_id,destinatario,texto) VALUES(:c,:a,:ca,:d,:t)",
        Map.of(
            "c",
            atendimentoAtual.get("ClienteId"),
            "a",
            atendimentoAtual.get("id"),
            "ca",
            atendimentoAtual.get("CanalId"),
            "d",
            contato.get("ContatoNumero"),
            "t",
            texto));
  }

  @Transactional
  public List<Map<String, Object>> tags(
      long id, List<Long> ids, ServicoFirma.Contexto contextoAtual) {
    var atendimentoAtual = bloquear(id, contextoAtual);
    RegrasFirma.exigir(ids.size() <= 100, "Muitas tags");
    for (long tag : ids) {
      var etiqueta = dados.registro("Tag", tag, contextoAtual);
      RegrasFirma.exigir(
          !Boolean.FALSE.equals(etiqueta.get("TagStatus"))
              && (etiqueta.get("SetorId") == null
                  || Objects.equals(etiqueta.get("SetorId"), atendimentoAtual.get("SetorId"))),
          "Tag inativa ou de outro setor");
    }
    banco.executar(
        "DELETE FROM firma.\"AtendimentoTag\" WHERE \"AtendimentoId\"=:id", Map.of("id", id));
    for (long tag : new LinkedHashSet<>(ids))
      banco.executar(
          "INSERT INTO firma.\"AtendimentoTag\"(\"AtendimentoId\",\"TagId\",\"createdAt\",\"updatedAt\") VALUES(:a,:t,now(),now())",
          Map.of("a", id, "t", tag));
    return conversa(id, contextoAtual).get("tags") instanceof List<?> listaAtual
        ? (List<Map<String, Object>>) (List<?>) listaAtual
        : List.of();
  }

  public List<Map<String, Object>> historico(long id, ServicoFirma.Contexto contextoAtual) {
    dados.autorizar(contextoAtual, "atendimentos", "read");
    var atendimentoAtual = dados.registro("Atendimento", id, contextoAtual);
    return banco.listar(
        "SELECT id,\"AtendimentoProtocolo\",\"AtendimentoStatus\",\"createdAt\",\"AtendimentoDataFinalizado\" FROM firma.\"Atendimento\" WHERE \"ContatoId\"=:id AND \"ClienteId\"=:c ORDER BY id DESC LIMIT 100",
        Map.of("id", atendimentoAtual.get("ContatoId"), "c", contextoAtual.cliente()));
  }

  @Transactional
  public Map<String, Object> enviar(
      long id, String texto, ServicoFirma.Contexto contextoAtual, String identidade) {
    return enviar(id, Map.of("texto", texto), contextoAtual, identidade);
  }

  @Transactional
  public Map<String, Object> enviar(
      long id, Map<String, Object> pedido, ServicoFirma.Contexto contextoAtual, String identidade) {
    // Confere o atendimento e os anexos antes de chamar ChatTransporte ou ServicoCanalOficial.

    String texto = Objects.toString(pedido.get("texto"), "");
    RegrasFirma.exigir(
        texto.length() <= 4096 && temMensagem(pedido), "Informe texto, template ou arquivo");
    var atendimentoAtual = bloquear(id, contextoAtual);
    if ("finalizado".equals(atendimentoAtual.get("AtendimentoStatus")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O atendimento está finalizado");
    var canal =
        dados.registro(
            "Canal", ((Number) atendimentoAtual.get("CanalId")).longValue(), contextoAtual);
    if (Boolean.FALSE.equals(canal.get("CanalStatusUso")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Canal desativado");
    var contato =
        dados.registro(
            "Contato", ((Number) atendimentoAtual.get("ContatoId")).longValue(), contextoAtual);
    Long resposta =
        pedido.get("MensagemRespostaId") instanceof Number valor ? valor.longValue() : null;
    String externoResposta = null;
    if (resposta != null) {
      var mensagem = dados.registro("Mensagem", resposta, contextoAtual);
      RegrasFirma.exigir(
          Objects.equals(mensagem.get("AtendimentoId"), atendimentoAtual.get("id")),
          "A mensagem respondida pertence a outro atendimento");
      externoResposta = Objects.toString(mensagem.get("MensagemIdWhatsApp"), null);
    }
    Long arquivoId = pedido.get("ArquivoId") == null ? null : numero(pedido, "ArquivoId");
    ServicoArquivosChat.Arquivo midia =
        arquivoId == null ? null : arquivos.baixar(arquivoId, contextoAtual);
    UUID identificadorUnico = UUID.fromString(canal.get("CanalGUID").toString());
    String telefone = Objects.toString(contato.get("ContatoNumero"), "");
    String retorno;
    if (pedido.get("MensagemTemplateId") != null) {
      var modelo =
          dados.registro("MensagemTemplate", numero(pedido, "MensagemTemplateId"), contextoAtual);
      RegrasFirma.exigir(
          "Meta".equals(canal.get("CanalProvedorAPI"))
              && "APPROVED".equals(modelo.get("MensagemTemplateStatusMeta"))
              && !Boolean.FALSE.equals(modelo.get("MensagemTemplateStatusUso"))
              && (modelo.get("CanalId") == null
                  || Objects.equals(modelo.get("CanalId"), canal.get("id")))
              && (modelo.get("SetorId") == null
                  || Objects.equals(modelo.get("SetorId"), atendimentoAtual.get("SetorId"))),
          "Template não aprovado ou indisponível neste canal e setor");
      texto = Objects.toString(modelo.get("MensagemTemplateCorpo"), "");
      List<?> variaveis = pedido.get("variaveis") instanceof List<?> lista ? lista : List.of();
      var indices = new HashSet<String>();
      var trechoEncontrado = java.util.regex.Pattern.compile("\\{\\{([0-9]+)}}").matcher(texto);
      while (trechoEncontrado.find()) indices.add(trechoEncontrado.group(1));
      RegrasFirma.exigir(
          variaveis.size() == indices.size()
              && variaveis.stream()
                  .allMatch(valorAtual ->
                      valorAtual instanceof String etiqueta
                          && !etiqueta.isBlank()
                          && etiqueta.length() <= 4096),
          "Variáveis do template inválidas");
      var parametros = new ArrayList<Map<String, Object>>();
      for (int indice = 0; indice < variaveis.size(); indice++) {
        parametros.add(Map.of("type", "text", "text", variaveis.get(indice)));
        texto = texto.replace("{{" + (indice + 1) + "}}", variaveis.get(indice).toString());
      }
      retorno =
          oficial.enviarModelo(
              identificadorUnico,
              telefone,
              modelo.get("MensagemTemplateNome").toString(),
              "pt_BR",
              parametros,
              midia);
    } else if (midia != null)
      retorno =
          transporte.enviarArquivo(
              identificadorUnico, telefone, midia.nome(), midia.tipo(), midia.conteudo(), texto);
    else retorno = transporte.enviar(identificadorUnico, telefone, texto, externoResposta);
    var valores = new HashMap<String, Object>();
    valores.put("AtendimentoId", id);
    valores.put("CanalId", canal.get("id"));
    valores.put("AtendenteId", atendimentoAtual.get("AtendenteId"));
    valores.put("MensagemTexto", texto);
    valores.put("MensagemTipo", arquivoId == null ? "Texto" : "Arquivo");
    valores.put("ArquivoId", arquivoId);
    valores.put("MensagemFromMe", true);
    valores.put("MensagemEnviadaPeloAtendente", true);
    valores.put("MensagemEnviadaExternamente", false);
    valores.put("MensagemIdWhatsApp", retorno);
    valores.put("MensagemRespostaId", resposta);
    valores.put("MensagemDataHoraWhatsApp", java.time.OffsetDateTime.now());
    var mensagem = persistencia.gravar("Mensagem", null, valores);
    espelho.salvar("Mensagem", mensagem, contextoAtual);
    banco.executar(
        "UPDATE firma.\"Atendimento\" SET \"MensagemId\"=:mensagem,\"updatedAt\"=now() WHERE id=:id",
        Map.of("mensagem", mensagem.get("id"), "id", id));
    return Map.of("id", mensagem.get("id"), "enviada", true);
  }
}
