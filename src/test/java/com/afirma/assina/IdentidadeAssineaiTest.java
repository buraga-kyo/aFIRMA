package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;

class IdentidadeAssineaiTest {

  @Test
  void autenticaJwtDoNodeEHashBcrypt2b() throws Exception {
    var banco = mock(BancoAssineai.class);
    var decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString())).thenThrow(
      new JwtException("emissor diferente")
    );
    String hash = new BCryptPasswordEncoder(4)
      .encode("senha-teste")
      .replace("$2a$", "$2b$");
    when(banco.um(anyString(), anyMap())).thenReturn(
      new LinkedHashMap<>(
        Map.of(
          "EmpresaId",
          71,
          "EmpresaEmail",
          "empresa@example.com",
          "EmpresaSenha",
          hash
        )
      )
    );
    var identidade = new IdentidadeAssineai(
      banco,
      decoder,
      "segredo-antigo",
      71,
      new MockEnvironment()
    );
    String token = identidade
      .login(Map.of("email", "empresa@example.com", "password", "senha-teste"))
      .get("token");
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    assertEquals("empresa:empresa@example.com", identidade.autenticar(request));
    assertTrue(identidade.podeConsultarLegada("empresa:empresa@example.com"));
    assertFalse(identidade.podeConsultarLegada("empresa:outra@example.com"));
  }

  @Test
  void impedeSenhaIncorretaEOperacaoSemAcesso() {
    var banco = mock(BancoAssineai.class);
    var identidade = new IdentidadeAssineai(
      banco,
      mock(JwtDecoder.class),
      "segredo",
      0,
      new MockEnvironment()
    );
    when(banco.um(anyString(), anyMap())).thenReturn(
      new LinkedHashMap<>(
        Map.of("EmpresaSenha", new BCryptPasswordEncoder(4).encode("correta"))
      )
    );
    assertThrows(ResponseStatusException.class, () ->
      identidade.login(
        Map.of("email", "empresa@example.com", "password", "incorreta")
      )
    );
    assertThrows(ResponseStatusException.class, () ->
      identidade.autenticar(new MockHttpServletRequest())
    );
  }

  @Test
  void tokenAtualSemEscopoNaoGanhaAcessoPorCompatibilidade() {
    var banco = mock(BancoAssineai.class);
    var decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString())).thenReturn(
      Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .claim("scope", "chat.use")
        .subject("pessoa")
        .build()
    );
    var identidade = new IdentidadeAssineai(
      banco,
      decoder,
      "segredo",
      0,
      new MockEnvironment()
    );
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token");
    var erro = assertThrows(ResponseStatusException.class, () ->
      identidade.autenticar(request)
    );
    assertEquals(403, erro.getStatusCode().value());
    verifyNoInteractions(banco);
  }

  @Test
  void mantemVinculoDaSessaoQuandoEmailDaEmpresaMuda() throws Exception {
    var banco = mock(BancoAssineai.class);
    var decoder = mock(JwtDecoder.class);
    when(decoder.decode(anyString())).thenThrow(
      new JwtException("emissor diferente")
    );
    when(
      banco.um(
        contains("firma_assineai_identidade"),
        argThat(m -> "empresa:antigo@example.com".equals(m.get("identidade")))
      )
    ).thenReturn(Map.of("EmpresaId", 71));
    var identidade = new IdentidadeAssineai(
      banco,
      decoder,
      "segredo-antigo",
      0,
      new MockEnvironment()
    );
    var request = new MockHttpServletRequest();
    request.addHeader(
      "Authorization",
      "Bearer " +
        TokensEmpresaAssineai.emitir("antigo@example.com", "segredo-antigo")
    );
    assertEquals("empresa:antigo@example.com", identidade.autenticar(request));
  }
}
