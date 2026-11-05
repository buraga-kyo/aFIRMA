package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

/** A tela chama /api/firma. Aqui a conta é conferida antes de chamar os serviços do atendimento. */
@RestController
@RequestMapping("/api/firma")
public class ControleFirma {
  private final IdentidadeAssineai identidade;
  private final ServicoFirma servico;
  private final OperacaoFirma operacao;
  private final DisparosFirma disparos;
  private final FirmaOperacoes extras;
  private final ArquivosFirma arquivos;
  private final IndicadoresFirma indicadores;
  private final IntegracoesFirma integracoes;
  private final WebhooksFirma webhooks;
  private final ConteudoFirma conteudo;
  private final TemplatesMetaFirma templates;
  private final AutomacaoFirma automacoes;
  private final NotificacoesFirma notificacoes;

  public ControleFirma(
      IdentidadeAssineai identidade,
      ServicoFirma servico,
      OperacaoFirma operacao,
      DisparosFirma disparos,
      FirmaOperacoes extras,
      ArquivosFirma arquivos,
      IndicadoresFirma indicadores,
      IntegracoesFirma integracoes,
      WebhooksFirma webhooks,
      ConteudoFirma conteudo,
      TemplatesMetaFirma templates,
      AutomacaoFirma automacoes,
      NotificacoesFirma notificacoes) {
    this.notificacoes = notificacoes;
    this.templates = templates;
    this.automacoes = automacoes;
    this.indicadores = indicadores;
    this.integracoes = integracoes;
    this.webhooks = webhooks;
    this.conteudo = conteudo;
    this.disparos = disparos;
    this.extras = extras;
    this.arquivos = arquivos;
    this.identidade = identidade;
    this.servico = servico;
    this.operacao = operacao;
  }

  private ServicoFirma.Contexto contexto(HttpServletRequest requisicao) {
    return servico.contexto(identidade.autenticar(requisicao));
  }

  @GetMapping("/contexto")
  public Object contextoAtual(HttpServletRequest requisicao) {
    var item = contexto(requisicao);
    return Map.of(
        "nome",
        Objects.toString(
            servico.registro("Cliente", item.cliente(), item).get("ClienteNome"), "Sua empresa"),
        "clienteId",
        item.cliente(),
        "empresaId",
        item.empresa(),
        "usuarioId",
        item.usuario(),
        "repositorioId",
        item.repositorio(),
        "modulos",
        servico.catalogo(item),
        "resumo",
        servico.panorama(item));
  }

  @GetMapping("/registros/{modulo}")
  public Object listar(
      @PathVariable String modulo,
      @RequestParam(defaultValue = "1") int pagina,
      @RequestParam(defaultValue = "30") int tamanho,
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(required = false) String estado,
      @RequestParam Map<String, String> filtros,
      HttpServletRequest requisicao) {
    if (pagina < 1 || pagina > 100000 || tamanho < 1 || tamanho > 100 || busca.length() > 200)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida");
    return servico.listar(modulo, contexto(requisicao), pagina, tamanho, busca, estado, filtros);
  }

  @GetMapping("/registros/{modulo}/{id}")
  public Object registro(
      @PathVariable String modulo, @PathVariable long id, HttpServletRequest requisicao) {
    return servico.publico(modulo, id, contexto(requisicao));
  }

  @PostMapping("/registros/{modulo}")
  public Object criar(
      @PathVariable String modulo,
      @RequestBody Map<String, Object> dados,
      HttpServletRequest requisicao) {
    return modulo.equals("disparos")
        ? disparos.criar(dados, contexto(requisicao))
        : servico.salvar(modulo, null, dados, contexto(requisicao));
  }

  @PutMapping("/registros/{modulo}/{id}")
  public Object atualizar(
      @PathVariable String modulo,
      @PathVariable long id,
      @RequestBody Map<String, Object> dados,
      HttpServletRequest requisicao) {
    return servico.salvar(modulo, id, dados, contexto(requisicao));
  }

  @GetMapping("/conversas/{id}")
  public Object conversa(@PathVariable long id, HttpServletRequest requisicao) {
    return operacao.conversa(id, contexto(requisicao));
  }

  @PostMapping("/conversas")
  public Object nova(@RequestBody Map<String, Object> dados, HttpServletRequest requisicao) {
    return operacao.nova(dados, contexto(requisicao));
  }

  @PostMapping("/conversas/{id}/acao")
  public Object acao(
      @PathVariable long id,
      @RequestBody Map<String, Object> dados,
      HttpServletRequest requisicao) {
    String ator = identidade.autenticar(requisicao);
    return operacao.acao(id, dados, contexto(requisicao), ator);
  }

  @GetMapping("/conversas/{id}/mensagens")
  public Object mensagens(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") long antes,
      HttpServletRequest requisicao) {
    return operacao.mensagens(id, contexto(requisicao), antes);
  }

  @PutMapping("/conversas/{id}/tags")
  public Object tags(
      @PathVariable long id, @RequestBody List<Long> ids, HttpServletRequest requisicao) {
    return operacao.tags(id, ids, contexto(requisicao));
  }

  @GetMapping("/conversas/{id}/historico")
  public Object historico(@PathVariable long id, HttpServletRequest requisicao) {
    return operacao.historico(id, contexto(requisicao));
  }

  @PostMapping("/conversas/{id}/mensagens")
  public Object enviar(
      @PathVariable long id,
      @RequestBody Map<String, Object> dados,
      HttpServletRequest requisicao) {
    String ator = identidade.autenticar(requisicao);
    return operacao.enviar(id, dados, contexto(requisicao), ator);
  }

  @DeleteMapping("/registros/{modulo}/{id}")
  public Object remover(
      @PathVariable String modulo, @PathVariable long id, HttpServletRequest requisicao) {
    return extras.remover(modulo, id, contexto(requisicao));
  }

  @PostMapping("/disparos/{id}/{acao}")
  public Object disparo(
      @PathVariable long id, @PathVariable String acao, HttpServletRequest requisicao) {
    return disparos.acao(id, acao, contexto(requisicao));
  }

  @PostMapping("/canais/{id}/{acao}")
  public Object canal(
      @PathVariable long id, @PathVariable String acao, HttpServletRequest requisicao) {
    return extras.canal(id, acao, contexto(requisicao));
  }

  @PostMapping("/templates/{id}/{acao}")
  public Object modelo(
      @PathVariable long id, @PathVariable String acao, HttpServletRequest requisicao) {
    return extras.modelo(id, acao, contexto(requisicao));
  }

  @PostMapping("/conversas/{id}/ia/{agente}")
  public Object sugerir(
      @PathVariable long id, @PathVariable long agente, HttpServletRequest requisicao) {
    return Map.of("texto", extras.sugerir(id, agente, contexto(requisicao)));
  }

  @GetMapping("/bots/{id}/sessoes")
  public Object sessoesBot(@PathVariable long id, HttpServletRequest requisicao) {
    return extras.sessoesBot(id, contexto(requisicao));
  }

  @DeleteMapping("/bots/{id}/sessoes/{sessao}")
  public void cancelarBot(
      @PathVariable long id, @PathVariable UUID sessao, HttpServletRequest requisicao) {
    extras.cancelarBot(id, sessao, contexto(requisicao));
  }

  @DeleteMapping("/sessoes/{id}")
  public void revogarSessao(@PathVariable long id, HttpServletRequest requisicao) {
    extras.revogarSessao(id, contexto(requisicao));
  }

  @PostMapping("/conversas/{id}/mensagens/{mensagem}/{acao}")
  public Object mensagem(
      @PathVariable long id,
      @PathVariable long mensagem,
      @PathVariable String acao,
      HttpServletRequest requisicao) {
    return extras.mensagem(id, mensagem, acao, contexto(requisicao));
  }

  @PostMapping(value = "/arquivos", consumes = "multipart/form-data")
  public Object upload(
      @RequestParam org.springframework.web.multipart.MultipartFile arquivo,
      HttpServletRequest requisicao) {
    return arquivos.guardar(arquivo, contexto(requisicao));
  }

  @GetMapping("/arquivos/{id}")
  public org.springframework.http.ResponseEntity<byte[]> baixar(
      @PathVariable long id, HttpServletRequest requisicao) {
    var registroAtual = arquivos.baixar(id, contexto(requisicao));
    return org.springframework.http.ResponseEntity.ok()
        .header(
            "Content-Disposition",
            org.springframework.http.ContentDisposition.attachment()
                .filename(registroAtual.nome(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(org.springframework.http.MediaType.parseMediaType(registroAtual.tipo()))
        .body(registroAtual.conteudo());
  }

  @PostMapping(value = "/conversas/{id}/arquivos", consumes = "multipart/form-data")
  public Object enviarArquivo(
      @PathVariable long id,
      @RequestParam org.springframework.web.multipart.MultipartFile arquivo,
      @RequestParam(required = false) String legenda,
      HttpServletRequest requisicao) {
    return extras.enviarArquivo(id, arquivo, legenda, contexto(requisicao));
  }

  @GetMapping("/indicadores")
  public Object indicadores(
      @RequestParam(required = false) java.time.LocalDate inicio,
      @RequestParam(required = false) java.time.LocalDate fim,
      @RequestParam(required = false) Long setor,
      HttpServletRequest requisicao) {
    var hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
    return indicadores.consultar(
        contexto(requisicao),
        inicio == null ? hoje.withDayOfYear(1) : inicio,
        fim == null ? hoje : fim,
        setor);
  }

  @PostMapping("/integracoes/{nome}/{acao}")
  public Object integracao(
      @PathVariable String nome,
      @PathVariable String acao,
      @RequestBody Map<String, Object> filtros,
      HttpServletRequest requisicao) {
    var item = contexto(requisicao);
    servico.autorizar(item, "integracoes", "read");
    return integracoes.consultar(item.cliente(), nome, acao, filtros);
  }

  @GetMapping("/webhooks/{id}/entregas")
  public Object entregas(@PathVariable long id, HttpServletRequest requisicao) {
    return webhooks.entregas(id, contexto(requisicao));
  }

  @PostMapping(value = "/ia/conteudo/pdf", consumes = "multipart/form-data")
  public Object pdfIA(
      @RequestParam List<org.springframework.web.multipart.MultipartFile> arquivos,
      HttpServletRequest requisicao) {
    return Map.of("Texto", conteudo.pdf(arquivos, contexto(requisicao)));
  }

  @PostMapping("/ia/conteudo/links")
  public Object linksIA(@RequestBody List<String> links, HttpServletRequest requisicao) {
    return Map.of("Texto", conteudo.links(links, contexto(requisicao)));
  }

  @PostMapping(value = "/templates/exemplo-midia", consumes = "multipart/form-data")
  public Object exemploMidia(
      @RequestParam org.springframework.web.multipart.MultipartFile arquivo,
      HttpServletRequest requisicao) {
    var item = contexto(requisicao);
    servico.autorizar(item, "templates", "update");
    return Map.of("MediaHandle", templates.media(item.cliente(), arquivo));
  }

  @GetMapping("/ia/{id}/execucoes")
  public Object execucoesIA(@PathVariable long id, HttpServletRequest requisicao) {
    return automacoes.execucoes(id, contexto(requisicao));
  }

  @GetMapping("/firebase/config")
  public Object firebase() {
    return notificacoes.config();
  }

  @PostMapping("/notificacoes/token")
  public void notificacao(
      @RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    notificacoes.registrar(
        Objects.toString(parametros.get("Token"), ""),
        OperacaoFirma.numero(parametros, "AtendenteId"),
        contexto(requisicao));
  }

  @GetMapping("/operador")
  public Object operador(HttpServletRequest requisicao) {
    var item = contexto(requisicao);
    return servico.operador(item);
  }

  @PutMapping("/operador")
  public Object preferencias(
      @RequestBody Map<String, Object> parametros, HttpServletRequest requisicao) {
    return servico.preferencias(contexto(requisicao), parametros);
  }
}
