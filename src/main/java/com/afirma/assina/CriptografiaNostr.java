package com.afirma.assina;

import fr.acinq.secp256k1.Secp256k1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CriptografiaNostr {
  static final String PROTOCOLO_ACEITE = "afirma.nostr.aceite.v1";
  static final String PROTOCOLO_PUBLICO = "afirma.nostr.comprovante.v1";
  static final String TEXTO_ACEITE =
      "Li este PDF e concordo com o seu conteúdo. Autorizo registrar meu aceite com esta chave"
          + " Nostr e com o papel informado.";
  private final ObjectMapper json;
  private final SecureRandom sorteio = new SecureRandom();

  public CriptografiaNostr(ObjectMapper json) {
    this.json = json;
  }

  static byte[] bytes(String hexadecimal) {
    return HexFormat.of().parseHex(hexadecimal);
  }

  static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  static boolean hexadecimal(Object valor, int tamanho) {
    return valor instanceof String texto && texto.length() == tamanho && texto.matches("[0-9a-f]+");
  }

  static long inteiro(Object valor) {
    if (!(valor instanceof Number numero)) throw invalido();
    long inteiro = numero.longValue();
    if (numero.doubleValue() != inteiro || inteiro < 0 || inteiro > 9007199254740991L)
      throw invalido();
    return inteiro;
  }

  static byte[] juntar(byte[]... partes) {
    int tamanho = Arrays.stream(partes).mapToInt(p -> p.length).sum();
    byte[] tudo = new byte[tamanho];
    int posicao = 0;
    for (byte[] parte : partes) {
      System.arraycopy(parte, 0, tudo, posicao, parte.length);
      posicao += parte.length;
    }
    return tudo;
  }

  static byte[] sha256(byte[] dados) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(dados);
    } catch (java.security.NoSuchAlgorithmException falha) {
      throw new IllegalStateException("SHA-256 indisponível");
    }
  }

  byte[] sortear(int tamanho) {
    byte[] bytes = new byte[tamanho];
    sorteio.nextBytes(bytes);
    return bytes;
  }

  byte[] criarChaveCliente() {
    byte[] chave;
    do {
      chave = sortear(32);
    } while (!Secp256k1.get().secKeyVerify(chave));
    return chave;
  }

  String chavePublica(byte[] chave) {
    return hex(Arrays.copyOfRange(Secp256k1.get().pubkeyCreate(chave), 1, 33));
  }

  byte[] resumoEvento(Map<String, ?> evento) {
    Object tags = evento.get("tags");
    if (!(tags instanceof List<?> lista) || lista.size() > 1000) throw invalido();
    for (Object tag : lista) {
      if (!(tag instanceof List<?> itens)
          || itens.size() > 100
          || itens.stream().anyMatch(i -> !(i instanceof String))) throw invalido();
    }
    if (!hexadecimal(evento.get("pubkey"), 64)
        || !(evento.get("content") instanceof String texto)
        || texto.length() > 32768) throw invalido();
    long tipo = inteiro(evento.get("kind"));
    if (tipo > 65535) throw invalido();
    // O NIP-01 assina esta lista exata. A ordem dos campos do objeto recebido não entra no resumo.
    String serializado =
        serializarNip01(
            List.of(0, evento.get("pubkey"), inteiro(evento.get("created_at")), tipo, tags, texto));
    return sha256(serializado.getBytes(StandardCharsets.UTF_8));
  }

  private String serializarNip01(Object valor) {
    if (valor instanceof List<?> lista)
      return "[" + String.join(",", lista.stream().map(this::serializarNip01).toList()) + "]";
    if (valor instanceof Number numero) return Long.toString(numero.longValue());
    String texto = (String) valor;
    var saida = new StringBuilder("\"");
    for (int i = 0; i < texto.length(); i++) {
      char letra = texto.charAt(i);
      switch (letra) {
        case '"' -> saida.append("\\\"");
        case '\\' -> saida.append("\\\\");
        case '\b' -> saida.append("\\b");
        case '\f' -> saida.append("\\f");
        case '\n' -> saida.append("\\n");
        case '\r' -> saida.append("\\r");
        case '\t' -> saida.append("\\t");
        default -> {
          if (Character.isHighSurrogate(letra)
              && i + 1 < texto.length()
              && Character.isLowSurrogate(texto.charAt(i + 1)))
            saida.append(letra).append(texto.charAt(++i));
          else if (letra < 32 || Character.isSurrogate(letra))
            saida.append(String.format("\\u%04x", (int) letra));
          else saida.append(letra);
        }
      }
    }
    return saida.append('"').toString();
  }

  public Map<String, Object> conferirEvento(Map<String, ?> bruto) {
    if (bruto == null || !hexadecimal(bruto.get("id"), 64) || !hexadecimal(bruto.get("sig"), 128))
      throw invalido();
    Map<String, Object> evento = new LinkedHashMap<>(bruto);
    byte[] resumo = resumoEvento(evento);
    try {
      if (!MessageDigest.isEqual(resumo, bytes(evento.get("id").toString()))
          || !Secp256k1.get()
              .verifySchnorr(
                  bytes(evento.get("sig").toString()),
                  resumo,
                  bytes(evento.get("pubkey").toString()))) throw invalido();
    } catch (RuntimeException falha) {
      throw invalido();
    }
    return evento;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> conferirAceite(Map<String, ?> bruto) {
    var evento = conferirEvento(bruto);
    Map<?, ?> aceite;
    try {
      aceite = json.readValue(evento.get("content").toString(), Map.class);
    } catch (RuntimeException falha) {
      throw invalido();
    }
    if (aceite == null) throw invalido();
    if (inteiro(evento.get("kind")) != 1
        || !PROTOCOLO_ACEITE.equals(aceite.get("protocolo"))
        || !"aceitar-documento".equals(aceite.get("acao"))
        || !"SHA-256".equals(aceite.get("algoritmo"))
        || !hexadecimal(aceite.get("hashPdf"), 64)
        || !hexadecimal(aceite.get("desafio"), 64)
        || inteiro(aceite.get("tamanhoPdf")) < 5
        || inteiro(aceite.get("tamanhoPdf")) > 20 * 1024 * 1024
        || !(aceite.get("papel") instanceof String papel)
        || papel.isBlank()
        || papel.length() > 80
        || !TEXTO_ACEITE.equals(aceite.get("textoAceite"))) throw invalido();
    return evento;
  }

  String compromisso(String eventoId, String sal) {
    if (!hexadecimal(eventoId, 64) || !hexadecimal(sal, 64)) throw invalido();
    return hex(
        sha256(
            juntar(
                (PROTOCOLO_PUBLICO + "\0").getBytes(StandardCharsets.UTF_8),
                bytes(sal),
                bytes(eventoId))));
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> montarEvento(Map<String, ?> pedido) {
    long quando = inteiro(pedido.get("criadoEm"));
    if ("PERFIL".equals(pedido.get("tipo"))) {
      if (!(pedido.get("nome") instanceof String nome)
          || nome.isBlank()
          || nome.length() > 100
          || !(pedido.get("sobre") instanceof String sobre)
          || sobre.length() > 500) throw invalido();
      var perfil = new LinkedHashMap<String, Object>();
      perfil.put("name", nome.trim());
      perfil.put("about", sobre);
      return new LinkedHashMap<>(
          Map.of(
              "kind",
              0,
              "created_at",
              quando,
              "tags",
              List.of(),
              "content",
              json.writeValueAsString(perfil)));
    }
    if (!"COMPROVANTE".equals(pedido.get("tipo"))) throw invalido();
    var aceite = conferirAceite((Map<String, ?>) pedido.get("aceite"));
    var conteudo = new LinkedHashMap<String, Object>();
    conteudo.put("protocolo", PROTOCOLO_PUBLICO);
    conteudo.put("acao", "registrar-aceite");
    conteudo.put("algoritmo", "SHA-256");
    conteudo.put(
        "compromisso",
        compromisso(aceite.get("id").toString(), Objects.toString(pedido.get("sal"), "")));
    return new LinkedHashMap<>(
        Map.of(
            "kind",
            1,
            "created_at",
            quando,
            "tags",
            List.of(List.of("client", "aFIRMA")),
            "content",
            json.writeValueAsString(conteudo)));
  }

  Map<String, Object> assinarConversa(Map<String, ?> rascunho, byte[] chaveCliente) {
    var evento = new LinkedHashMap<String, Object>(rascunho);
    evento.put("pubkey", chavePublica(chaveCliente));
    byte[] resumo = resumoEvento(evento);
    evento.put("id", hex(resumo));
    evento.put("sig", hex(Secp256k1.get().signSchnorr(resumo, chaveCliente, sortear(32))));
    return evento;
  }

  Map<String, Object> conferirResposta(
      Map<String, ?> bruto, Map<String, ?> esperado, String chave) {
    var evento = conferirEvento(bruto);
    if (!chave.equals(evento.get("pubkey"))
        || inteiro(evento.get("kind")) != inteiro(esperado.get("kind"))
        || inteiro(evento.get("created_at")) != inteiro(esperado.get("created_at"))
        || !Objects.equals(evento.get("content"), esperado.get("content"))
        || !Objects.equals(evento.get("tags"), esperado.get("tags"))) throw invalido();
    return evento;
  }

  String serializarEvento(Map<String, ?> evento) {
    var ordenado = new LinkedHashMap<String, Object>();
    for (String campo : List.of("id", "pubkey", "created_at", "kind", "tags", "content", "sig"))
      ordenado.put(campo, evento.get(campo));
    return json.writeValueAsString(ordenado);
  }

  static IllegalArgumentException invalido() {
    return new IllegalArgumentException("Evento ou prova Nostr inválida");
  }
}
