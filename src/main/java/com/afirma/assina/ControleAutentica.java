package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/autentica")
public class ControleAutentica {
  private final IdentidadeAssineai identidade;
  private final ServicoAutentica servico;
  private final OperacaoAutentica operacao;private final DisparosAutentica disparos;private final AutenticaOperacoes extras;private final ArquivosAutentica arquivos;private final IndicadoresAutentica indicadores;private final IntegracoesAutentica integracoes;private final WebhooksAutentica webhooks;private final ConteudoAutentica conteudo;private final TemplatesMetaAutentica templates;private final AutomacaoAutentica automacoes;private final NotificacoesAutentica notificacoes;
  public ControleAutentica(IdentidadeAssineai identidade,ServicoAutentica servico,OperacaoAutentica operacao,DisparosAutentica disparos,AutenticaOperacoes extras,ArquivosAutentica arquivos,IndicadoresAutentica indicadores,IntegracoesAutentica integracoes,WebhooksAutentica webhooks,ConteudoAutentica conteudo,TemplatesMetaAutentica templates,AutomacaoAutentica automacoes,NotificacoesAutentica notificacoes){this.notificacoes=notificacoes;this.templates=templates;this.automacoes=automacoes;this.indicadores=indicadores;this.integracoes=integracoes;this.webhooks=webhooks;this.conteudo=conteudo;this.disparos=disparos;this.extras=extras;this.arquivos=arquivos;this.identidade=identidade;this.servico=servico;this.operacao=operacao;}
  private ServicoAutentica.Contexto contexto(HttpServletRequest r){return servico.contexto(identidade.autenticar(r));}
  @GetMapping("/contexto") public Object contextoAtual(HttpServletRequest r){var c=contexto(r);return Map.of("nome",Objects.toString(servico.registro("Cliente",c.cliente(),c).get("ClienteNome"),"Sua empresa"),"clienteId",c.cliente(),"empresaId",c.empresa(),"usuarioId",c.usuario(),"repositorioId",c.repositorio(),"modulos",servico.catalogo(c),"resumo",servico.panorama(c));}
  @GetMapping("/registros/{modulo}") public Object listar(@PathVariable String modulo,@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="30")int tamanho,@RequestParam(defaultValue="")String busca,@RequestParam(required=false)String estado,@RequestParam Map<String,String> filtros,HttpServletRequest r){if(pagina<1||pagina>100000||tamanho<1||tamanho>100||busca.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginação inválida");return servico.listar(modulo,contexto(r),pagina,tamanho,busca,estado,filtros);}
  @GetMapping("/registros/{modulo}/{id}") public Object registro(@PathVariable String modulo,@PathVariable long id,HttpServletRequest r){return servico.publico(modulo,id,contexto(r));}
  @PostMapping("/registros/{modulo}") public Object criar(@PathVariable String modulo,@RequestBody Map<String,Object> dados,HttpServletRequest r){return modulo.equals("disparos")?disparos.criar(dados,contexto(r)):servico.salvar(modulo,null,dados,contexto(r));}
  @PutMapping("/registros/{modulo}/{id}") public Object atualizar(@PathVariable String modulo,@PathVariable long id,@RequestBody Map<String,Object> dados,HttpServletRequest r){return servico.salvar(modulo,id,dados,contexto(r));}
  @GetMapping("/conversas/{id}") public Object conversa(@PathVariable long id,HttpServletRequest r){return operacao.conversa(id,contexto(r));}
  @PostMapping("/conversas") public Object nova(@RequestBody Map<String,Object> dados,HttpServletRequest r){return operacao.nova(dados,contexto(r));}
  @PostMapping("/conversas/{id}/acao") public Object acao(@PathVariable long id,@RequestBody Map<String,Object> dados,HttpServletRequest r){String ator=identidade.autenticar(r);return operacao.acao(id,dados,contexto(r),ator);}
  @GetMapping("/conversas/{id}/mensagens") public Object mensagens(@PathVariable long id,@RequestParam(defaultValue="0")long antes,HttpServletRequest r){return operacao.mensagens(id,contexto(r),antes);}
  @PutMapping("/conversas/{id}/tags") public Object tags(@PathVariable long id,@RequestBody List<Long> ids,HttpServletRequest r){return operacao.tags(id,ids,contexto(r));}
  @GetMapping("/conversas/{id}/historico") public Object historico(@PathVariable long id,HttpServletRequest r){return operacao.historico(id,contexto(r));}
  @PostMapping("/conversas/{id}/mensagens") public Object enviar(@PathVariable long id,@RequestBody Map<String,Object> dados,HttpServletRequest r){String ator=identidade.autenticar(r);return operacao.enviar(id,dados,contexto(r),ator);}

  @DeleteMapping("/registros/{modulo}/{id}") public Object remover(@PathVariable String modulo,@PathVariable long id,HttpServletRequest r){return extras.remover(modulo,id,contexto(r));}
  @PostMapping("/disparos/{id}/{acao}") public Object disparo(@PathVariable long id,@PathVariable String acao,HttpServletRequest r){return disparos.acao(id,acao,contexto(r));}
  @PostMapping("/canais/{id}/{acao}") public Object canal(@PathVariable long id,@PathVariable String acao,HttpServletRequest r){return extras.canal(id,acao,contexto(r));}
  @PostMapping("/templates/{id}/{acao}") public Object modelo(@PathVariable long id,@PathVariable String acao,HttpServletRequest r){return extras.modelo(id,acao,contexto(r));}
  @PostMapping("/conversas/{id}/ia/{agente}") public Object sugerir(@PathVariable long id,@PathVariable long agente,HttpServletRequest r){return Map.of("texto",extras.sugerir(id,agente,contexto(r)));}
  @GetMapping("/bots/{id}/sessoes") public Object sessoesBot(@PathVariable long id,HttpServletRequest r){return extras.sessoesBot(id,contexto(r));}
  @DeleteMapping("/bots/{id}/sessoes/{sessao}") public void cancelarBot(@PathVariable long id,@PathVariable UUID sessao,HttpServletRequest r){extras.cancelarBot(id,sessao,contexto(r));}
  @DeleteMapping("/sessoes/{id}") public void revogarSessao(@PathVariable long id,HttpServletRequest r){extras.revogarSessao(id,contexto(r));}
  @PostMapping("/conversas/{id}/mensagens/{mensagem}/{acao}") public Object mensagem(@PathVariable long id,@PathVariable long mensagem,@PathVariable String acao,HttpServletRequest r){return extras.mensagem(id,mensagem,acao,contexto(r));}
  @PostMapping(value="/arquivos",consumes="multipart/form-data") public Object upload(@RequestParam org.springframework.web.multipart.MultipartFile arquivo,HttpServletRequest r){return arquivos.guardar(arquivo,contexto(r));}
  @GetMapping("/arquivos/{id}") public org.springframework.http.ResponseEntity<byte[]> baixar(@PathVariable long id,HttpServletRequest r){var a=arquivos.baixar(id,contexto(r));return org.springframework.http.ResponseEntity.ok().header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(a.nome(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(org.springframework.http.MediaType.parseMediaType(a.tipo())).body(a.conteudo());}
  @PostMapping(value="/conversas/{id}/arquivos",consumes="multipart/form-data") public Object enviarArquivo(@PathVariable long id,@RequestParam org.springframework.web.multipart.MultipartFile arquivo,@RequestParam(required=false)String legenda,HttpServletRequest r){return extras.enviarArquivo(id,arquivo,legenda,contexto(r));}

  @GetMapping("/indicadores") public Object indicadores(@RequestParam(required=false)java.time.LocalDate inicio,@RequestParam(required=false)java.time.LocalDate fim,@RequestParam(required=false)Long setor,HttpServletRequest r){var hoje=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));return indicadores.consultar(contexto(r),inicio==null?hoje.withDayOfYear(1):inicio,fim==null?hoje:fim,setor);}
  @PostMapping("/integracoes/{nome}/{acao}") public Object integracao(@PathVariable String nome,@PathVariable String acao,@RequestBody Map<String,Object> filtros,HttpServletRequest r){var c=contexto(r);servico.autorizar(c,"integracoes","read");return integracoes.consultar(c.cliente(),nome,acao,filtros);}
  @GetMapping("/webhooks/{id}/entregas") public Object entregas(@PathVariable long id,HttpServletRequest r){return webhooks.entregas(id,contexto(r));}
  @PostMapping(value="/ia/conteudo/pdf",consumes="multipart/form-data") public Object pdfIA(@RequestParam List<org.springframework.web.multipart.MultipartFile> arquivos,HttpServletRequest r){return Map.of("Texto",conteudo.pdf(arquivos,contexto(r)));}
  @PostMapping("/ia/conteudo/links") public Object linksIA(@RequestBody List<String> links,HttpServletRequest r){return Map.of("Texto",conteudo.links(links,contexto(r)));}

 @PostMapping(value="/templates/exemplo-midia",consumes="multipart/form-data")public Object exemploMidia(@RequestParam org.springframework.web.multipart.MultipartFile arquivo,HttpServletRequest r){var c=contexto(r);servico.autorizar(c,"templates","update");return Map.of("MediaHandle",templates.media(c.cliente(),arquivo));}
 @GetMapping("/ia/{id}/execucoes")public Object execucoesIA(@PathVariable long id,HttpServletRequest r){return automacoes.execucoes(id,contexto(r));}
 @GetMapping("/firebase/config")public Object firebase(){return notificacoes.config();}
 @PostMapping("/notificacoes/token")public void notificacao(@RequestBody Map<String,Object> p,HttpServletRequest r){notificacoes.registrar(Objects.toString(p.get("Token"),""),OperacaoAutentica.numero(p,"AtendenteId"),contexto(r));}

 @GetMapping("/operador")public Object operador(HttpServletRequest r){var c=contexto(r);return servico.operador(c);}
 @PutMapping("/operador")public Object preferencias(@RequestBody Map<String,Object> p,HttpServletRequest r){return servico.preferencias(contexto(r),p);}
}
