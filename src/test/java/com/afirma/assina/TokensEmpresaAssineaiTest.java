package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;

class TokensEmpresaAssineaiTest {

  @Test
  void preservaClaimIdEAlgoritmoDoJsonwebtoken() throws Exception {
    String token = TokensEmpresaAssineai.emitir(
      "empresa@example.com",
      "segredo-antigo"
    );
    assertEquals(
      "empresa@example.com",
      TokensEmpresaAssineai.ler(token, "segredo-antigo")
    );
    assertEquals(
      JWSAlgorithm.HS256,
      SignedJWT.parse(token).getHeader().getAlgorithm()
    );
    assertEquals(
      "empresa@example.com",
      SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("id")
    );
  }

  @Test
  void rejeitaChaveIncorretaEPayloadAlterado() throws Exception {
    String token = TokensEmpresaAssineai.emitir(
      "empresa@example.com",
      "segredo-antigo"
    );
    assertThrows(Exception.class, () ->
      TokensEmpresaAssineai.ler(token, "outra-chave")
    );
    String[] partes = token.split("\\.");
    String modificado =
      partes[0] +
      "." +
      Base64URL.encode("{\"id\":\"outra@example.com\",\"exp\":9999999999}") +
      "." +
      partes[2];
    assertThrows(Exception.class, () ->
      TokensEmpresaAssineai.ler(modificado, "segredo-antigo")
    );
  }

  @Test
  void naoAceitaTokenSemAssinatura() {
    String token = new PlainJWT(
      new JWTClaimsSet.Builder().claim("id", "empresa@example.com").build()
    ).serialize();
    assertThrows(Exception.class, () ->
      TokensEmpresaAssineai.ler(token, "segredo-antigo")
    );
  }
}
