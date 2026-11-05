package com.afirma.assina;

import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** As campanhas e integrações usam esta fila para agendar os envios e guardar a confirmação. */
@Service
public class DisparosFirma {
  private static final Logger registroLog = LoggerFactory.getLogger(DisparosFirma.class);
  private final BancoAssineai banco;
  private final ServicoFirma dados;
  private final FirmaPersistencia persistencia;
  private final TransactionTemplate transacoes;
  private final ChatTransporte transporte;
  private final ServicoCanalOficial meta;
  private final EspelhoFirma espelho;
  private final OperacaoFirma operacao;
  private final ArquivosFirma arquivos;
  private final ObjectMapper json;
  private final boolean rotinas;

  public DisparosFirma(
      BancoAssineai banco,
      ServicoFirma dados,
      FirmaPersistencia persistencia,
      TransactionTemplate transacoes,
      ChatTransporte transporte,
      ServicoCanalOficial meta,
      EspelhoFirma espelho,
      OperacaoFirma operacao,
      ArquivosFirma arquivos,
      ObjectMapper json,
      @Value("${APP_FIRMA_ROTINAS:true}") boolean rotinas) {
    this.banco = banco;
    this.dados = dados;
    this.persistencia = persistencia;
    this.transacoes = transacoes;
    this.transporte = transporte;
    this.meta = meta;
    this.espelho = espelho;
    this.operacao = operacao;
    this.arquivos = arquivos;
    this.json = json;
    this.rotinas = rotinas;
  }

  public static String telefone(Object valor) {
    String textoAtual = Objects.toString(valor, "").replaceAll("[^0-9]", "");
    if (textoAtual.length() == 10 || textoAtual.length() == 11) textoAtual = "55" + textoAtual;
    return textoAtual;
  }

  public static String personalizar(
      String texto, Map<?, ?> destinatario, List<?> variaveis, boolean oficial) {
    String registroAtual = Objects.toString(texto, "");
    if (oficial) {
      for (var valor : variaveis)
        if (valor instanceof Map<?, ?> registroDoRegistro) {
          Object coluna = registroDoRegistro.get("valor");
          registroAtual =
              registroAtual.replace(
                  "{{" + registroDoRegistro.get("id") + "}}",
                  coluna != null && coluna.equals("Saudação")
                      ? "{{Saudação}}"
                      : Objects.toString(destinatario.get(coluna), ""));
        }
    } else
      for (var itemAtual : destinatario.entrySet())
        registroAtual =
            registroAtual.replace(
                "{{" + itemAtual.getKey() + "}}", Objects.toString(itemAtual.getValue(), ""));
    return registroAtual;
  }

  static boolean horario(Map<String, Object> dadosAtuais, ZonedDateTime agora) {
    String dias = Objects.toString(dadosAtuais.get("DisparoMassaDiasEnvio"), "0,1,2,3,4,5,6");
    int dia = agora.getDayOfWeek().getValue() % 7;
    if (!Arrays.asList(dias.replaceAll("[\\[\\]\\s]", "").split(","))
        .contains(Integer.toString(dia))) return false;
    LocalTime
        inicio =
            LocalTime.parse(Objects.toString(dadosAtuais.get("DisparoMassaHoraReinicio"), "00:00")),
        fim =
            LocalTime.parse(
                Objects.toString(dadosAtuais.get("DisparoMassaUltimoHorario"), "23:59")),
        atual = agora.toLocalTime().withSecond(0).withNano(0);
    return !atual.isBefore(inicio) && !atual.isAfter(fim);
  }

  static ZonedDateTime proximaJanela(
      Map<String, Object> dadosAtuais, ZonedDateTime agora, boolean limiteDiario) {
    for (int dias = limiteDiario ? 1 : 0; dias <= 8; dias++) {
      var tabela =
          agora
              .plusDays(dias)
              .with(
                  LocalTime.parse(
                      Objects.toString(dadosAtuais.get("DisparoMassaHoraReinicio"), "00:00")))
              .withSecond(0)
              .withNano(0);
      if (tabela.isAfter(agora) && horario(dadosAtuais, tabela)) return tabela;
    }
    throw new IllegalArgumentException("Não há dia de envio configurado");
  }

  private void adiar(
      long id, Map<String, Object> dadosAtuais, ZonedDateTime agora, boolean limite) {
    var proxima = proximaJanela(dadosAtuais, agora, limite);
    banco.executar(
        "UPDATE firma_relacionamento_disparo SET proximo_envio=:p WHERE disparo_id=:id",
        Map.of("p", proxima.toOffsetDateTime(), "id", id));
    persistencia.gravar(
        "DisparoMassa",
        id,
        Map.of(
            "DisparoMassaDiaReinicio",
            proxima.toOffsetDateTime(),
            "DisparoMassaStatus",
            "Aguardando envio"));
  }

  @Transactional
  public Map<String, Object> criar(
      Map<String, Object> pedido, ServicoFirma.Contexto contextoAtual) {
    dados.autorizar(contextoAtual, "disparos", "insert");
    var dadosDoRegistro = new LinkedHashMap<String, Object>();
    Set<String> permitidos =
        Set.of(
            "CanalId",
            "ListaContatoId",
            "MensagemTemplateId",
            "SetorId",
            "ArquivoId",
            "DisparoMassaDataHoraInicio",
            "DisparoMassaMensagensPorDisparos",
            "DisparoMassaIntervaloEntreDisparos",
            "DisparoMassaMaxMensagemDia",
            "DisparoMassaUltimoHorario",
            "DisparoMassaHoraReinicio",
            "DisparoMassaDiasEnvio",
            "DisparoMassaConteudo",
            "DisparoMassaColunaDestinatario",
            "DisparoMassaTemplateVariaveis",
            "DisparoMassaDesejaGerarAtendimento");
    RegrasFirma.exigir(
        permitidos.containsAll(pedido.keySet()),
        "O pedido contém campos de controle não editáveis");
    dadosDoRegistro.putAll(pedido);
    var canal = dados.registro("Canal", OperacaoFirma.numero(pedido, "CanalId"), contextoAtual);
    var lista =
        dados.registro(
            "ListaContato", OperacaoFirma.numero(pedido, "ListaContatoId"), contextoAtual);
    RegrasFirma.exigir(
        !Boolean.FALSE.equals(canal.get("CanalStatusUso"))
            && !Boolean.FALSE.equals(lista.get("ListaContatoStatus")),
        "Canal ou lista desativado");
    if (pedido.get("SetorId") instanceof Number valorAtual) {
      var setor = dados.registro("Setor", valorAtual.longValue(), contextoAtual);
      RegrasFirma.exigir(
          Objects.equals(setor.get("CanalId"), canal.get("id")), "Setor de outro canal");
    }
    Map<String, Object> modelo = null;
    if (pedido.get("MensagemTemplateId") instanceof Number valorAtual) {
      modelo = dados.registro("MensagemTemplate", valorAtual.longValue(), contextoAtual);
      RegrasFirma.exigir(
          (modelo.get("CanalId") == null || Objects.equals(modelo.get("CanalId"), canal.get("id")))
              && !Boolean.FALSE.equals(modelo.get("MensagemTemplateStatusUso")),
          "Template indisponível neste canal");
    }
    boolean oficial = "Meta".equals(canal.get("CanalProvedorAPI"));
    if (oficial)
      RegrasFirma.exigir(
          modelo != null && "APPROVED".equals(modelo.get("MensagemTemplateStatusMeta")),
          "Selecione um template aprovado pela Meta");
    String corpo =
        modelo == null
            ? Objects.toString(dadosDoRegistro.get("DisparoMassaConteudo"), "")
            : Objects.toString(modelo.get("MensagemTemplateCorpo"), "");
    RegrasFirma.exigir(!corpo.isBlank() && corpo.length() <= 4096, "Informe o conteúdo do disparo");
    String coluna =
        Objects.toString(dadosDoRegistro.get("DisparoMassaColunaDestinatario"), "telefone");
    RegrasFirma.exigir(!coluna.isBlank(), "Escolha a coluna do telefone");
    int lote = inteiro(dadosDoRegistro, "DisparoMassaMensagensPorDisparos", 1, 1, 1000),
        intervalo = inteiro(dadosDoRegistro, "DisparoMassaIntervaloEntreDisparos", 5, 1, 86400);
    if (dadosDoRegistro.get("DisparoMassaMaxMensagemDia") != null)
      inteiro(dadosDoRegistro, "DisparoMassaMaxMensagemDia", 1, 1, 1000000);
    dadosDoRegistro.put("DisparoMassaMensagensPorDisparos", lote);
    dadosDoRegistro.put("DisparoMassaIntervaloEntreDisparos", intervalo);
    dadosDoRegistro.putIfAbsent("DisparoMassaDiasEnvio", "0,1,2,3,4,5,6");
    dadosDoRegistro.putIfAbsent("DisparoMassaHoraReinicio", "00:00");
    dadosDoRegistro.putIfAbsent("DisparoMassaUltimoHorario", "23:59");
    String dias =
        Objects.toString(dadosDoRegistro.get("DisparoMassaDiasEnvio"), "")
            .replaceAll("[\\[\\]\\s]", "");
    RegrasFirma.exigir(
        dias.matches("[0-6](,[0-6])*"),
        "Dias da semana inválidos; use 0 a 6 separados por vírgula");
    dadosDoRegistro.put("DisparoMassaDiasEnvio", dias);
    try {
      RegrasFirma.exigir(
          !LocalTime.parse(dadosDoRegistro.get("DisparoMassaHoraReinicio").toString())
              .isAfter(
                  LocalTime.parse(dadosDoRegistro.get("DisparoMassaUltimoHorario").toString())),
          "Horário inicial posterior ao final");
    } catch (java.time.format.DateTimeParseException erroAtual) {
      RegrasFirma.exigir(false, "Use horários HH:mm");
    }
    List<?> vars =
        dadosDoRegistro.get("DisparoMassaTemplateVariaveis") instanceof List<?> listaAtual
            ? listaAtual
            : List.of();
    dadosDoRegistro.put("DisparoMassaTemplateVariaveis", vars);
    RegrasFirma.exigir(lista.get("ListaContatoContatos") instanceof List<?>, "A lista está vazia");
    var destinatarios = new LinkedHashMap<String, Map<?, ?>>();
    for (Object item : (List<?>) lista.get("ListaContatoContatos"))
      if (item instanceof Map<?, ?> registroDoRegistro) {
        String textoAtual = telefone(registroDoRegistro.get(coluna));
        if (textoAtual.matches("[0-9]{12,15}"))
          destinatarios.putIfAbsent(textoAtual, registroDoRegistro);
      }
    RegrasFirma.exigir(
        !destinatarios.isEmpty(), "A lista não contém telefones válidos nesta coluna");
    Long arquivo =
        dadosDoRegistro.remove("ArquivoId") instanceof Number valorAtual
            ? valorAtual.longValue()
            : null;
    if (arquivo != null) {
      var arquivoAtual = dados.registro("Arquivo", arquivo, contextoAtual);
      dadosDoRegistro.put("DisparoMassaAnexo", arquivoAtual.get("ArquivoURL"));
      dadosDoRegistro.put("DisparoMassaAnexoChave", arquivoAtual.get("ArquivoChave"));
      dadosDoRegistro.put("DisparoMassaAnexoNome", arquivoAtual.get("ArquivoNome"));
    }
    dadosDoRegistro.putIfAbsent("DisparoMassaDataHoraInicio", OffsetDateTime.now());
    if (dadosDoRegistro.get("DisparoMassaDataHoraInicio") instanceof String data) {
      try {
        dadosDoRegistro.put("DisparoMassaDataHoraInicio", OffsetDateTime.parse(data));
      } catch (java.time.format.DateTimeParseException erro) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de início inválida");
      }
    }
    dadosDoRegistro.put("DisparoMassaStatus", "Aguardando envio");
    dadosDoRegistro.put("DisparoMassaColunaDestinatario", coluna);
    dadosDoRegistro.put("DisparoMassaQuantidadeMensagens", destinatarios.size());
    dadosDoRegistro.put("DisparoMassaQuantidadePendente", destinatarios.size());
    dadosDoRegistro.put("DisparoMassaQuantidadeEnviado", 0);
    dadosDoRegistro.put("DisparoMassaQuantidadeFalha", 0);
    dadosDoRegistro.put("DisparoMassaQuantidadeCancelado", 0);
    dadosDoRegistro.put(
        "DisparoMassaQtdMensagensEnviadasDiaAtual", Map.of("dia", OffsetDateTime.now(), "qtd", 0));
    dadosDoRegistro.put("DisparoMassaProcessandoMensagens", false);
    dadosDoRegistro.put("ClienteId", contextoAtual.cliente());
    var mensagens = new ArrayList<Map<String, Object>>();
    for (var itemAtual : destinatarios.entrySet())
      mensagens.add(
          Map.of(
              "Destinatario",
              itemAtual.getKey(),
              "Mensagem",
              personalizar(corpo, itemAtual.getValue(), vars, oficial),
              "Variaveis",
              itemAtual.getValue(),
              "Status",
              "Pendente"));
    dadosDoRegistro.put("DisparoMassaMensagens", mensagens);
    var dadosAtuais = persistencia.gravar("DisparoMassa", null, dadosDoRegistro);
    long id = ((Number) dadosAtuais.get("id")).longValue();
    banco.executar(
        "INSERT INTO firma_relacionamento_disparo(disparo_id) VALUES(:id)", Map.of("id", id));
    int indice = 0;
    for (var registroDoRegistro : mensagens) {
      var parametrosAtuais =
          new HashMap<String, Object>(
              Map.of(
                  "c",
                  contextoAtual.cliente(),
                  "d",
                  id,
                  "ca",
                  canal.get("id"),
                  "n",
                  registroDoRegistro.get("Destinatario"),
                  "t",
                  registroDoRegistro.get("Mensagem"),
                  "i",
                  indice++));
      parametrosAtuais.put("modelo", modelo == null ? null : modelo.get("id"));
      parametrosAtuais.put("arquivo", arquivo);
      parametrosAtuais.put(
          "vars",
          json.writeValueAsString(
              parametros(vars, (Map<?, ?>) registroDoRegistro.get("Variaveis"))));
      banco.executar(
          "INSERT INTO firma_relacionamento_envio(cliente_id,disparo_id,canal_id,destinatario,texto,modelo_id,arquivo_id,variaveis,indice) VALUES(:c,:d,:ca,:n,:t,:modelo,:arquivo,CAST(:vars AS jsonb),:i)",
          parametrosAtuais);
    }
    return dadosAtuais;
  }

  private static int inteiro(
      Map<String, Object> valor, String campo, int padrao, int menorValor, int maiorValor) {
    if (valor.get(campo) == null) return padrao;
    RegrasFirma.exigir(valor.get(campo) instanceof Number, "Número inválido: " + campo);
    double valorAtual = ((Number) valor.get(campo)).doubleValue();
    RegrasFirma.exigir(
        valorAtual >= menorValor
            && valorAtual <= maiorValor
            && valorAtual == Math.floor(valorAtual),
        "Valor fora do intervalo: " + campo);
    return (int) valorAtual;
  }

  private List<Map<String, Object>> parametros(List<?> variaveis, Map<?, ?> destinatario) {
    var registroAtual = new ArrayList<Map<String, Object>>();
    for (Object valor : variaveis)
      if (valor instanceof Map<?, ?> registroDoRegistro)
        registroAtual.add(
            Map.of(
                "type",
                "text",
                "text",
                registroDoRegistro.get("valor") != null
                        && registroDoRegistro.get("valor").equals("Saudação")
                    ? "{{Saudação}}"
                    : Objects.toString(destinatario.get(registroDoRegistro.get("valor")), "")));
    return registroAtual;
  }

  @Transactional
  public Map<String, Object> acao(long id, String acao, ServicoFirma.Contexto contextoAtual) {
    dados.autorizar(contextoAtual, "disparos", "update");
    var dadosAtuais = dados.registro("DisparoMassa", id, contextoAtual);
    banco.um("SELECT id FROM firma.\"DisparoMassa\" WHERE id=:id FOR UPDATE", Map.of("id", id));
    if (acao.equals("cancelar")) {
      banco.executar(
          "UPDATE firma_relacionamento_envio SET estado='CANCELADO',atualizada_em=now() WHERE disparo_id=:id AND estado='PENDENTE'",
          Map.of("id", id));
      persistencia.gravar("DisparoMassa", id, Map.of("DisparoMassaStatus", "Cancelado"));
    } else if (acao.equals("repetir-falhas")) {
      RegrasFirma.exigir(
          !"Cancelado".equals(dadosAtuais.get("DisparoMassaStatus")), "Disparo cancelado");
      banco.executar(
          "UPDATE firma_relacionamento_envio SET estado='PENDENTE',erro=NULL,atualizada_em=now() WHERE disparo_id=:id AND estado='FALHOU'",
          Map.of("id", id));
      persistencia.gravar("DisparoMassa", id, Map.of("DisparoMassaStatus", "Aguardando envio"));
    } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação inválida");
    if (banco.um(
            "SELECT disparo_id FROM firma_relacionamento_disparo WHERE disparo_id=:id",
            Map.of("id", id))
        == null) {
      Object legado = espelho.id("DisparoMassa", id);
      RegrasFirma.exigir(legado != null, "Este disparo não tem fila operacional");
      if (acao.equals("cancelar")) {
        banco.executar(
            "UPDATE chat_campanha SET estado='CANCELADA' WHERE id=CAST(:id AS uuid)",
            Map.of("id", legado.toString()));
        banco.executar(
            "UPDATE chat_campanha_envio SET estado='CANCELADO' WHERE campanha_id=CAST(:id AS uuid) AND estado='PENDENTE'",
            Map.of("id", legado.toString()));
      } else {
        int quantidade =
            banco.executar(
                "UPDATE chat_campanha_envio SET estado='PENDENTE',tentativas=0,proxima_tentativa_em=now(),atualizada_em=now() WHERE campanha_id=CAST(:id AS uuid) AND estado='FALHOU'",
                Map.of("id", legado.toString()));
        RegrasFirma.exigir(quantidade > 0, "Não há falhas para repetir");
        banco.executar(
            "UPDATE chat_campanha SET estado='AGENDADA',proximo_envio_em=NULL WHERE id=CAST(:id AS uuid)",
            Map.of("id", legado.toString()));
      }
    } else atualizar(id);
    return dados.registro("DisparoMassa", id, contextoAtual);
  }

  public Map<String, Object> avulso(
      Map<String, Object> parametrosAtuais, ServicoFirma.Contexto contextoAtual, String chave) {
    // A chave da requisição liga a tentativa ao envio salvo. Repetir a chamada consulta o mesmo
    // envio.

    RegrasFirma.exigir(
        chave != null && chave.length() <= 128 && !chave.isBlank(),
        "Informe Idempotency-Key com até 128 caracteres");
    var envio =
        transacoes.execute(transacao -> {
          banco.um(
              "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
              Map.of("k", "api:" + contextoAtual.cliente() + ":" + chave));
          var antigo =
              banco.um(
                  "SELECT * FROM firma_relacionamento_envio WHERE cliente_id=:c AND requisicao=:k",
                  Map.of("c", contextoAtual.cliente(), "k", chave));
          if (antigo != null) return antigo;
          long canalId = OperacaoFirma.numero(parametrosAtuais, "CanalId");
          var canal = dados.registro("Canal", canalId, contextoAtual);
          RegrasFirma.exigir(
              !Boolean.FALSE.equals(canal.get("CanalStatusUso")), "Canal desativado");
          String destino = telefone(parametrosAtuais.get("Destinatario"));
          RegrasFirma.exigir(destino.matches("[0-9]{12,15}"), "Destinatário inválido");
          Long atendimento =
              parametrosAtuais.get("AtendimentoId") instanceof Number valorAtual
                  ? valorAtual.longValue()
                  : null;
          if (atendimento != null) {
            var atendimentoAtual = dados.registro("Atendimento", atendimento, contextoAtual);
            var contato =
                dados.registro(
                    "Contato",
                    ((Number) atendimentoAtual.get("ContatoId")).longValue(),
                    contextoAtual);
            RegrasFirma.exigir(
                Objects.equals(atendimentoAtual.get("CanalId"), canal.get("id"))
                    && Objects.equals(contato.get("ContatoNumero"), destino)
                    && !"finalizado".equals(atendimentoAtual.get("AtendimentoStatus")),
                "Atendimento não corresponde ao canal e destinatário ou está finalizado");
          }
          Long
              modelo =
                  parametrosAtuais.get("MensagemTemplateId") instanceof Number valorAtual
                      ? valorAtual.longValue()
                      : null,
              arquivo =
                  parametrosAtuais.get("ArquivoId") instanceof Number valorAtual
                      ? valorAtual.longValue()
                      : null;
          String texto = Objects.toString(parametrosAtuais.get("texto"), "");
          List<?> parametrosDoRegistro = List.of();
          if (modelo != null) {
            var modeloAtual = dados.registro("MensagemTemplate", modelo, contextoAtual);
            RegrasFirma.exigir(
                "Meta".equals(canal.get("CanalProvedorAPI"))
                    && "APPROVED".equals(modeloAtual.get("MensagemTemplateStatusMeta"))
                    && (modeloAtual.get("CanalId") == null
                        || Objects.equals(modeloAtual.get("CanalId"), canal.get("id"))),
                "Template não aprovado para este canal");
            texto = Objects.toString(modeloAtual.get("MensagemTemplateCorpo"), "");
            List<?> vars =
                parametrosAtuais.get("variaveis") instanceof List<?> listaAtual
                    ? listaAtual
                    : List.of();
            var trechoEncontrado =
                java.util.regex.Pattern.compile("\\{\\{([0-9]+)}}").matcher(texto);
            var nomes = new HashSet<String>();
            while (trechoEncontrado.find()) nomes.add(trechoEncontrado.group(1));
            RegrasFirma.exigir(
                vars.size() == nomes.size()
                    && vars.stream()
                        .allMatch(valor -> valor instanceof String tabela && !tabela.isBlank()),
                "Variáveis do template inválidas");
            var parametros = new ArrayList<Map<String, Object>>();
            for (int indice = 0; indice < vars.size(); indice++) {
              parametros.add(Map.of("type", "text", "text", vars.get(indice)));
              texto = texto.replace("{{" + (indice + 1) + "}}", vars.get(indice).toString());
            }
            parametrosDoRegistro = parametros;
          }
          Long resposta =
              parametrosAtuais.get("MensagemRespostaId") instanceof Number rn
                  ? rn.longValue()
                  : null;
          if (resposta != null) {
            var mensagem = dados.registro("Mensagem", resposta, contextoAtual);
            RegrasFirma.exigir(
                Objects.equals(mensagem.get("CanalId"), canal.get("id")),
                "A mensagem respondida pertence a outro canal");
          }
          if (parametrosAtuais.get("interativa") != null)
            RegrasFirma.exigir(
                "Meta".equals(canal.get("CanalProvedorAPI")),
                "Este provedor não suporta mensagens interativas");
          if (arquivo != null) dados.registro("Arquivo", arquivo, contextoAtual);
          RegrasFirma.exigir(
              arquivo != null || modelo != null || !texto.isBlank() && texto.length() <= 4096,
              "Mensagem inválida");
          var valores =
              new HashMap<String, Object>(
                  Map.of(
                      "c",
                      contextoAtual.cliente(),
                      "ca",
                      canalId,
                      "n",
                      destino,
                      "t",
                      texto,
                      "k",
                      chave));
          valores.put("origem", parametrosAtuais.getOrDefault("origem", "API"));
          valores.put(
              "interativa",
              parametrosAtuais.get("interativa") == null
                  ? null
                  : json.writeValueAsString(parametrosAtuais.get("interativa")));
          valores.put("resposta", resposta);
          valores.put("encerrar", Boolean.TRUE.equals(parametrosAtuais.get("encerrar")));
          valores.put("a", atendimento);
          valores.put("m", modelo);
          valores.put("ar", arquivo);
          valores.put("v", json.writeValueAsString(parametrosDoRegistro));
          return banco.um(
              "INSERT INTO firma_relacionamento_envio(cliente_id,canal_id,destinatario,texto,atendimento_id,modelo_id,arquivo_id,variaveis,requisicao,origem,interativa,resposta_id,encerrar) VALUES(:c,:ca,:n,:t,:a,:m,:ar,CAST(:v AS jsonb),:k,:origem,CAST(:interativa AS jsonb),:resposta,:encerrar) RETURNING *",
              valores);
        });
    if ("PENDENTE".equals(envio.get("estado"))) {
      var reservado =
          transacoes.execute(transacao ->
              banco.um(
                  "UPDATE firma_relacionamento_envio SET estado='ENVIANDO',atualizada_em=now() WHERE id=:id AND estado='PENDENTE' RETURNING *",
                  Map.of("id", envio.get("id"))));
      if (reservado != null) enviar(reservado);
    }
    return consultarEnvio(((Number) envio.get("id")).longValue(), contextoAtual);
  }

  public Map<String, Object> consultarEnvio(long id, ServicoFirma.Contexto contextoAtual) {
    var itemAtual =
        banco.um(
            "SELECT id,estado,externo,erro,atualizada_em FROM firma_relacionamento_envio WHERE id=:id AND cliente_id=:c",
            Map.of("id", id, "c", contextoAtual.cliente()));
    if (itemAtual == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return itemAtual;
  }

  @Scheduled(fixedDelay = 1000)
  public void executar() {
    if (!rotinas) return;
    try {
      banco.executar(
          "UPDATE firma_relacionamento_envio SET estado='INCERTO',erro='Envio interrompido sem confirmação; confira o provedor antes de reenviar',atualizada_em=now() WHERE estado='ENVIANDO' AND atualizada_em<now()-interval '5 minutes'",
          Map.of());
      for (int indice = 0; indice < 20; indice++) {
        var envio = transacoes.execute(transacao -> reservar());
        if (envio == null) break;
        enviar(envio);
      }
    } catch (RuntimeException erroAtual) {
      registroLog.warn("Fila Firma: {}", erroAtual.getClass().getSimpleName());
    }
  }

  private Map<String, Object> reservar() {
    var fila =
        banco.listar(
            "SELECT e.*,d.\"DisparoMassaDesejaGerarAtendimento\",d.\"SetorId\" FROM firma_relacionamento_envio e LEFT JOIN firma.\"DisparoMassa\" d ON d.id=e.disparo_id LEFT JOIN firma_relacionamento_disparo agenda ON agenda.disparo_id=e.disparo_id WHERE e.estado='PENDENTE' AND (e.disparo_id IS NULL OR (d.\"DisparoMassaStatus\" IN ('Aguardando envio','Enviando') AND d.\"DisparoMassaDataHoraInicio\"<=now() AND agenda.proximo_envio<=now())) ORDER BY e.id LIMIT 30 FOR UPDATE OF e SKIP LOCKED",
            Map.of());
    for (var itemAtual : fila) {
      if (itemAtual.get("disparo_id") instanceof Number valorAtual) {
        long id = valorAtual.longValue();
        var dadosAtuais =
            banco.um(
                "SELECT d.*,agenda.dia,agenda.enviados_dia,agenda.lote FROM firma.\"DisparoMassa\" d JOIN firma_relacionamento_disparo agenda ON agenda.disparo_id=d.id WHERE d.id=:id FOR UPDATE OF d,agenda SKIP LOCKED",
                Map.of("id", id));
        if (dadosAtuais == null) continue;
        ZonedDateTime agora = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        if (!horario(dadosAtuais, agora)) {
          adiar(id, dadosAtuais, agora, false);
          continue;
        }
        boolean mesmoDia =
            Objects.toString(dadosAtuais.get("dia"), "").equals(agora.toLocalDate().toString());
        int quantidade = mesmoDia ? ((Number) dadosAtuais.get("enviados_dia")).intValue() : 0;
        if (dadosAtuais.get("DisparoMassaMaxMensagemDia") instanceof Number maiorValor
            && quantidade >= maiorValor.intValue()) {
          adiar(id, dadosAtuais, agora, true);
          continue;
        }
        int lote = ((Number) dadosAtuais.get("lote")).intValue() + 1,
            tamanho = ((Number) dadosAtuais.get("DisparoMassaMensagensPorDisparos")).intValue();
        banco.executar(
            "UPDATE firma_relacionamento_disparo SET dia=:dia,enviados_dia=:qtd,lote=:lote,proximo_envio=now()+(:intervalo * interval '1 second') WHERE disparo_id=:id",
            Map.of(
                "dia",
                agora.toLocalDate(),
                "qtd",
                quantidade + 1,
                "lote",
                lote >= tamanho ? 0 : lote,
                "intervalo",
                lote >= tamanho ? dadosAtuais.get("DisparoMassaIntervaloEntreDisparos") : 0,
                "id",
                id));
        var controle = new HashMap<String, Object>();
        controle.put("DisparoMassaStatus", "Enviando");
        controle.put("DisparoMassaDiaReinicio", null);
        persistencia.gravar("DisparoMassa", id, controle);
      }
      banco.executar(
          "UPDATE firma_relacionamento_envio SET estado='ENVIANDO',atualizada_em=now() WHERE id=:id",
          Map.of("id", itemAtual.get("id")));
      return itemAtual;
    }
    return null;
  }

  private void enviar(Map<String, Object> itemAtual) {
    long id = ((Number) itemAtual.get("id")).longValue();
    boolean transmitido = false;
    try {
      var canal =
          banco.um(
              "SELECT * FROM firma.\"Canal\" WHERE id=:id AND COALESCE(\"CanalStatusUso\",true)",
              Map.of("id", itemAtual.get("canal_id")));
      if (canal == null) throw new IllegalStateException("Canal desativado");
      UUID identificadorUnico = UUID.fromString(canal.get("CanalGUID").toString());
      String texto = saudacao(Objects.toString(itemAtual.get("texto"), ""));
      String telefone = itemAtual.get("destinatario").toString();
      String externo;
      if ("Meta".equals(canal.get("CanalProvedorAPI"))
          && itemAtual.get("modelo_id") instanceof Number registroDoRegistro) {
        var modelo =
            banco.um(
                "SELECT * FROM firma.\"MensagemTemplate\" WHERE id=:id AND \"MensagemTemplateStatusMeta\"='APPROVED' AND COALESCE(\"MensagemTemplateStatusUso\",true)",
                Map.of("id", registroDoRegistro));
        if (modelo == null) throw new IllegalStateException("Template não aprovado");
        List<?> vars =
            itemAtual.get("variaveis") instanceof List<?> listaAtual ? listaAtual : List.of();
        ServicoArquivosChat.Arquivo midia = null;
        if (itemAtual.get("arquivo_id") instanceof Number arquivoAtual)
          midia =
              arquivos.baixar(
                  arquivoAtual.longValue(),
                  contexto(((Number) itemAtual.get("cliente_id")).longValue()));
        else if (modelo.get("MensagemTemplateMediaChave") != null) {
          var anexo =
              banco.um(
                  "SELECT id FROM firma.\"Arquivo\" WHERE \"ArquivoChave\"=:chave ORDER BY id DESC LIMIT 1",
                  Map.of("chave", modelo.get("MensagemTemplateMediaChave")));
          if (anexo != null)
            midia =
                arquivos.baixar(
                    ((Number) anexo.get("id")).longValue(),
                    contexto(((Number) itemAtual.get("cliente_id")).longValue()));
        }
        externo =
            meta.enviarModelo(
                identificadorUnico,
                telefone,
                modelo.get("MensagemTemplateNome").toString(),
                "pt_BR",
                json.readValue(saudacao(json.writeValueAsString(vars)), List.class),
                midia);
      } else if (itemAtual.get("interativa") instanceof Map<?, ?> interativa) {
        externo =
            meta.enviarInterativa(identificadorUnico, telefone, (Map<String, Object>) interativa);
      } else if (itemAtual.get("arquivo_id") instanceof Number registroLocal) {
        var itemDoRegistro = contexto(((Number) itemAtual.get("cliente_id")).longValue());
        var arquivoAtual = arquivos.baixar(registroLocal.longValue(), itemDoRegistro);
        externo =
            transporte.enviarArquivo(
                identificadorUnico,
                telefone,
                arquivoAtual.nome(),
                arquivoAtual.tipo(),
                arquivoAtual.conteudo(),
                texto);
      } else {
        String respostaId = null;
        if (itemAtual.get("resposta_id") instanceof Number rn) {
          var respondida =
              banco.um(
                  "SELECT \"MensagemIdWhatsApp\" FROM firma.\"Mensagem\" WHERE id=:id",
                  Map.of("id", rn));
          if (respondida != null)
            respostaId = Objects.toString(respondida.get("MensagemIdWhatsApp"), null);
        }
        externo = transporte.enviar(identificadorUnico, telefone, texto, respostaId);
      }
      transmitido = true;
      final String externoAtual = externo;
      transacoes.executeWithoutResult(transacao -> {
        banco.executar(
            "UPDATE firma_relacionamento_envio SET estado='ENVIADO',externo=:ext,atualizada_em=now() WHERE id=:id",
            Map.of("ext", externoAtual, "id", id));
        registrar(itemAtual, texto, externoAtual);
        if (itemAtual.get("disparo_id") instanceof Number dadosAtuais)
          atualizar(dadosAtuais.longValue());
      });
    } catch (RuntimeException erro) {
      final boolean incerto = transmitido || erro instanceof EntregaIndeterminada;
      transacoes.executeWithoutResult(transacao -> {
        banco.executar(
            "UPDATE firma_relacionamento_envio SET estado=:estado,erro=:erro,atualizada_em=now() WHERE id=:id",
            Map.of(
                "estado",
                incerto ? "INCERTO" : "FALHOU",
                "erro",
                erro instanceof ResponseStatusException registroAtual
                    ? Objects.toString(registroAtual.getReason(), "Falha no transporte")
                    : "Falha na operação; confira a configuração do canal",
                "id",
                id));
        if (itemAtual.get("disparo_id") instanceof Number dadosAtuais) {
          if (!incerto)
            banco.executar(
                "UPDATE firma_relacionamento_disparo SET enviados_dia=GREATEST(0,enviados_dia-1) WHERE disparo_id=:id",
                Map.of("id", dadosAtuais));
          atualizar(dadosAtuais.longValue());
        }
      });
    }
  }

  private String saudacao(String textoAtual) {
    int hora = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).getHour();
    return textoAtual.replace(
        "{{Saudação}}", hora < 12 ? "Bom dia" : hora < 18 ? "Boa tarde" : "Boa noite");
  }

  private ServicoFirma.Contexto contexto(long cliente) {
    var vinculo =
        banco.um(
            "SELECT * FROM firma_relacionamento_empresa WHERE cliente_id=:c LIMIT 1",
            Map.of("c", cliente));
    if (vinculo == null) throw new IllegalStateException("Empresa sem vínculo");
    return new ServicoFirma.Contexto(
        ((Number) vinculo.get("empresa_id")).longValue(),
        cliente,
        ((Number) vinculo.get("repositorio_id")).longValue(),
        0);
  }

  private void registrar(Map<String, Object> itemAtual, String texto, String externo) {
    var itemDoRegistro = contexto(((Number) itemAtual.get("cliente_id")).longValue());
    Long atendimento =
        itemAtual.get("atendimento_id") instanceof Number valorAtual
            ? valorAtual.longValue()
            : null;
    if (atendimento == null) {
      var contato =
          banco.um(
              "SELECT id FROM firma.\"Contato\" WHERE \"ClienteId\"=:c AND \"ContatoNumero\"=:n LIMIT 1",
              Map.of("c", itemDoRegistro.cliente(), "n", itemAtual.get("destinatario")));
      if (contato == null)
        contato =
            dados.salvar(
                "contatos",
                null,
                Map.of(
                    "ContatoNome",
                    itemAtual.get("destinatario"),
                    "ContatoNumero",
                    itemAtual.get("destinatario")),
                itemDoRegistro);
      var registroLocal =
          banco.um(
              "SELECT id FROM firma.\"Atendimento\" WHERE \"CanalId\"=:ca AND \"ContatoId\"=:co AND \"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY id DESC LIMIT 1",
              Map.of("ca", itemAtual.get("canal_id"), "co", contato.get("id")));
      if (registroLocal == null
          && Boolean.TRUE.equals(itemAtual.get("DisparoMassaDesejaGerarAtendimento"))) {
        var parametrosAtuais =
            new HashMap<String, Object>(
                Map.of("CanalId", itemAtual.get("canal_id"), "ContatoId", contato.get("id")));
        if (itemAtual.get("SetorId") != null)
          parametrosAtuais.put("SetorId", itemAtual.get("SetorId"));
        registroLocal = operacao.nova(parametrosAtuais, itemDoRegistro);
      }
      if (registroLocal != null) atendimento = ((Number) registroLocal.get("id")).longValue();
    }
    var dadosDoRegistro =
        new HashMap<String, Object>(
            Map.of(
                "CanalId",
                itemAtual.get("canal_id"),
                "MensagemTexto",
                texto,
                "MensagemTipo",
                itemAtual.get("arquivo_id") == null ? "Texto" : "Arquivo",
                "MensagemFromMe",
                true,
                "MensagemEnviadaViaAPI",
                !Set.of("IA", "MOTOR_CHAT")
                    .contains(Objects.toString(itemAtual.get("origem"), "API")),
                "MensagemEnviadaPeloBot",
                Set.of("IA", "BOT_EXTERNO")
                    .contains(Objects.toString(itemAtual.get("origem"), "API")),
                "MensagemIdWhatsApp",
                externo,
                "MensagemDataHoraWhatsApp",
                OffsetDateTime.now()));
    dadosDoRegistro.put("MensagemInterativa", itemAtual.get("interativa"));
    dadosDoRegistro.put("MensagemRespostaId", itemAtual.get("resposta_id"));
    dadosDoRegistro.put(
        "MensagemEnviadaPeloAtendente", "MOTOR_CHAT".equals(itemAtual.get("origem")));
    dadosDoRegistro.put("MensagemEnviadaExternamente", false);
    dadosDoRegistro.put("AtendimentoId", atendimento);
    dadosDoRegistro.put("ArquivoId", itemAtual.get("arquivo_id"));
    var registroDoRegistro = persistencia.gravar("Mensagem", null, dadosDoRegistro);
    espelho.salvar("Mensagem", registroDoRegistro, itemDoRegistro);
    if (atendimento != null && Boolean.TRUE.equals(itemAtual.get("encerrar")))
      operacao.acao(atendimento, Map.of("acao", "finalizar"), itemDoRegistro, "API");
  }

  void atualizar(long id) {
    var filas =
        banco.listar(
            "SELECT * FROM firma_relacionamento_envio WHERE disparo_id=:id ORDER BY indice",
            Map.of("id", id));
    int pendentes = 0, enviadas = 0, falhas = 0, canceladas = 0;
    var mensagens = new ArrayList<Map<String, Object>>();
    for (var itemAtual : filas) {
      String situacao =
          switch (itemAtual.get("estado").toString()) {
            case "ENVIADO" -> {
              enviadas++;
              yield "Enviada";
            }
            case "FALHOU", "INCERTO" -> {
              falhas++;
              yield "Falha";
            }
            case "CANCELADO" -> {
              canceladas++;
              yield "Cancelada";
            }
            default -> {
              pendentes++;
              yield "Pendente";
            }
          };
      var dadosLocal =
          new HashMap<String, Object>(
              Map.of(
                  "Destinatario",
                  itemAtual.get("destinatario"),
                  "Mensagem",
                  Objects.toString(itemAtual.get("texto"), ""),
                  "Status",
                  situacao,
                  "EstadoEnvio",
                  itemAtual.get("estado")));
      if (itemAtual.get("erro") != null) dadosLocal.put("Erro", itemAtual.get("erro"));
      mensagens.add(dadosLocal);
    }
    var dadosDoRegistro =
        new HashMap<String, Object>(
            Map.of(
                "DisparoMassaMensagens",
                mensagens,
                "DisparoMassaQuantidadePendente",
                pendentes,
                "DisparoMassaQuantidadeEnviado",
                enviadas,
                "DisparoMassaQuantidadeFalha",
                falhas,
                "DisparoMassaQuantidadeCancelado",
                canceladas));
    var hoje =
        banco.um(
            "SELECT count(*) qtd FROM firma_relacionamento_envio WHERE disparo_id=:id AND estado='ENVIADO' AND timezone('America/Sao_Paulo',atualizada_em)::date=timezone('America/Sao_Paulo',now())::date",
            Map.of("id", id));
    dadosDoRegistro.put(
        "DisparoMassaQtdMensagensEnviadasDiaAtual",
        Map.of("dia", OffsetDateTime.now(), "qtd", hoje.get("qtd")));
    if (pendentes == 0) {
      var dadosAtuais =
          banco.um(
              "SELECT \"DisparoMassaStatus\" FROM firma.\"DisparoMassa\" WHERE id=:id",
              Map.of("id", id));
      if (!"Cancelado".equals(dadosAtuais.get("DisparoMassaStatus")))
        dadosDoRegistro.put("DisparoMassaStatus", "Concluído");
    }
    persistencia.gravar("DisparoMassa", id, dadosDoRegistro);
  }
}
