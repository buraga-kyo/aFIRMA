package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assineai/nostr/conexoes")
public class ControleConexoesBlockchain {
  private final IdentidadeAssineai identidade;
  private final ConfiguracaoBlockchainEmpresa configuracao;
  private final CarimboNostr carimbo;
  private final ServicoPublicacaoNostr publicacoes;

  public ControleConexoesBlockchain(
      IdentidadeAssineai identidade,
      ConfiguracaoBlockchainEmpresa configuracao,
      CarimboNostr carimbo,
      ServicoPublicacaoNostr publicacoes) {
    this.identidade = identidade;
    this.configuracao = configuracao;
    this.carimbo = carimbo;
    this.publicacoes = publicacoes;
  }

  private Acesso acesso(HttpServletRequest requisicao) {
    String ator = identidade.autenticar(requisicao);
    return new Acesso(BancoAssineai.id(identidade.empresaParaAdministrar(ator), "EmpresaId"), ator);
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> estado(HttpServletRequest r) {
    return resposta(configuracao.estado(acesso(r).empresa));
  }

  @PutMapping("/nostr")
  public ResponseEntity<Map<String, Object>> nostr(@RequestBody Nostr p, HttpServletRequest r) {
    var a = acesso(r);
    return resposta(
        configuracao.salvarNostr(
            a.empresa, a.ator, p.referencia, p.chavePublica, p.bunker, p.relays));
  }

  @PutMapping("/bitcoin")
  public ResponseEntity<Map<String, Object>> bitcoin(@RequestBody Bitcoin p, HttpServletRequest r) {
    var a = acesso(r);
    return resposta(
        configuracao.salvarBitcoin(a.empresa, a.ator, p.endereco, p.usuario, p.senha, p.remover));
  }

  @PostMapping("/nostr/testar")
  public ResponseEntity<Map<String, Object>> testarNostr(
      @RequestBody Teste p, HttpServletRequest r) {
    var a = acesso(r);
    return resposta(publicacoes.conferirIdentidade(a.empresa, p.referencia));
  }

  @PostMapping("/bitcoin/testar")
  public ResponseEntity<Map<String, Object>> testarBitcoin(HttpServletRequest r) {
    var a = acesso(r);
    try {
      return resposta(carimbo.testarBitcoin(a.empresa));
    } catch (Exception falha) {
      if (falha instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Não foi possível consultar o Bitcoin Core. Confira endereço, credenciais e"
              + " disponibilidade.");
    }
  }

  private ResponseEntity<Map<String, Object>> resposta(Map<String, Object> dados) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dados);
  }

  public record Nostr(String referencia, String chavePublica, String bunker, List<String> relays) {
    @Override
    public String toString() {
      return "Configuração privada Nostr";
    }
  }

  public record Bitcoin(String endereco, String usuario, String senha, boolean remover) {
    @Override
    public String toString() {
      return "Configuração privada Bitcoin Core";
    }
  }

  public record Teste(String referencia) {}

  private record Acesso(long empresa, String ator) {}
}
