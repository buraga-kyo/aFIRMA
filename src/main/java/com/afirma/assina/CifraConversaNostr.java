package com.afirma.assina;

import fr.acinq.secp256k1.Secp256k1;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class CifraConversaNostr {
  private final CriptografiaNostr criptografia;

  public CifraConversaNostr(CriptografiaNostr criptografia) {
    this.criptografia = criptografia;
  }

  byte[] chaveDaConversa(byte[] chaveCliente, String chaveDoAssinador) {
    byte[] ponto =
        Secp256k1.get()
            .pubKeyTweakMul(
                CriptografiaNostr.juntar(new byte[] {2}, CriptografiaNostr.bytes(chaveDoAssinador)),
                chaveCliente);
    // NIP-44 usa a coordenada X sem hash. O método ECDH genérico da biblioteca devolve outro
    // formato.
    return hmac("nip44-v2".getBytes(StandardCharsets.UTF_8), Arrays.copyOfRange(ponto, 1, 33));
  }

  static byte[] hmac(byte[] chave, byte[] dados) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(chave, "HmacSHA256"));
      return mac.doFinal(dados);
    } catch (Exception falha) {
      throw new IllegalStateException("HMAC indisponível");
    }
  }

  byte[] chavesDaMensagem(byte[] conversa, byte[] nonce) {
    if (conversa.length != 32 || nonce.length != 32) throw CriptografiaNostr.invalido();
    byte[] anterior = new byte[0], resultado = new byte[0];
    for (int rodada = 1; resultado.length < 76; rodada++) {
      anterior =
          hmac(conversa, CriptografiaNostr.juntar(anterior, nonce, new byte[] {(byte) rodada}));
      resultado = CriptografiaNostr.juntar(resultado, anterior);
    }
    return Arrays.copyOf(resultado, 76);
  }

  int tamanhoComFolga(int tamanho) {
    if (tamanho < 1 || tamanho > 65536) throw CriptografiaNostr.invalido();
    if (tamanho <= 32) return 32;
    int potencia = Integer.highestOneBit(tamanho - 1) << 1;
    int bloco = potencia <= 256 ? 32 : potencia / 8;
    return bloco * ((tamanho - 1) / bloco + 1);
  }

  public String cifrar(String texto, byte[] conversa) {
    return cifrar(texto, conversa, criptografia.sortear(32));
  }

  String cifrar(String texto, byte[] conversa, byte[] nonce) {
    byte[] limpo = texto.getBytes(StandardCharsets.UTF_8);
    if (limpo.length < 1 || limpo.length > 65535) throw CriptografiaNostr.invalido();
    byte[] acolchoado = new byte[tamanhoComFolga(limpo.length) + 2];
    acolchoado[0] = (byte) (limpo.length >>> 8);
    acolchoado[1] = (byte) limpo.length;
    System.arraycopy(limpo, 0, acolchoado, 2, limpo.length);
    byte[] chaves = chavesDaMensagem(conversa, nonce);
    byte[] cifrado = chacha(acolchoado, chaves);
    byte[] autenticacao =
        hmac(Arrays.copyOfRange(chaves, 44, 76), CriptografiaNostr.juntar(nonce, cifrado));
    return Base64.getEncoder()
        .encodeToString(CriptografiaNostr.juntar(new byte[] {2}, nonce, cifrado, autenticacao));
  }

  public String abrir(String pacote, byte[] conversa) {
    if (pacote == null || pacote.length() < 132 || pacote.length() > 87472)
      throw CriptografiaNostr.invalido();
    byte[] dados = Base64.getDecoder().decode(pacote);
    if (dados.length < 99 || dados.length > 65603 || dados[0] != 2)
      throw CriptografiaNostr.invalido();
    byte[] nonce = Arrays.copyOfRange(dados, 1, 33),
        cifrado = Arrays.copyOfRange(dados, 33, dados.length - 32);
    byte[] chaves = chavesDaMensagem(conversa, nonce);
    byte[] esperado =
        hmac(Arrays.copyOfRange(chaves, 44, 76), CriptografiaNostr.juntar(nonce, cifrado));
    // Confere o MAC antes de abrir o conteúdo, sem comparar byte a byte com saída antecipada.
    if (!MessageDigest.isEqual(
        esperado, Arrays.copyOfRange(dados, dados.length - 32, dados.length)))
      throw CriptografiaNostr.invalido();
    byte[] aberto = chacha(cifrado, chaves);
    int tamanho = ((aberto[0] & 255) << 8) | (aberto[1] & 255);
    if (aberto.length != tamanhoComFolga(tamanho) + 2 || tamanho + 2 > aberto.length)
      throw CriptografiaNostr.invalido();
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(aberto, 2, tamanho))
          .toString();
    } catch (CharacterCodingException falha) {
      throw CriptografiaNostr.invalido();
    }
  }

  private byte[] chacha(byte[] dados, byte[] chaves) {
    try {
      var cifra = Cipher.getInstance("ChaCha20");
      cifra.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(Arrays.copyOf(chaves, 32), "ChaCha20"),
          new ChaCha20ParameterSpec(Arrays.copyOfRange(chaves, 32, 44), 0));
      return cifra.doFinal(dados);
    } catch (Exception falha) {
      throw new IllegalStateException("ChaCha20 indisponível");
    }
  }
}
