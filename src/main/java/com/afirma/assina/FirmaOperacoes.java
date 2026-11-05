package com.afirma.assina;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

/** Liga as ações da tela aos serviços de canal, bot, arquivo, template e assistente. */
@Service
public class FirmaOperacoes {
  private final BancoAssineai banco;
  private final ServicoFirma dados;
  private final EspelhoFirma espelho;
  private final ChatTransporte transporte;
  private final ServicoCanalOficial oficial;
  private final ServicoBots bots;
  private final ServicoInteligenciaConversacional ia;
  private final ServicoArquivosChat arquivos;
  private final FirmaPersistencia persistencia;
  private final TemplatesMetaFirma templates;
  private final HistoricoWhatsAppFirma historico;

  public FirmaOperacoes(
      BancoAssineai banco,
      ServicoFirma dados,
      EspelhoFirma espelho,
      ChatTransporte transporte,
      ServicoCanalOficial oficial,
      ServicoBots bots,
      ServicoInteligenciaConversacional ia,
      ServicoArquivosChat arquivos,
      FirmaPersistencia persistencia,
      TemplatesMetaFirma templates,
      HistoricoWhatsAppFirma historico) {
    this.historico = historico;
    this.templates = templates;
    this.banco = banco;
    this.dados = dados;
    this.espelho = espelho;
    this.transporte = transporte;
    this.oficial = oficial;
    this.bots = bots;
    this.ia = ia;
    this.arquivos = arquivos;
    this.persistencia = persistencia;
  }

  long legado(String tabela, long id) {
    Object valor = espelho.id(tabela, id);
    if (valor == null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro sem vínculo operacional");
    return Long.parseLong(valor.toString());
  }

  UUID uuid(String tabela, long id) {
    Object valor = espelho.id(tabela, id);
    if (valor == null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Registro sem vínculo operacional");
    return UUID.fromString(valor.toString());
  }

  Authentication executor(ServicoFirma.Contexto contexto) {
    long empresa = legado("Cliente", contexto.cliente());
    // Credencial interna, nunca enviada ao navegador; só depois de validar contexto e operação.
    var token =
        Jwt.withTokenValue("execucao-interna")
            .header("alg", "none")
            .subject("firma:" + contexto.usuario())
            .claim("empresa_id", empresa)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(30))
            .build();
    return new JwtAuthenticationToken(
        token, List.of(new SimpleGrantedAuthority("SCOPE_chat.manage")));
  }

  public Object canal(long id, String acao, ServicoFirma.Contexto contexto) {
    dados.autorizar(
        contexto,
        "canais",
        Set.of("estado", "conexao", "historico-status").contains(acao) ? "read" : "update");
    var canal = dados.registro("Canal", id, contexto);
    RegrasFirma.exigir(!Boolean.FALSE.equals(canal.get("CanalStatusUso")), "Canal desativado");
    UUID identificadorUnico = UUID.fromString(canal.get("CanalGUID").toString());
    if (acao.equals("estado"))
      return Map.of(
          "estado",
          "Meta".equals(canal.get("CanalProvedorAPI"))
              ? Objects.toString(canal.get("CanalStatus"), "OFFLINE")
              : transporte.estado(identificadorUnico));
    RegrasFirma.exigir(
        !"Meta".equals(canal.get("CanalProvedorAPI")),
        "O canal Meta usa as credenciais da empresa e os identificadores do canal");
    if (acao.equals("historico-status")) {
      var resumo = new LinkedHashMap<String, Object>(historico.resumo(id));
      resumo.put("ponte", transporte.historico(identificadorUnico, false));
      return resumo;
    }
    if (acao.equals("importar")) return transporte.historico(identificadorUnico, true);
    if (acao.equals("conexao")) return transporte.conexao(identificadorUnico);
    return switch (acao) {
      case "conectar" -> {
        transporte.conectar(identificadorUnico);
        yield Map.of("estado", "CONECTANDO");
      }
      case "desconectar" -> {
        transporte.desconectar(identificadorUnico);
        yield Map.of("estado", "DESCONECTADO");
      }
      case "qr" -> Map.of("qr", transporte.qr(identificadorUnico));
      default ->
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação de canal inválida");
    };
  }

  public Object modelo(long id, String acao, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "templates", "update");
    var modeloAtual = dados.registro("MensagemTemplate", id, contexto);
    long canal = OperacaoFirma.numero(modeloAtual, "CanalId");
    dados.registro("Canal", canal, contexto);
    return templates.operar(id, acao, contexto.cliente());
  }

  public String sugerir(long atendimento, long agente, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    var atendimentoAtual = dados.registro("Atendimento", atendimento, contexto);
    var configuracao = dados.registro("InteligenciaArtificial", agente, contexto);
    RegrasFirma.exigir(
        (configuracao.get("CanalId") == null
                || Objects.equals(configuracao.get("CanalId"), atendimentoAtual.get("CanalId")))
            && (configuracao.get("SetorId") == null
                || Objects.equals(configuracao.get("SetorId"), atendimentoAtual.get("SetorId"))),
        "A IA pertence a outro canal ou setor");
    return ia.responder(
        legado("Cliente", contexto.cliente()),
        legado("InteligenciaArtificial", agente),
        uuid("Atendimento", atendimento));
  }

  public Object sessoesBot(long bot, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "bots", "read");
    dados.registro("ChatbotDefinicao", bot, contexto);
    return bots.sessoes(executor(contexto), legado("ChatbotDefinicao", bot));
  }

  public void cancelarBot(long bot, UUID sessao, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "bots", "update");
    dados.registro("ChatbotDefinicao", bot, contexto);
    RegrasFirma.exigir(
        banco.um(
                "SELECT id FROM chat_bot_sessao WHERE id=:id AND definicao_id=:bot AND empresa_id=:e",
                Map.of(
                    "id",
                    sessao,
                    "bot",
                    legado("ChatbotDefinicao", bot),
                    "e",
                    legado("Cliente", contexto.cliente())))
            != null,
        "Sessão não encontrada neste bot");
    bots.cancelarSessao(executor(contexto), sessao);
  }

  public Object enviarArquivo(
      long id, MultipartFile arquivo, String legenda, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    var atendimentoAtual = dados.registro("Atendimento", id, contexto);
    RegrasFirma.exigir(
        !"finalizado".equals(atendimentoAtual.get("AtendimentoStatus")), "Atendimento finalizado");
    return arquivos.enviar(uuid("Atendimento", id), arquivo, legenda, executor(contexto));
  }

  @Transactional
  public Object mensagem(
      long atendimento, long mensagem, String acao, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    var atendimentoAtual = dados.registro("Atendimento", atendimento, contexto);
    var mensagemAtual = dados.registro("Mensagem", mensagem, contexto);
    RegrasFirma.exigir(
        Objects.equals(mensagemAtual.get("AtendimentoId"), atendimentoAtual.get("id")),
        "Mensagem de outro atendimento");
    RegrasFirma.exigir(Set.of("lida", "remover").contains(acao), "Ação inválida");
    if (acao.equals("remover"))
      RegrasFirma.exigir(
          Boolean.TRUE.equals(mensagemAtual.get("MensagemFromMe")),
          "Somente mensagens enviadas podem ser apagadas");
    var contato =
        dados.registro(
            "Contato", ((Number) atendimentoAtual.get("ContatoId")).longValue(), contexto);
    transporte.mensagem(
        uuid("Canal", ((Number) atendimentoAtual.get("CanalId")).longValue()),
        Objects.toString(contato.get("ContatoNumero"), ""),
        Objects.toString(mensagemAtual.get("MensagemIdWhatsApp"), ""),
        Boolean.TRUE.equals(mensagemAtual.get("MensagemFromMe")),
        acao);
    var registroAtual =
        persistencia.gravar(
            "Mensagem",
            mensagem,
            acao.equals("remover") ? Map.of("MensagemDeletada", true) : Map.of("MensagemACK", 3));
    espelho.salvar("Mensagem", registroAtual, contexto);
    return Map.of("sucesso", true);
  }

  @Transactional
  public void revogarSessao(long id, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "sessoes", "read");
    if (!dados.pode(contexto, "all", "manage") && !dados.pode(contexto, "usuariodetail", "update"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    dados.registro("Sessao", id, contexto);
    persistencia.gravar("Sessao", id, Map.of("SessaoStatus", "F"));
  }

  @Transactional
  public Map<String, Object> remover(String slug, long id, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, slug, "delete");
    String texto = dados.modulo(slug).get("tabela").toString();
    RegrasFirma.exigir(
        !Set.of("Cliente", "Atendimento", "DisparoMassa", "Sessao").contains(texto),
        "Use a operação específica deste módulo");
    var registroAtual = dados.registro(texto, id, contexto);
    String situacao =
        switch (texto) {
          case "Canal" -> "CanalStatusUso";
          case "Atendente",
              "Setor",
              "Tag",
              "ListaContato",
              "ChatbotDefinicao",
              "InteligenciaArtificial",
              "Integracao",
              "ChaveAPI" ->
              texto + "Status";
          case "MensagemTemplate" -> "MensagemTemplateStatusUso";
          case "Usuario" -> "UsuarioBloqueado";
          default -> null;
        };
    if (situacao != null) {
      var novo = persistencia.gravar(texto, id, Map.of(situacao, texto.equals("Usuario")));
      espelho.salvar(texto, novo, contexto);
      banco.executar(
          "INSERT INTO firma_relacionamento_exclusao(cliente_id,tabela,registro_id,usuario_id) VALUES(:c,:t,:id,:u) ON CONFLICT DO NOTHING",
          Map.of("c", contexto.cliente(), "t", texto, "id", id, "u", contexto.usuario()));
      return Map.of("excluido", true, "id", id);
    }
    banco.um("SELECT set_config('firma.relacionamento_direcao','canonico',true)", Map.of());
    if (texto.equals("AtendenteSetor"))
      banco.executar(
          "DELETE FROM chat_atendente_setor WHERE atendente_id=:a AND setor_id=:s",
          Map.of(
              "a",
              legado("Atendente", ((Number) registroAtual.get("AtendenteId")).longValue()),
              "s",
              legado("Setor", ((Number) registroAtual.get("SetorId")).longValue())));
    if (texto.equals("Webhook")) {
      Object antigo = espelho.id(texto, id);
      if (antigo != null)
        banco.executar(
            "UPDATE chat_webhook SET ativo=false WHERE id=CAST(:id AS uuid)",
            Map.of("id", antigo.toString()));
    }
    if (texto.equals("MensagemRapida")) {
      Object antigo = espelho.id(texto, id);
      if (antigo != null)
        banco.executar(
            "UPDATE chat_mensagem_rapida SET ativa=false WHERE id=:id",
            Map.of("id", Long.parseLong(antigo.toString())));
    }
    String esquema =
        Set.of("Perfil", "Permissao", "PerfilPermissao", "UsuarioPermissao").contains(texto)
            ? "firma_identidade"
            : "firma";
    banco.executar("DELETE FROM " + esquema + ".\"" + texto + "\" WHERE id=:id", Map.of("id", id));
    banco.executar(
        "DELETE FROM firma_relacionamento_vinculo WHERE destino=:t AND destino_id=:id",
        Map.of("t", texto, "id", id));
    return Map.of("excluido", true, "id", id);
  }
}
