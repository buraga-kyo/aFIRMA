package com.afirma.assina;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentidadeAssineai {

  private final BancoAssineai banco;
  private final JwtDecoder atual;
  private final String segredo;
  private final long empresaLegada;
  private final BCryptPasswordEncoder senhas = new BCryptPasswordEncoder(12);

  public IdentidadeAssineai(
    BancoAssineai banco,
    JwtDecoder atual,
    @Value("${APP_ASSINEAI_JWT_SECRET:${JWT_SECRET:}}") String segredo,
    @Value("${APP_ASSINEAI_EMPRESA_LEGADA_ID:0}") long empresaLegada,
    org.springframework.core.env.Environment ambiente
  ) {
    this.banco = banco;
    this.atual = atual;
    this.segredo = !segredo.isBlank()
      ? segredo
      : ambiente.matchesProfiles("local-test")
        ? "afirma-local-test-compatibilidade-jwt-2026"
        : "";
    this.empresaLegada = empresaLegada;
  }

  public String autenticar(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) throw erro(
      HttpStatus.UNAUTHORIZED,
      "Informe o acesso da empresa"
    );
    String token = header.substring(7);
    try {
      Jwt jwt = atual.decode(token);
      String escopos = jwt.getClaimAsString("scope");
      if (
        escopos == null ||
        !Arrays.asList(escopos.split(" ")).contains("pdf.sign")
      ) throw erro(HttpStatus.FORBIDDEN, "Acesso sem permissão de assinatura");
      return "jwt:" + jwt.getIssuer() + ":" + jwt.getSubject();
    } catch (JwtException e) {
      if (segredo.isBlank()) throw erro(
        HttpStatus.UNAUTHORIZED,
        "Acesso inválido"
      );
      try {
        String email = TokensEmpresaAssineai.ler(token, segredo);
        if (
          banco.um(
            "SELECT e.\"EmpresaId\" FROM \"Empresas\" e LEFT JOIN firma_assineai_identidade i ON i.empresa_id=e.\"EmpresaId\" WHERE (lower(e.\"EmpresaEmail\")=lower(:email) OR i.identidade=:identidade) AND e.\"EmpresaSenha\" IS NOT NULL LIMIT 1",
            Map.of("email", email, "identidade", "empresa:" + email)
          ) == null
        ) throw erro(HttpStatus.UNAUTHORIZED, "Empresa inválida");
        return "empresa:" + email.toLowerCase(Locale.ROOT);
      } catch (Exception falha) {
        throw erro(HttpStatus.UNAUTHORIZED, "Acesso inválido ou expirado");
      }
    }
  }

  @Transactional
  public Map<String, Object> empresa(String identidade) {
    var vinculo = banco.um(
      "SELECT e.* FROM \"Empresas\" e JOIN firma_assineai_identidade i ON i.empresa_id=e.\"EmpresaId\" WHERE i.identidade=:i",
      Map.of("i", identidade)
    );
    if (vinculo != null) return publico(vinculo);
    String email = identidade.startsWith("empresa:")
      ? identidade.substring(8)
      : "";
    var anterior = email.isEmpty()
      ? null
      : banco.um(
          "SELECT * FROM \"Empresas\" WHERE lower(\"EmpresaEmail\")=lower(:email) ORDER BY \"EmpresaId\" LIMIT 1",
          Map.of("email", email)
        );
    long id;
    if (anterior != null) id = BancoAssineai.id(anterior, "EmpresaId");
    else {
      banco.um(
        "SELECT pg_advisory_xact_lock(hashtext(:i))",
        Map.of("i", identidade)
      );
      vinculo = banco.um(
        "SELECT e.* FROM \"Empresas\" e JOIN firma_assineai_identidade i ON i.empresa_id=e.\"EmpresaId\" WHERE i.identidade=:i",
        Map.of("i", identidade)
      );
      if (vinculo != null) return publico(vinculo);
      id = banco.inserir(
        "Empresas",
        "EmpresaId",
        new LinkedHashMap<>(
          Map.of(
            "EmpresaUsuario",
            identidade.substring(identidade.lastIndexOf(':') + 1),
            "EmpresaTemaTelaAssinatura",
            "firma"
          )
        )
      );
    }
    banco.executar(
      "INSERT INTO firma_assineai_identidade(identidade,empresa_id) VALUES(:i,:id) ON CONFLICT(identidade) DO NOTHING",
      Map.of("i", identidade, "id", id)
    );
    return publico(
      banco.um(
        "SELECT * FROM \"Empresas\" WHERE \"EmpresaId\"=:id",
        Map.of("id", id)
      )
    );
  }

  @Transactional
  public Map<String, Object> salvar(
    String identidade,
    Map<String, Object> entrada
  ) {
    var empresa = empresa(identidade);
    var dados = new LinkedHashMap<String, Object>();
    for (String campo : List.of(
      "EmpresaNome",
      "EmpresaCNPJ",
      "EmpresaEmail",
      "EmpresaTemaTelaAssinatura"
    )) {
      if (entrada.containsKey(campo)) {
        String valor = Objects.toString(entrada.get(campo), "").strip();
        if (valor.length() > 255) throw erro(
          HttpStatus.BAD_REQUEST,
          "Dados da empresa devem ter até 255 caracteres"
        );
        if (
          campo.equals("EmpresaEmail") &&
          !valor.isEmpty() &&
          !valor.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        ) throw erro(HttpStatus.BAD_REQUEST, "E-mail inválido");
        if (
          campo.equals("EmpresaTemaTelaAssinatura") &&
          !List.of(
            "firma",
            "Caderno",
            "Cor Solida",
            "caderno",
            "cor-solida"
          ).contains(valor)
        ) throw erro(HttpStatus.BAD_REQUEST, "Tema inválido");
        dados.put(campo, valor);
      }
    }
    banco.atualizar(
      "Empresas",
      "EmpresaId",
      BancoAssineai.id(empresa, "EmpresaId"),
      dados
    );
    return empresa(identidade);
  }

  @Transactional
  public void registrar(Map<String, Object> dados) {
    String email = Objects.toString(dados.get("email"), "")
      .strip()
      .toLowerCase(Locale.ROOT);
    String senha = Objects.toString(dados.get("password"), "");
    String usuario = Objects.toString(dados.get("username"), "").strip();
    if (
      !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
      email.length() > 254 ||
      senha.length() < 8 ||
      senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 ||
      usuario.isBlank() ||
      usuario.length() > 120
    ) throw erro(
      HttpStatus.BAD_REQUEST,
      "Informe usuário, e-mail e senha de 8 a 72 bytes"
    );
    banco.um(
      "SELECT pg_advisory_xact_lock(hashtext(:email))",
      Map.of("email", email)
    );
    if (
      banco.um(
        "SELECT \"EmpresaId\" FROM \"Empresas\" WHERE lower(\"EmpresaEmail\")=:email",
        Map.of("email", email)
      ) != null
    ) throw erro(HttpStatus.CONFLICT, "E-mail já cadastrado");
    banco.inserir(
      "Empresas",
      "EmpresaId",
      new LinkedHashMap<>(
        Map.of(
          "EmpresaUsuario",
          usuario,
          "EmpresaEmail",
          email,
          "EmpresaSenha",
          senhas.encode(senha),
          "EmpresaTemaTelaAssinatura",
          "firma"
        )
      )
    );
  }

  public Map<String, String> login(Map<String, Object> dados) {
    if (segredo.isBlank()) throw erro(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Configure APP_ASSINEAI_JWT_SECRET para o acesso da empresa"
    );
    String email = Objects.toString(dados.get("email"), "").strip();
    String senha = Objects.toString(dados.get("password"), "");
    var empresa = banco.um(
      "SELECT * FROM \"Empresas\" WHERE lower(\"EmpresaEmail\")=lower(:email) AND \"EmpresaSenha\" IS NOT NULL ORDER BY \"EmpresaId\" LIMIT 1",
      Map.of("email", email)
    );
    if (
      empresa == null ||
      senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 ||
      !senhas.matches(senha, BancoAssineai.texto(empresa, "EmpresaSenha"))
    ) throw erro(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos");
    try {
      return Map.of(
        "token",
        TokensEmpresaAssineai.emitir(email.toLowerCase(Locale.ROOT), segredo)
      );
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao emitir acesso", e);
    }
  }

  public boolean podeConsultarLegada(String identidade) {
    if (empresaLegada <= 0) return false;
    var e = banco.um(
      "SELECT \"EmpresaEmail\" FROM \"Empresas\" WHERE \"EmpresaId\"=:id",
      Map.of("id", empresaLegada)
    );
    return (
      e != null &&
      identidade.equals(
        "empresa:" +
          BancoAssineai.texto(e, "EmpresaEmail").toLowerCase(Locale.ROOT)
      )
    );
  }

  public String identidadeIntegracaoLegada() {
    if (empresaLegada <= 0) throw erro(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Configure a empresa proprietária dos registros legados"
    );
    var e = banco.um(
      "SELECT \"EmpresaEmail\" FROM \"Empresas\" WHERE \"EmpresaId\"=:id",
      Map.of("id", empresaLegada)
    );
    if (
      e == null || BancoAssineai.texto(e, "EmpresaEmail").isBlank()
    ) throw erro(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Empresa legada não encontrada"
    );
    return (
      "empresa:" +
      BancoAssineai.texto(e, "EmpresaEmail").toLowerCase(Locale.ROOT)
    );
  }

  private Map<String, Object> publico(Map<String, Object> empresa) {
    var copia = new LinkedHashMap<>(empresa);
    copia.remove("EmpresaSenha");
    return copia;
  }

  private ResponseStatusException erro(HttpStatus status, String mensagem) {
    return new ResponseStatusException(status, mensagem);
  }
}
