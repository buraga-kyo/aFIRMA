package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/** O login entrega o token que IdentidadeAssineai confere nas próximas chamadas da API. */
@RestController
@RequestMapping("/api/firma")
public class AutenticacaoFirma {
  private final BancoAssineai banco;
  private final IdentidadeAssineai identidade;
  private final String segredo;
  private final ContaFirma conta;
  private final ServicoFirma servico;

  public AutenticacaoFirma(
      BancoAssineai banco,
      IdentidadeAssineai identidade,
      @Value("${APP_ASSINEAI_JWT_SECRET:${JWT_SECRET:}}") String segredo,
      Environment ambiente,
      ContaFirma conta,
      ServicoFirma servico) {
    this.conta = conta;
    this.servico = servico;
    this.banco = banco;
    this.identidade = identidade;
    this.segredo =
        segredo.isBlank() && ambiente.matchesProfiles("local-test")
            ? "afirma-local-test-compatibilidade-jwt-2026"
            : segredo;
  }

  @PostMapping("/login")
  public Object entrar(@RequestBody Map<String, Object> dados, HttpServletRequest requisicao)
      throws Exception {
    String email = Objects.toString(dados.get("email"), "").strip(),
        senha = Objects.toString(dados.get("password"), "");
    if (email.length() > 254 || senha.length() > 256)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credenciais inválidas");
    try {
      if (senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72)
        return identidade.login(dados);
    } catch (ResponseStatusException erro) {
      if (erro.getStatusCode().value() != 401) throw erro;
    }
    if (segredo.isBlank())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Acesso não configurado");
    var usuarios =
        banco.listar(
            "SELECT u.* FROM firma_identidade.\"Usuario\" u JOIN firma.\"Cliente\" c ON c.id=u.\"UsuarioClienteId\" WHERE lower(u.\"UsuarioEmail\")=lower(:email) AND NOT COALESCE(u.\"UsuarioBloqueado\",false) AND COALESCE(c.\"ClienteStatus\",true)",
            Map.of("email", email));
    var validos =
        usuarios.stream()
            .filter(usuario -> {
              return SenhaFirma.verificar(
                  senha,
                  Objects.toString(usuario.get("UsuarioSenha"), ""),
                  Objects.toString(usuario.get("UsuarioRandomBytes"), ""));
            })
            .toList();
    if (validos.size() != 1)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
    var usuario = validos.getFirst();
    String token = TokensEmpresaAssineai.emitir("usuario:" + usuario.get("id"), segredo);
    banco.executar(
        "INSERT INTO firma_identidade.\"Sessao\"(\"UsuarioId\",\"RepositorioId\",\"SessaoJWT\",\"SessaoStatus\",\"SessaoTipoAutenticacao\",\"SessaoCriacao\",\"SessaoUltimoAcesso\",\"SessaoIP\",\"createdAt\",\"updatedAt\") VALUES(:u,:r,:token,'A','local',now(),now(),:ip,now(),now())",
        Map.of(
            "u",
            usuario.get("id"),
            "r",
            usuario.get("RepositorioId"),
            "token",
            token,
            "ip",
            requisicao.getRemoteAddr()));
    return Map.of("token", token);
  }

  @PostMapping("/logout")
  public void sair(HttpServletRequest requisicao) {
    identidade.autenticar(requisicao);
    String token = requisicao.getHeader("Authorization").substring(7);
    banco.executar(
        "INSERT INTO firma_relacionamento_token_revogado(resumo) VALUES(:resumo) ON CONFLICT DO NOTHING",
        Map.of("resumo", resumo(token)));
    banco.executar(
        "UPDATE firma_identidade.\"Sessao\" SET \"SessaoStatus\"='F',\"updatedAt\"=now() WHERE \"SessaoJWT\"=:t",
        Map.of("t", token));
  }

  static String resumo(String token) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException erro) {
      throw new IllegalStateException(erro);
    }
  }

  @PostMapping("/recuperar-senha")
  public Object recuperar(@RequestBody Map<String, Object> dados) {
    conta.solicitar(Objects.toString(dados.get("email"), ""));
    return Map.of("mensagem", "Se houver uma conta elegível, enviaremos o código por e-mail.");
  }

  @PostMapping("/redefinir-senha")
  public void redefinir(@RequestBody Map<String, Object> dados) {
    conta.redefinir(
        Objects.toString(dados.get("email"), ""),
        Objects.toString(dados.get("codigo"), ""),
        Objects.toString(dados.get("senha"), ""));
  }

  @PutMapping("/conta/senha")
  public void senha(@RequestBody Map<String, Object> dados, HttpServletRequest requisicao) {
    conta.senha(
        servico.contexto(identidade.autenticar(requisicao)),
        Objects.toString(dados.get("atual"), ""),
        Objects.toString(dados.get("nova"), ""));
  }
}
