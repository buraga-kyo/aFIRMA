package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;

@Component
public class ConexoesNostr {
  private final HttpClient cliente =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  HttpClient cliente() {
    return cliente;
  }

  byte[] buscar(URI endereco, byte[] corpo, Map<String, String> cabecalhos, int limite)
      throws Exception {
    var rascunho = HttpRequest.newBuilder(endereco).timeout(Duration.ofSeconds(6));
    cabecalhos.forEach(rascunho::header);
    if (corpo == null) rascunho.GET();
    else rascunho.POST(HttpRequest.BodyPublishers.ofByteArray(corpo));
    var futuro = cliente.sendAsync(rascunho.build(), info -> new CorpoLimitado(limite));
    try {
      var resposta = futuro.get(7, TimeUnit.SECONDS);
      if (resposta.statusCode() != 200)
        throw new IllegalStateException("Serviço externo indisponível");
      return resposta.body();
    } finally {
      futuro.cancel(true);
    }
  }

  static URI rpcSeguro(String endereco) {
    URI uri = URI.create(endereco);
    boolean local =
        Set.of("localhost", "127.0.0.1", "::1", "[::1]")
            .contains(Objects.toString(uri.getHost(), ""));
    if (uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getQuery() != null
        || uri.getHost() == null
        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && local))
      throw new IllegalArgumentException(
          "RPC Bitcoin exige HTTPS ou HTTP em loopback, sem credenciais na URL");
    return uri;
  }

  private static final class CorpoLimitado implements HttpResponse.BodySubscriber<byte[]> {
    private final int limite;
    private final ByteArrayOutputStream dados = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> fim = new CompletableFuture<>();
    private Flow.Subscription entrada;

    CorpoLimitado(int limite) {
      this.limite = limite;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return fim;
    }

    @Override
    public void onSubscribe(Flow.Subscription entrada) {
      this.entrada = entrada;
      entrada.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> partes) {
      for (var parte : partes) {
        if (parte.remaining() > limite - dados.size()) {
          entrada.cancel();
          fim.completeExceptionally(
              new IllegalArgumentException("Resposta externa excede o limite"));
          return;
        }
        byte[] bytes = new byte[parte.remaining()];
        parte.get(bytes);
        dados.writeBytes(bytes);
      }
      entrada.request(1);
    }

    @Override
    public void onError(Throwable falha) {
      fim.completeExceptionally(new IllegalStateException("Falha na conexão externa"));
    }

    @Override
    public void onComplete() {
      fim.complete(dados.toByteArray());
    }
  }
}
