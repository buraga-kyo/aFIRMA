package com.afirma.assina;
import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

class ServicoEntregaAssinaturaTest {
    @Test void enviaContratoAoGatewayEReconheceFalhaDoProvedor() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var recebido = new AtomicReference<String>(); var autorizacao = new AtomicReference<String>();
        server.createContext("/entrega", exchange -> {
            recebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacao.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(202, -1); exchange.close();
        });
        server.createContext("/falha", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var json = new ObjectMapper();
            new ServicoEntregaAssinatura(base + "/entrega", "teste", json).enviar("email", "ana@example.com", "codigo", "123456");
            var dados = json.readTree(recebido.get());
            assertEquals("123456", dados.get("conteudo").asText());
            assertEquals("email", dados.get("canal").asText());
            assertEquals("Bearer teste", autorizacao.get());
            assertThrows(IllegalStateException.class, () -> new ServicoEntregaAssinatura(base + "/falha", "", json).enviar("email", "ana@example.com", "codigo", "123456"));
        } finally { server.stop(0); }
    }
}
