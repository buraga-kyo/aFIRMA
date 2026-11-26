package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assineai/modelos-email")
public class ControleModelosEmailAssinatura {
  private final IdentidadeAssineai identidade;
  private final ModelosEmailAssinatura modelos;
  private final EnviadorCodigoConvite enviador;
  private final ServicoPlanoBuscaAssineAi busca;

  public ControleModelosEmailAssinatura(IdentidadeAssineai identidade, ModelosEmailAssinatura modelos,
      EnviadorCodigoConvite enviador, ServicoPlanoBuscaAssineAi busca) {
    this.identidade = identidade;
    this.modelos = modelos;
    this.enviador = enviador;
    this.busca = busca;
  }

  @GetMapping({"", "/verificacao"})
  public ResponseEntity<List<ModelosEmailAssinatura.Resumo>> listar(HttpServletRequest pedido) {
    return resposta(modelos.listar(acesso(pedido).empresa()));
  }

  @PostMapping("/plano-busca")
  public ResponseEntity<ServicoPlanoBuscaAssineAi.Plano> plano(HttpServletRequest pedido,
      @RequestBody Map<String, String> dados) {
    acesso(pedido);
    return resposta(busca.interpretarTemplates(dados.get("consulta")));
  }

  @GetMapping("/{momento}/novo")
  public ResponseEntity<Map<String, Object>> novo(HttpServletRequest pedido, @PathVariable String momento) {
    acesso(pedido);
    return editar(modelos.novo(MomentoEmailAssinatura.de(momento)));
  }

  @GetMapping("/{momento}/{id}")
  public ResponseEntity<Map<String, Object>> obter(HttpServletRequest pedido, @PathVariable String momento, @PathVariable long id) {
    return editar(modelos.obter(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), id));
  }

  @PostMapping("/{momento}")
  public ResponseEntity<ModelosEmailAssinatura.Detalhe> criar(HttpServletRequest pedido, @PathVariable String momento,
      @RequestBody ModelosEmailAssinatura.Entrada entrada) {
    return resposta(modelos.salvar(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), null, entrada));
  }

  @PutMapping("/{momento}/{id}")
  public ResponseEntity<ModelosEmailAssinatura.Detalhe> salvar(HttpServletRequest pedido, @PathVariable String momento, @PathVariable long id,
      @RequestBody ModelosEmailAssinatura.Entrada entrada) {
    return resposta(modelos.salvar(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), id, entrada));
  }

  @PostMapping({"/{momento}/publicar", "/{momento}/{id}/publicar"})
  public ResponseEntity<ModelosEmailAssinatura.Detalhe> publicar(HttpServletRequest pedido, @PathVariable String momento,
      @PathVariable(required = false) Long id, @RequestBody ModelosEmailAssinatura.Entrada entrada) {
    var acesso = acesso(pedido);
    return resposta(modelos.publicar(acesso.empresa(), MomentoEmailAssinatura.de(momento), id, acesso.ator(), entrada));
  }

  @PostMapping("/{momento}/{id}/duplicar")
  public ResponseEntity<ModelosEmailAssinatura.Detalhe> duplicar(HttpServletRequest pedido, @PathVariable String momento, @PathVariable long id) {
    return resposta(modelos.duplicar(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), id));
  }

  @DeleteMapping("/{momento}/{id}")
  public ResponseEntity<Void> excluir(HttpServletRequest pedido, @PathVariable String momento, @PathVariable long id) {
    modelos.excluir(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{momento}/{id}/historico/{versao}/restaurar")
  public ResponseEntity<ModelosEmailAssinatura.Detalhe> restaurar(HttpServletRequest pedido, @PathVariable String momento,
      @PathVariable long id, @PathVariable long versao) {
    return resposta(modelos.restaurar(acesso(pedido).empresa(), MomentoEmailAssinatura.de(momento), id, versao));
  }

  @PostMapping("/{momento}/previa")
  public ResponseEntity<Map<String, Object>> previa(HttpServletRequest pedido, @PathVariable String momento,
      @RequestBody ModelosEmailAssinatura.Modelo modelo) {
    acesso(pedido);
    var etapa = MomentoEmailAssinatura.de(momento);
    var limpo = ModelosEmailAssinatura.limpar(modelo, etapa, false);
    return resposta(Map.of("modelo", limpo, "mensagem", exemplo(limpo, etapa)));
  }

  @PostMapping("/{momento}/teste")
  public ResponseEntity<Map<String, String>> teste(HttpServletRequest pedido, @PathVariable String momento,
      @RequestBody ModelosEmailAssinatura.Modelo modelo) {
    acesso(pedido);
    var etapa = MomentoEmailAssinatura.de(momento);
    enviador.enviarTeste(exemplo(ModelosEmailAssinatura.limpar(modelo, etapa, true), etapa));
    return resposta(Map.of("destino", "teste-assinatura@example.test"));
  }

  private ResponseEntity<Map<String, Object>> editar(ModelosEmailAssinatura.Detalhe estado) {
    return resposta(Map.of("estado", estado, "testeDisponivel", enviador.testeDisponivel(),
        "variaveis", VariaveisEmailAssinatura.para(MomentoEmailAssinatura.de(estado.momento()))));
  }

  private static ModelosEmailAssinatura.Mensagem exemplo(ModelosEmailAssinatura.Modelo modelo, MomentoEmailAssinatura etapa) {
    return ModelosEmailAssinatura.renderizar(modelo, VariaveisEmailAssinatura.exemplo(etapa));
  }

  private Acesso acesso(HttpServletRequest pedido) {
    String ator = identidade.autenticar(pedido);
    var empresa = identidade.empresaParaAdministrar(ator);
    return new Acesso(BancoAssineai.id(empresa, "EmpresaId"), ator);
  }

  private static <T> ResponseEntity<T> resposta(T corpo) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corpo);
  }

  private record Acesso(long empresa, String ator) {}
}
