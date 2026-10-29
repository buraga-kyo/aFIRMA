package com.afirma.assina;

import com.nimbusds.jose.*;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TokensEmpresaAssineai {

  private TokensEmpresaAssineai() {}

  private static byte[] assinar(byte[] dados, String segredo) throws Exception {
    if (segredo.isBlank()) throw new IllegalArgumentException(
      "Segredo não configurado"
    );
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(
      new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
    );
    return mac.doFinal(dados);
  }

  public static String emitir(String email, String segredo) throws Exception {
    var jwt = new SignedJWT(
      new JWSHeader(JWSAlgorithm.HS256),
      new JWTClaimsSet.Builder()
        .claim("id", email)
        .issueTime(new Date())
        .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
        .build()
    );
    return (
      jwt.getHeader().toBase64URL() +
      "." +
      jwt.getPayload().toBase64URL() +
      "." +
      Base64URL.encode(assinar(jwt.getSigningInput(), segredo))
    );
  }

  public static String ler(String token, String segredo) throws Exception {
    if (token.length() > 8192) throw new IllegalArgumentException(
      "Token inválido"
    );
    var jwt = SignedJWT.parse(token);
    if (
      !JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) ||
      !MessageDigest.isEqual(
        jwt.getSignature().decode(),
        assinar(jwt.getSigningInput(), segredo)
      )
    ) throw new IllegalArgumentException("Token inválido");
    var claims = jwt.getJWTClaimsSet();
    if (
      claims.getExpirationTime() == null ||
      !claims.getExpirationTime().toInstant().isAfter(Instant.now()) ||
      (claims.getNotBeforeTime() != null &&
        claims.getNotBeforeTime().toInstant().isAfter(Instant.now()))
    ) throw new IllegalArgumentException("Token expirado ou ainda não válido");
    String email = claims.getStringClaim("id");
    if (
      email == null || email.isBlank() || email.length() > 254
    ) throw new IllegalArgumentException("Empresa inválida");
    return email.toLowerCase(Locale.ROOT);
  }
}
