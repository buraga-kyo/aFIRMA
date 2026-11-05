package com.afirma.assina;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Recebe os lotes da ponte e guarda as mensagens com suas datas, sem iniciar bots ou respostas. */
@Service
public class HistoricoWhatsAppFirma {
  private final BancoAssineai banco;
  private final FirmaPersistencia persistencia;
  private final EspelhoFirma espelho;
  private final ObjectMapper json;

  public HistoricoWhatsAppFirma(
      BancoAssineai banco,
      FirmaPersistencia persistencia,
      EspelhoFirma espelho,
      ObjectMapper json) {
    this.banco = banco;
    this.persistencia = persistencia;
    this.espelho = espelho;
    this.json = json;
  }

  public Map<String, Object> resumo(long canal) {
    return banco.um(
        "SELECT (SELECT count(*) FROM firma_relacionamento_historico_conversa WHERE canal_id=:c) conversas,COALESCE(sum(mensagens),0) mensagens,COALESCE(sum(duplicadas),0) duplicadas,COALESCE(sum(ignoradas),0) ignoradas,max(recebida_em) atualizada_em FROM firma_relacionamento_historico_lote WHERE canal_id=:c",
        Map.of("c", canal));
  }

  @Transactional
  public void receber(UUID identificadorUnico, JsonNode lote) {
    RegrasFirma.exigir(
        lote != null
            && lote.isObject()
            && lote.path("mensagens").isArray()
            && lote.path("mensagens").size() <= 50
            && lote.path("conversas").isArray()
            && lote.path("conversas").size() <= 100,
        "Lote de histórico inválido");
    UUID id;
    try {
      id = UUID.fromString(lote.path("lote").asText());
    } catch (Exception erro) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lote inválido");
    }
    var canal =
        banco.um(
            "SELECT * FROM firma.\"Canal\" WHERE \"CanalGUID\"=:g AND \"CanalStatusUso\" AND \"CanalProvedorAPI\"='Baileys'",
            Map.of("g", identificadorUnico));
    if (canal == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal indisponível");
    long canalId = ((Number) canal.get("id")).longValue(),
        cliente = ((Number) canal.get("ClienteId")).longValue();
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of("k", "historico:" + canalId));
    var parametros = Map.of("c", canalId, "l", id);
    if (banco.um(
            "SELECT lote FROM firma_relacionamento_historico_lote WHERE canal_id=:c AND lote=:l",
            parametros)
        != null) return;
    banco.um("SELECT set_config('firma.importando_historico','true',true)", Map.of());
    var contexto = new ServicoFirma.Contexto(0, cliente, 0, 0);
    var atendimentos = new HashMap<String, Map<String, Object>>();
    int novas = 0,
        duplicadas = 0,
        ignoradas = Math.max(0, Math.min(100000, lote.path("ignoradas").asInt(0)));
    for (var conversa : lote.path("conversas")) {
      String telefone = conversa.path("telefone").asText();
      if (!telefone.matches("[0-9]{8,15}")) {
        ignoradas++;
        continue;
      }
      atendimentos.put(
          telefone, conversa(canal, telefone, conversa.path("nome").asText(""), contexto));
    }
    var afetados = new HashSet<Long>();
    for (var registro : lote.path("mensagens")) {
      String telefone = registro.path("telefone").asText(), externo = registro.path("id").asText();
      long timestamp = registro.path("timestamp").asLong();
      if (!telefone.matches("[0-9]{8,15}")
          || externo.isBlank()
          || externo.length() > 100
          || timestamp < 946684800L
          || timestamp > Instant.now().plusSeconds(86400).getEpochSecond()
          || registro.path("texto").asText().length() > 10000) {
        ignoradas++;
        continue;
      }
      var existe =
          banco.um(
              "SELECT id,\"CanalId\" FROM firma.\"Mensagem\" WHERE \"MensagemIdWhatsApp\"=:id",
              Map.of("id", externo));
      if (existe != null) {
        duplicadas++;
        continue;
      }
      var registroAtual =
          atendimentos.computeIfAbsent(
              telefone,
              valor -> conversa(canal, valor, registro.path("nome").asText(""), contexto));
      long atendimento = ((Number) registroAtual.get("id")).longValue();
      var dados = new LinkedHashMap<String, Object>();
      dados.put("CanalId", canalId);
      dados.put("AtendimentoId", atendimento);
      dados.put("MensagemIdWhatsApp", externo);
      dados.put("MensagemTexto", registro.path("texto").asText("[mensagem sem texto]"));
      dados.put("MensagemTipo", registro.path("tipo").asText("Texto"));
      dados.put("MensagemFromMe", registro.path("fromMe").asBoolean());
      dados.put("MensagemACK", registro.path("ack").asInt(2));
      dados.put("MensagemEnviadaExternamente", registro.path("fromMe").asBoolean());
      dados.put(
          "MensagemDataHoraWhatsApp", Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC));
      dados.put(
          "MensagemRAW",
          json.writeValueAsString(
              Map.of("historicoImportado", true, "tipo", registro.path("tipo").asText("Texto"))));
      if (registro.path("key").isObject())
        dados.put("MensagemKeyWhatsApp", json.writeValueAsString(registro.path("key")));
      var nova = persistencia.inserirHistorico(dados);
      if (nova == null) {
        duplicadas++;
        continue;
      }
      espelho.salvar("Mensagem", nova, contexto);
      novas++;
      afetados.add(atendimento);
      // O espelho leva a data da mensagem para as tabelas que o chat consulta.
      banco.executar(
          "UPDATE chat_mensagem SET criado_em=:d WHERE id=CAST(:id AS bigint)",
          Map.of(
              "id",
              espelho.id("Mensagem", nova.get("id")).toString(),
              "d",
              dados.get("MensagemDataHoraWhatsApp")));
    }
    for (long atendimento : afetados) {
      banco.executar(
          "UPDATE firma.\"Atendimento\" a SET \"MensagemId\"=(SELECT id FROM firma.\"Mensagem\" WHERE \"AtendimentoId\"=a.id ORDER BY COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\") DESC,id DESC LIMIT 1),\"AtendimentoDataUltimaMensagemCliente\"=(SELECT max(COALESCE(\"MensagemDataHoraWhatsApp\",\"createdAt\")) FROM firma.\"Mensagem\" WHERE \"AtendimentoId\"=a.id AND NOT COALESCE(\"MensagemFromMe\",false)) WHERE a.id=:id",
          Map.of("id", atendimento));
    }
    var resultado = new HashMap<String, Object>(parametros);
    resultado.put("m", novas);
    resultado.put("d", duplicadas);
    resultado.put("i", ignoradas);
    banco.executar(
        "INSERT INTO firma_relacionamento_historico_lote(canal_id,lote,mensagens,duplicadas,ignoradas) VALUES(:c,:l,:m,:d,:i)",
        resultado);
  }

  private Map<String, Object> conversa(
      Map<String, Object> canal, String numero, String nome, ServicoFirma.Contexto contextoAtual) {
    long canalId = ((Number) canal.get("id")).longValue();
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of("k", "Contato:" + contextoAtual.cliente() + ":" + numero));
    var contato =
        banco.um(
            "SELECT * FROM firma.\"Contato\" WHERE \"ClienteId\"=:c AND \"ContatoNumero\"=:n ORDER BY id LIMIT 1",
            Map.of("c", contextoAtual.cliente(), "n", numero));
    if (contato == null) {
      contato =
          persistencia.gravar(
              "Contato",
              null,
              Map.of(
                  "ClienteId",
                  contextoAtual.cliente(),
                  "ContatoNome",
                  nome.isBlank() ? numero : nome.substring(0, Math.min(70, nome.length())),
                  "ContatoNumero",
                  numero,
                  "ContatoJidWhatsApp",
                  numero + "@s.whatsapp.net"));
      espelho.salvar("Contato", contato, contextoAtual);
    }
    long contatoId = ((Number) contato.get("id")).longValue();
    var parametrosAtuais = Map.of("c", canalId, "co", contatoId);
    var importada =
        banco.um(
            "SELECT a.* FROM firma_relacionamento_historico_conversa h JOIN firma.\"Atendimento\" a ON a.id=h.atendimento_id WHERE h.canal_id=:c AND h.contato_id=:co",
            parametrosAtuais);
    if (importada != null) return importada;
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of("k", "atendimento:" + canalId + ":" + contatoId));
    var registroAtual =
        banco.um(
            "SELECT * FROM firma.\"Atendimento\" WHERE \"CanalId\"=:c AND \"ContatoId\"=:co AND \"AtendimentoStatus\" IN ('pendente','emandamento') ORDER BY id DESC LIMIT 1",
            parametrosAtuais);
    if (registroAtual == null) {
      var setor =
          banco.um(
              "SELECT id FROM firma.\"Setor\" WHERE \"SetorStatus\" AND \"CanalId\"=:canal ORDER BY id LIMIT 1",
              Map.of("c", contextoAtual.cliente(), "canal", canalId));
      var dados = new LinkedHashMap<String, Object>();
      dados.put("ClienteId", contextoAtual.cliente());
      dados.put("CanalId", canalId);
      dados.put("ContatoId", contatoId);
      dados.put("SetorId", setor == null ? null : setor.get("id"));
      dados.put("AtendimentoStatus", "pendente");
      dados.put(
          "AtendimentoProtocolo", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
      dados.put("AtendimentoQtdMensagensNaoLidas", 0);
      dados.put("AtendimentoBOTExterno", false);
      registroAtual = persistencia.gravar("Atendimento", null, dados);
      espelho.salvar("Atendimento", registroAtual, contextoAtual);
    }
    banco.executar(
        "INSERT INTO firma_relacionamento_historico_conversa VALUES(:c,:co,:a)",
        Map.of("c", canalId, "co", contatoId, "a", registroAtual.get("id")));
    return registroAtual;
  }
}
