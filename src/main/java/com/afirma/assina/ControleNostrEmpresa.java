package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assineai/nostr")
public class ControleNostrEmpresa {
  private final IdentidadeAssineai identidade;
  private final ServicoNostrEmpresa servico;

  public ControleNostrEmpresa(IdentidadeAssineai identidade, ServicoNostrEmpresa servico) {
    this.identidade = identidade;
    this.servico = servico;
  }

  private Acesso acesso(HttpServletRequest r, boolean administrador) {
    String ator = identidade.autenticar(r);
    var empresa =
        administrador ? identidade.empresaParaAdministrar(ator) : identidade.empresa(ator);
    return new Acesso(BancoAssineai.id(empresa, "EmpresaId"), ator);
  }

  @GetMapping("/estado")
  public Map<String, Object> estado(HttpServletRequest r) {
    return servico.estado(acesso(r, false).empresa());
  }

  @GetMapping("/identidades")
  public Map<String, Object> identidades(HttpServletRequest r) {
    return servico.identidades(acesso(r, true).empresa());
  }

  @PutMapping("/configuracao")
  public Map<String, Object> salvar(@RequestBody Configuracao p, HttpServletRequest r) {
    var a = acesso(r, true);
    return servico.salvar(
        a.empresa(),
        a.ator(),
        p.referencia(),
        p.nome(),
        p.sobre(),
        Boolean.TRUE.equals(p.ativa()),
        p.autorizacao());
  }

  @PostMapping("/pausar")
  public ResponseEntity<Void> pausar(HttpServletRequest r) {
    var a = acesso(r, true);
    servico.pausar(a.empresa(), a.ator());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/publicacoes")
  public Map<String, Object> publicar(@RequestBody Prova p, HttpServletRequest r) {
    var a = acesso(r, false);
    return Map.of("id", servico.enfileirar(a.empresa(), a.ator(), p.evento()));
  }

  @GetMapping("/publicacoes")
  public List<Map<String, Object>> listar(HttpServletRequest r) {
    return servico.listar(acesso(r, false).empresa());
  }

  @PostMapping("/publicacoes/{id}/tentar")
  public Map<String, Object> tentar(@PathVariable UUID id, HttpServletRequest r) {
    servico.tentarNovamente(acesso(r, true).empresa(), id);
    return Map.of("id", id);
  }

  @GetMapping("/publicacoes/{id}/pacote")
  public Map<String, Object> pacote(@PathVariable UUID id, HttpServletRequest r) {
    return servico.pacote(acesso(r, false).empresa(), id);
  }

  @GetMapping("/publicacoes/{id}/evento")
  public ResponseEntity<byte[]> evento(@PathVariable UUID id, HttpServletRequest r) {
    return arquivo(
        servico.evento(acesso(r, false).empresa(), id),
        "evento-nostr.json",
        MediaType.APPLICATION_JSON);
  }

  @GetMapping("/publicacoes/{id}/ots")
  public ResponseEntity<byte[]> ots(@PathVariable UUID id, HttpServletRequest r) {
    return arquivo(
        servico.ots(acesso(r, false).empresa(), id),
        "evento-nostr.json.ots",
        MediaType.APPLICATION_OCTET_STREAM);
  }

  private ResponseEntity<byte[]> arquivo(byte[] dados, String nome, MediaType tipo) {
    return ResponseEntity.ok()
        .contentType(tipo)
        .header("Cache-Control", "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(nome, StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(dados);
  }

  public record Configuracao(
      String referencia, String nome, String sobre, Boolean ativa, String autorizacao) {}

  public record Prova(Map<String, Object> evento) {}

  private record Acesso(long empresa, String ator) {}
}
