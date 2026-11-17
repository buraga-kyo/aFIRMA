package com.afirma.assina;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

final class CanalRelayNostr implements WebSocket.Listener, AutoCloseable {
  private final BlockingQueue<String> mensagens = new ArrayBlockingQueue<>(128);
  private final StringBuilder pedacos = new StringBuilder();
  private volatile boolean encerrado;
  private WebSocket conexao;

  CanalRelayNostr(HttpClient cliente, URI endereco) throws Exception {
    var tentativa =
        cliente
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .buildAsync(endereco, this);
    try {
      conexao = tentativa.get(5, TimeUnit.SECONDS);
    } finally {
      if (!tentativa.isDone()) tentativa.cancel(true);
    }
  }

  void enviar(String mensagem) throws Exception {
    conexao.sendText(mensagem, true).get(3, TimeUnit.SECONDS);
  }

  String esperar(long ate) throws InterruptedException, SemResposta {
    while (!encerrado || !mensagens.isEmpty()) {
      long falta = ate - System.nanoTime();
      if (falta <= 0) break;
      String mensagem =
          mensagens.poll(Math.min(falta, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
      if (mensagem != null) return mensagem;
    }
    throw new SemResposta();
  }

  @Override
  public void onOpen(WebSocket ws) {
    ws.request(1);
  }

  @Override
  public CompletionStage<?> onText(WebSocket ws, CharSequence texto, boolean ultimo) {
    if (pedacos.length() + texto.length() > 140000) {
      encerrado = true;
      ws.abort();
      return CompletableFuture.completedFuture(null);
    }
    pedacos.append(texto);
    if (ultimo) {
      if (!mensagens.offer(pedacos.toString())) {
        encerrado = true;
        ws.abort();
      }
      pedacos.setLength(0);
    }
    ws.request(1);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletionStage<?> onBinary(WebSocket ws, java.nio.ByteBuffer dados, boolean ultimo) {
    encerrado = true;
    ws.abort();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletionStage<?> onClose(WebSocket ws, int codigo, String motivo) {
    encerrado = true;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void onError(WebSocket ws, Throwable falha) {
    encerrado = true;
  }

  static final class SemResposta extends java.io.IOException {}

  @Override
  public void close() {
    encerrado = true;
    if (conexao != null) conexao.abort();
  }
}
