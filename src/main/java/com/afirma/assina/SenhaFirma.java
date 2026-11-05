package com.afirma.assina;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** O login confere a senha com PBKDF2-HMAC-SHA256. O sal fica separado do resumo da senha. */
public final class SenhaFirma {
  private SenhaFirma() {}

  public record Credencial(String hash, String sal) {}

  public static Credencial criar(String senha) {
    byte[] sal = new byte[16];
    new SecureRandom().nextBytes(sal);
    return new Credencial(
        Base64.getEncoder().encodeToString(derivar(senha, sal)),
        Base64.getEncoder().encodeToString(sal));
  }

  private static byte[] derivar(String senha, byte[] sal) {
    var spec = new PBEKeySpec(senha.toCharArray(), sal, 310000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception erro) {
      throw new IllegalStateException("PBKDF2 indisponível", erro);
    } finally {
      spec.clearPassword();
    }
  }

  public static boolean verificar(String senha, String hash, String sal) {
    try {
      if (hash.startsWith("$2"))
        return senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && new BCryptPasswordEncoder().matches(senha, hash);
      byte[] valor = Base64.getDecoder().decode(sal), cabecalho = Base64.getDecoder().decode(hash);
      return valor.length == 16
          && cabecalho.length == 32
          && MessageDigest.isEqual(cabecalho, derivar(senha, valor));
    } catch (IllegalArgumentException erro) {
      return false;
    }
  }
}
