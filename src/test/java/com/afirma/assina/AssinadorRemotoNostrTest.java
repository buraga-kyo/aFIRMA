package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.*;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssinadorRemotoNostrTest {
  final JsonMapper json = JsonMapper.builder().build();
  final CriptografiaNostr cripto = new CriptografiaNostr(json);
  final CifraConversaNostr cifra = new CifraConversaNostr(cripto);
  final ConexoesNostr conexoes = new ConexoesNostr();
  final byte[] cliente = chave(1), transporte = chave(2), empresa = chave(3);

  static byte[] chave(int valor) {
    byte[] chave = new byte[32];
    Arrays.fill(chave, (byte) valor);
    return chave;
  }

  @SuppressWarnings("unchecked")
  WebSocketListener bunker(
      boolean autorizado,
      boolean adulterar,
      boolean forjar,
      AtomicInteger pareamentos,
      AtomicReference<Throwable> falha) {
    return new WebSocketListener() {
      boolean liberado = autorizado;
      String inscricao;

      @Override
      public void onMessage(WebSocket ws, String texto) {
        try {
          List<?> mensagem = json.readValue(texto, List.class);
          if (mensagem.get(0).equals("REQ")) {
            inscricao = mensagem.get(1).toString();
            return;
          }
          if (!mensagem.get(0).equals("EVENT")) return;
          var e = cripto.conferirEvento((Map<String, ?>) mensagem.get(1));
          byte[] conversa = cifra.chaveDaConversa(transporte, e.get("pubkey").toString());
          Map<?, ?> pedido =
              json.readValue(cifra.abrir(e.get("content").toString(), conversa), Map.class);
          String metodo = pedido.get("method").toString();
          var resposta = new LinkedHashMap<String, Object>();
          resposta.put("id", pedido.get("id"));
          if (metodo.equals("connect")) {
            pareamentos.incrementAndGet();
            assertEquals("primeiro-acesso", ((List<?>) pedido.get("params")).get(1));
            liberado = true;
            resposta.put("result", "ack");
          } else if (!liberado) resposta.put("error", "cliente não autorizado");
          else if (metodo.equals("get_public_key"))
            resposta.put("result", cripto.chavePublica(empresa));
          else if (metodo.equals("sign_event")) {
            var rascunho =
                (Map<String, Object>)
                    json.readValue(
                        ((List<?>) pedido.get("params")).getFirst().toString(), Map.class);
            if (adulterar) rascunho.put("content", "outro pedido");
            resposta.put(
                "result", json.writeValueAsString(cripto.assinarConversa(rascunho, empresa)));
          }
          if (forjar) resposta.put("id", "resposta-de-outro-pedido");
          var retorno =
              cripto.assinarConversa(
                  Map.of(
                      "kind",
                      24133,
                      "created_at",
                      1700000000,
                      "tags",
                      List.of(List.of("p", e.get("pubkey"))),
                      "content",
                      cifra.cifrar(json.writeValueAsString(resposta), conversa)),
                  transporte);
          ws.send(json.writeValueAsString(List.of("EVENT", inscricao, retorno)));
          if (forjar) {
            ws.send(json.writeValueAsString(List.of("EVENT", inscricao, retorno)));
            ws.close(1000, "fim");
          }
        } catch (Throwable erro) {
          falha.set(erro);
          ws.close(1011, "falha no teste");
        }
      }
    };
  }

  PerfisNostrConfigurados.Perfil perfil(MockWebServer servidor) {
    URI url = URI.create(servidor.url("/").toString().replaceFirst("http:", "ws:"));
    return new PerfisNostrConfigurados.Perfil(
        1,
        "ref",
        cripto.chavePublica(empresa),
        cliente,
        cripto.chavePublica(transporte),
        "primeiro-acesso",
        List.of(url),
        List.of(url));
  }

  @Test
  void conversaCifradaConfereIdentidadeEfetivaEAssinaComOutraChaveDeTransporte() throws Exception {
    var erro = new AtomicReference<Throwable>();
    var pareamentos = new AtomicInteger();
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(
          new MockResponse.Builder()
              .webSocketUpgrade(bunker(false, false, false, pareamentos, erro))
              .build());
      servidor.start();
      var cliente = new AssinadorRemotoNostr(json, cripto, cifra, conexoes);
      var pedido =
          Map.<String, Object>of(
              "tipo", "PERFIL", "nome", "Firma", "sobre", "Perfil público", "criadoEm", 1700000000);
      var resposta = cliente.assinar(perfil(servidor), pedido);
      assertEquals(cripto.chavePublica(empresa), resposta.get("chavePublica"));
      assertEquals(1, pareamentos.get());
      assertNull(erro.get());
      var evento =
          cripto.conferirEvento(
              json.readValue(resposta.get("eventoCanonico").toString(), Map.class));
      assertEquals(0, ((Number) evento.get("kind")).intValue());
    }
  }

  @Test
  void clienteJaAutorizadoNaoReenviaSegredoDePareamento() throws Exception {
    var erro = new AtomicReference<Throwable>();
    var pareamentos = new AtomicInteger();
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(
          new MockResponse.Builder()
              .webSocketUpgrade(bunker(true, false, false, pareamentos, erro))
              .build());
      servidor.start();
      assertEquals(
          cripto.chavePublica(empresa),
          new AssinadorRemotoNostr(json, cripto, cifra, conexoes)
              .conferirIdentidade(perfil(servidor))
              .get("chavePublica"));
      assertEquals(0, pareamentos.get());
      assertNull(erro.get());
    }
  }

  @Test
  void recusaAssinaturaDeOutroConteudo() throws Exception {
    var erro = new AtomicReference<Throwable>();
    var pareamentos = new AtomicInteger();
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(
          new MockResponse.Builder()
              .webSocketUpgrade(bunker(true, true, false, pareamentos, erro))
              .build());
      servidor.start();
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new AssinadorRemotoNostr(json, cripto, cifra, conexoes)
                  .assinar(
                      perfil(servidor),
                      Map.of(
                          "tipo", "PERFIL", "nome", "Firma", "sobre", "", "criadoEm", 1700000000)));
      assertNull(erro.get());
    }
  }

  @Test
  void publicacaoExigeAckDoEventoCerto() throws Exception {
    try (var servidor = new MockWebServer()) {
      servidor.enqueue(
          new MockResponse.Builder()
              .webSocketUpgrade(
                  new WebSocketListener() {
                    @Override
                    public void onMessage(WebSocket ws, String texto) {
                      List<?> m = json.readValue(texto, List.class);
                      Map<?, ?> e = (Map<?, ?>) m.get(1);
                      ws.send(json.writeValueAsString(List.of("OK", "outro-id", true, "ignorar")));
                      ws.send(json.writeValueAsString(List.of("OK", e.get("id"), true, "gravado")));
                    }
                  })
              .build());
      servidor.start();
      var servico =
          new ServicoPublicacaoNostr(
              mock(PerfisNostrConfigurados.class),
              mock(AssinadorRemotoNostr.class),
              cripto,
              mock(CarimboNostr.class),
              conexoes,
              mock(LimiteAssinadorNostr.class),
              json);
      var recibo =
          servico.enviarAoRelay(
              URI.create(servidor.url("/").toString().replaceFirst("http:", "ws:")),
              Map.of("id", "id-correto"));
      assertEquals(true, recibo.get("aceito"));
    }
  }
}
