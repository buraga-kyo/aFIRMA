package com.afirma.assina;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

class ServicoEntregaAssinaturaTest {
    @Test void enviaContratoAoGatewayEReconheceFalhaDoProvedor() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var recebido = new AtomicReference<String>();
        var autorizacao = new AtomicReference<String>();
        servidor.createContext("/entrega", requisicaoHttp -> {
            recebido.set(new String(requisicaoHttp.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacao.set(requisicaoHttp.getRequestHeaders().getFirst("Authorization"));
            requisicaoHttp.sendResponseHeaders(202, -1);
            requisicaoHttp.close();
        });
        servidor.createContext("/falha", requisicaoHttp -> {
            requisicaoHttp.sendResponseHeaders(503, -1);
            requisicaoHttp.close();
        });
        servidor.start();
        try {
            String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
            var json = new ObjectMapper();
            new ServicoEntregaAssinatura(base + "/entrega", "teste", "", "", "", mock(EnviadorCodigoConvite.class), json).enviar("email", "ana@example.com", "codigo", "123456");
            var dados = json.readTree(recebido.get());
            assertEquals("123456", dados.get("conteudo").asText());
            assertEquals("email", dados.get("canal").asText());
            assertEquals("Bearer teste", autorizacao.get());
            assertThrows(IllegalStateException.class, () -> new ServicoEntregaAssinatura(base + "/falha", "", "", "", "", mock(EnviadorCodigoConvite.class), json).enviar("email", "ana@example.com", "codigo", "123456"));
        } finally { servidor.stop(0); }
    }

    @Test void enviaSmsComAutenticacaoBasicaTelefoneNormalizadoEExpiracao() throws Exception {
        var pedido = new AtomicReference<String>();
        var autorizacao = new AtomicReference<String>();
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/message", requisicaoHttp -> {
            pedido.set(new String(requisicaoHttp.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacao.set(requisicaoHttp.getRequestHeaders().getFirst("Authorization"));
            requisicaoHttp.sendResponseHeaders(202, -1);
            requisicaoHttp.close();
        });
        servidor.start();
        try {
            var json = new ObjectMapper();
            String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/message";
            var servico = new ServicoEntregaAssinatura("", "", url, "app", "teste", mock(EnviadorCodigoConvite.class), json);
            assertTrue(servico.smsDisponivel());
            assertFalse(servico.disponivel());
            servico.enviar("sms", "(11) 99999-9999", "codigo", "123456");
            var codigo = json.readTree(pedido.get());
            assertEquals("Basic YXBwOnRlc3Rl", autorizacao.get());
            assertEquals("+5511999999999", codigo.get("phoneNumbers").get(0).asText());
            assertEquals("Código de assinatura aFIRMA: 123456", codigo.get("textMessage").get("text").asText());
            assertEquals(DesafioAssinatura.VALIDADE_SEGUNDOS, codigo.get("ttl").asInt());
            servico.enviar("sms", "+5511999999999", "convite", "https://firma.example/convite/exemplo");
            var convite = json.readTree(pedido.get());
            assertTrue(convite.get("textMessage").get("text").asText().endsWith("https://firma.example/convite/exemplo"));
        } finally {
            servidor.stop(0);
        }
    }
}
