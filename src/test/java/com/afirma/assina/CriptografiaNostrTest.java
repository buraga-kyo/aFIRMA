package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import fr.acinq.secp256k1.Secp256k1;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CriptografiaNostrTest {
  final JsonMapper json = JsonMapper.builder().build();
  final CriptografiaNostr cripto = new CriptografiaNostr(json);
  final CifraConversaNostr cifra = new CifraConversaNostr(cripto);

  byte[] arquivo(String nome) throws Exception {
    return getClass().getResourceAsStream("/nostr/" + nome).readAllBytes();
  }

  byte[] bytes(JsonNode v, String nome) {
    return CriptografiaNostr.bytes(v.path(nome).asText());
  }

  @Test
  void confereVetoresOficiaisBip340ParaResumoDe32Bytes() throws Exception {
    String[] linhas = new String(arquivo("bip340-vetores.csv"), StandardCharsets.UTF_8).split("\n");
    for (int i = 1; i < linhas.length; i++) {
      String[] campos = linhas[i].strip().split(",", 8);
      // Nostr assina SHA-256; a API da biblioteca recebe apenas mensagens de 32 bytes.
      if (campos[4].length() != 64) continue;
      boolean valido = false;
      try {
        valido =
            Secp256k1.get()
                .verifySchnorr(
                    CriptografiaNostr.bytes(campos[5]),
                    CriptografiaNostr.bytes(campos[4]),
                    CriptografiaNostr.bytes(campos[2]));
      } catch (RuntimeException falha) {
      }
      assertEquals(campos[6].equals("TRUE"), valido, "vetor " + campos[0]);
      if (!campos[1].isBlank())
        assertEquals(
            campos[5].toLowerCase(),
            CriptografiaNostr.hex(
                Secp256k1.get()
                    .signSchnorr(
                        CriptografiaNostr.bytes(campos[4]),
                        CriptografiaNostr.bytes(campos[1]),
                        CriptografiaNostr.bytes(campos[3]))));
    }
  }

  @Test
  void cifraEAbreOsVetoresOficiaisNip44() throws Exception {
    var validos = json.readTree(arquivo("nip44-vetores.json")).path("v2").path("valid");
    for (var v : validos.path("get_conversation_key"))
      assertEquals(
          v.path("conversation_key").asText(),
          CriptografiaNostr.hex(cifra.chaveDaConversa(bytes(v, "sec1"), v.path("pub2").asText())));
    var mensagens = validos.path("get_message_keys");
    for (var v : mensagens.path("keys"))
      assertArrayEquals(
          CriptografiaNostr.juntar(
              bytes(v, "chacha_key"), bytes(v, "chacha_nonce"), bytes(v, "hmac_key")),
          cifra.chavesDaMensagem(bytes(mensagens, "conversation_key"), bytes(v, "nonce")));
    for (var v : validos.path("calc_padded_len"))
      assertEquals(v.get(1).asInt(), cifra.tamanhoComFolga(v.get(0).asInt()));
    for (var v : validos.path("encrypt_decrypt")) {
      String texto = v.path("plaintext").asText(), pacote = v.path("payload").asText();
      assertEquals(pacote, cifra.cifrar(texto, bytes(v, "conversation_key"), bytes(v, "nonce")));
      assertEquals(texto, cifra.abrir(pacote, bytes(v, "conversation_key")));
    }
    for (var v : validos.path("encrypt_decrypt_long_msg")) {
      String texto = v.path("pattern").asText().repeat(v.path("repeat").asInt());
      if (texto.getBytes(StandardCharsets.UTF_8).length > 65535) continue;
      String pacote = cifra.cifrar(texto, bytes(v, "conversation_key"), bytes(v, "nonce"));
      assertEquals(
          v.path("payload_sha256").asText(),
          CriptografiaNostr.hex(CriptografiaNostr.sha256(pacote.getBytes(StandardCharsets.UTF_8))));
      assertEquals(texto, cifra.abrir(pacote, bytes(v, "conversation_key")));
    }
  }

  @Test
  void recusaOsVetoresInvalidosNip44() throws Exception {
    var invalidos = json.readTree(arquivo("nip44-vetores.json")).path("v2").path("invalid");
    for (var v : invalidos.path("get_conversation_key"))
      assertThrows(
          RuntimeException.class,
          () -> cifra.chaveDaConversa(bytes(v, "sec1"), v.path("pub2").asText()));
    for (var v : invalidos.path("decrypt"))
      assertThrows(
          RuntimeException.class,
          () -> cifra.abrir(v.path("payload").asText(), bytes(v, "conversation_key")),
          v.path("note").asText());
    assertThrows(RuntimeException.class, () -> cifra.cifrar("", new byte[32]));
    assertThrows(RuntimeException.class, () -> cifra.cifrar("x".repeat(65536), new byte[32]));
  }

  @Test
  void confereEventosGeradosPeloFrontendInclusiveUnicodeEControles() throws Exception {
    List<Map<String, Object>> eventos =
        json.readValue(arquivo("eventos-nostr-tools.json"), List.class);
    for (var evento : eventos) {
      assertEquals(evento.get("id"), cripto.conferirEvento(evento).get("id"));
      evento.put("content", evento.get("content") + "alterado");
      assertThrows(IllegalArgumentException.class, () -> cripto.conferirEvento(evento));
    }
  }
}
