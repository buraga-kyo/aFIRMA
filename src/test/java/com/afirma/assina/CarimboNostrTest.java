package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mockwebserver3.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class CarimboNostrTest {
  final JsonMapper json = JsonMapper.builder().build();
  final CriptografiaNostr cripto = new CriptografiaNostr(json);

  byte[] arquivo(String nome) throws Exception {
    return getClass().getResourceAsStream("/nostr/" + nome).readAllBytes();
  }

  static byte[] chave(int valor) {
    byte[] chave = new byte[32];
    Arrays.fill(chave, (byte) valor);
    return chave;
  }

  ProvaOpenTimestamps.Atestado pendente(String url) {
    byte[] u = url.getBytes(StandardCharsets.US_ASCII);
    return new ProvaOpenTimestamps.Atestado(
        ProvaOpenTimestamps.PENDENTE, CriptografiaNostr.juntar(new byte[] {(byte) u.length}, u));
  }

  Map<String, Object> pedido() {
    var aceite =
        cripto.assinarConversa(
            Map.of(
                "kind",
                1,
                "created_at",
                1700000000,
                "tags",
                List.of(),
                "content",
                json.writeValueAsString(
                    Map.of(
                        "protocolo",
                        CriptografiaNostr.PROTOCOLO_ACEITE,
                        "acao",
                        "aceitar-documento",
                        "algoritmo",
                        "SHA-256",
                        "hashPdf",
                        "aa".repeat(32),
                        "tamanhoPdf",
                        100,
                        "desafio",
                        "bb".repeat(32),
                        "papel",
                        "Signatário",
                        "textoAceite",
                        CriptografiaNostr.TEXTO_ACEITE))),
            chave(1));
    var p =
        new HashMap<String, Object>(
            Map.of(
                "tipo",
                "COMPROVANTE",
                "criadoEm",
                1700000001,
                "aceite",
                aceite,
                "sal",
                "cc".repeat(32)));
    var publico = cripto.assinarConversa(cripto.montarEvento(p), chave(2));
    p.put("eventoCanonico", cripto.serializarEvento(publico));
    p.put("chavePublica", publico.get("pubkey"));
    return p;
  }

  @Test
  void leProvaOficialEGravaFormatoCompativel() throws Exception {
    byte[] digest = CriptografiaNostr.sha256(arquivo("exemplo-oficial.txt"));
    var prova = ProvaOpenTimestamps.ler(arquivo("exemplo-oficial.txt.ots"), digest);
    assertFalse(prova.confirmacoes().isEmpty());
    var reaberta = ProvaOpenTimestamps.ler(prova.gravar(), digest);
    assertEquals(
        prova.confirmacoes().getFirst().altura(), reaberta.confirmacoes().getFirst().altura());
    assertArrayEquals(
        prova.confirmacoes().getFirst().mensagem(), reaberta.confirmacoes().getFirst().mensagem());
  }

  @Test
  void recusaCabecalhoVersaoResumoEDadosSobrando() throws Exception {
    byte[] original = arquivo("exemplo-oficial.txt.ots"),
        resumo = CriptografiaNostr.sha256(arquivo("exemplo-oficial.txt"));
    byte[] quebrado = original.clone();
    quebrado[1] ^= 1;
    assertThrows(IllegalArgumentException.class, () -> ProvaOpenTimestamps.ler(quebrado, resumo));
    byte[] versao = original.clone();
    versao[ProvaOpenTimestamps.CABECALHO.length] = 2;
    assertThrows(IllegalArgumentException.class, () -> ProvaOpenTimestamps.ler(versao, resumo));
    assertThrows(
        IllegalArgumentException.class, () -> ProvaOpenTimestamps.ler(original, new byte[32]));
    byte[] sobrando = Arrays.copyOf(original, original.length + 1);
    assertThrows(IllegalArgumentException.class, () -> ProvaOpenTimestamps.ler(sobrando, resumo));
    byte[] base =
        CriptografiaNostr.juntar(ProvaOpenTimestamps.CABECALHO, new byte[] {1, 8}, resumo);
    byte[] fundo = CriptografiaNostr.juntar(base, new byte[20000]);
    Arrays.fill(fundo, base.length, fundo.length, (byte) 8);
    assertThrows(IllegalArgumentException.class, () -> ProvaOpenTimestamps.ler(fundo, resumo));
  }

  @Test
  void calendarioNaoPodeMandarConsultarUrlInterna() throws Exception {
    var p = pedido();
    byte[] resumo =
        CriptografiaNostr.sha256(
            p.get("eventoCanonico").toString().getBytes(StandardCharsets.UTF_8));
    var prova = new ProvaOpenTimestamps(resumo);
    prova.raiz.atestados.add(pendente("http://127.0.0.1/segredos"));
    p.put("ots", Base64.getEncoder().encodeToString(prova.gravar()));
    var conexoes = mock(ConexoesNostr.class);
    when(conexoes.buscar(any(), any(), anyMap(), anyInt()))
        .thenAnswer(i -> {
          URI uri = i.getArgument(0);
          assertEquals("https", uri.getScheme());
          assertTrue(
              Set.of("alice.btc.calendar.opentimestamps.org", "bob.btc.calendar.opentimestamps.org")
                  .contains(uri.getHost()));
          throw new IOException();
        });
    var r = new CarimboNostr(conexoes, json, cripto, new MockEnvironment()).carimbar(p);
    assertEquals("PENDENTE", r.get("estado"));
    verify(conexoes, times(2)).buscar(any(), any(), anyMap(), anyInt());
  }

  @Test
  void respostaDoCalendarioGeraPendenciaNaoAnterioridadeConfirmada() throws Exception {
    var conexoes = mock(ConexoesNostr.class);
    when(conexoes.buscar(any(), any(), anyMap(), anyInt()))
        .thenAnswer(i -> {
          byte[] resumo = i.getArgument(1);
          var prova = new ProvaOpenTimestamps(resumo);
          prova.raiz.atestados.add(pendente("https://alice.btc.calendar.opentimestamps.org"));
          byte[] bytes = prova.gravar();
          return Arrays.copyOfRange(bytes, ProvaOpenTimestamps.CABECALHO.length + 34, bytes.length);
        });
    var r = new CarimboNostr(conexoes, json, cripto, new MockEnvironment()).carimbar(pedido());
    assertEquals("PENDENTE", r.get("estado"));
    assertNotNull(r.get("ots"));
  }

  @Test
  void atestacaoBitcoinSemNoPermaneceSemConfirmacao() throws Exception {
    var p = pedido();
    var prova =
        new ProvaOpenTimestamps(
            CriptografiaNostr.sha256(
                p.get("eventoCanonico").toString().getBytes(StandardCharsets.UTF_8)));
    prova.raiz.atestados.add(
        new ProvaOpenTimestamps.Atestado(ProvaOpenTimestamps.BITCOIN, new byte[] {100}));
    p.put("ots", Base64.getEncoder().encodeToString(prova.gravar()));
    var r =
        new CarimboNostr(mock(ConexoesNostr.class), json, cripto, new MockEnvironment())
            .carimbar(p);
    assertEquals("AGUARDANDO_NO_BITCOIN", r.get("estado"));
  }

  MockResponse rpc(Object resultado) {
    return new MockResponse.Builder()
        .body(json.writeValueAsString(Map.of("result", resultado)))
        .build();
  }

  String hashBloco(byte[] cabecalho) {
    byte[] hash = CriptografiaNostr.sha256(CriptografiaNostr.sha256(cabecalho));
    for (int i = 0; i < 16; i++) {
      byte b = hash[i];
      hash[i] = hash[31 - i];
      hash[31 - i] = b;
    }
    return CriptografiaNostr.hex(hash);
  }

  @Test
  void confirmaBlocoDoNoComSeisConfirmacoesEConfereReorganizacao() throws Exception {
    byte[] resumo = CriptografiaNostr.sha256("evento".getBytes(StandardCharsets.UTF_8)),
        cabecalho = new byte[80];
    System.arraycopy(resumo, 0, cabecalho, 36, 32);
    ByteBuffer.wrap(cabecalho, 68, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(1700000000);
    String bloco = hashBloco(cabecalho);
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(rpc(Map.of("chain", "main", "initialblockdownload", false, "blocks", 105)));
      servidor.enqueue(rpc(bloco));
      servidor.enqueue(rpc(CriptografiaNostr.hex(cabecalho)));
      servidor.enqueue(rpc(bloco));
      servidor.start();
      var carimbo =
          new CarimboNostr(
              new ConexoesNostr(),
              json,
              cripto,
              new MockEnvironment()
                  .withProperty("APP_BITCOIN_RPC_URL", servidor.url("/").toString())
                  .withProperty("APP_BITCOIN_RPC_USER", "firma_ots")
                  .withProperty("APP_BITCOIN_RPC_PASSWORD", "senha-teste"));
      var r = carimbo.conferirBloco(new ProvaOpenTimestamps.Confirmacao(resumo, 100));
      assertEquals(bloco, r.get("bloco"));
      assertEquals(1700000000L, r.get("data"));
      assertTrue(servidor.takeRequest().getHeaders().get("Authorization").startsWith("Basic "));
      servidor.enqueue(rpc(Map.of("chain", "main", "initialblockdownload", false, "blocks", 105)));
      servidor.enqueue(rpc(bloco));
      servidor.enqueue(rpc(CriptografiaNostr.hex(cabecalho)));
      servidor.enqueue(rpc("ff".repeat(32)));
      assertTrue(carimbo.conferirBloco(new ProvaOpenTimestamps.Confirmacao(resumo, 100)).isEmpty());
    }
  }

  @Test
  void rejeitaMerkleRootDivergenteENoSemSincronizar() throws Exception {
    byte[] cabecalho = new byte[80];
    String bloco = hashBloco(cabecalho);
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(rpc(Map.of("chain", "main", "initialblockdownload", false, "blocks", 105)));
      servidor.enqueue(rpc(bloco));
      servidor.enqueue(rpc(CriptografiaNostr.hex(cabecalho)));
      servidor.start();
      var carimbo =
          new CarimboNostr(
              new ConexoesNostr(),
              json,
              cripto,
              new MockEnvironment()
                  .withProperty("APP_BITCOIN_RPC_URL", servidor.url("/").toString()));
      assertTrue(
          carimbo
              .conferirBloco(
                  new ProvaOpenTimestamps.Confirmacao(
                      CriptografiaNostr.sha256(new byte[] {1}), 100))
              .isEmpty());
      servidor.enqueue(rpc(Map.of("chain", "main", "initialblockdownload", true, "blocks", 105)));
      assertTrue(
          carimbo.conferirBloco(new ProvaOpenTimestamps.Confirmacao(new byte[32], 100)).isEmpty());
      servidor.enqueue(rpc(Map.of("chain", "main", "initialblockdownload", false, "blocks", 104)));
      assertTrue(
          carimbo.conferirBloco(new ProvaOpenTimestamps.Confirmacao(new byte[32], 100)).isEmpty());
    }
  }

  @Test
  void transporteLimitaRespostaENaoSegueRedirecionamentos() throws Exception {
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(
          new MockResponse.Builder()
              .code(302)
              .addHeader("Location", "http://127.0.0.1/segredo")
              .build());
      servidor.enqueue(new MockResponse.Builder().body("x".repeat(1000)).build());
      servidor.start();
      var conexoes = new ConexoesNostr();
      assertThrows(
          Exception.class, () -> conexoes.buscar(servidor.url("/").uri(), null, Map.of(), 100));
      assertThrows(
          Exception.class, () -> conexoes.buscar(servidor.url("/").uri(), null, Map.of(), 100));
      assertEquals(2, servidor.getRequestCount());
    }
  }
}
