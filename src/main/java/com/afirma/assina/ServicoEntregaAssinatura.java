package com.afirma.assina;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class ServicoEntregaAssinatura {
    private final String url;
    private final String segredo;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ServicoEntregaAssinatura(@Value("${APP_ASSINATURAS_ENTREGA_URL:}") String url,
            @Value("${APP_ASSINATURAS_ENTREGA_SEGREDO:}") String segredo, ObjectMapper json) {
        this.url = url; this.segredo = segredo; this.json = json;
    }
    public boolean disponivel() { return !url.isBlank(); }
    public void enviar(String canal, String destino, String tipo, String conteudo) {
        if (!disponivel()) throw new IllegalStateException("Serviço de entrega de e-mail e WhatsApp não configurado");
        try {
            var pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
            if (!segredo.isBlank()) pedido.header("Authorization", "Bearer " + segredo);
            var resposta = http.send(pedido.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                Map.of("canal", canal, "destino", destino, "tipo", tipo, "conteudo", conteudo)))).build(), HttpResponse.BodyHandlers.discarding());
            if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) throw new IllegalStateException("Entrega recusada pelo provedor");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Entrega interrompida"); }
        catch (java.io.IOException e) { throw new IllegalStateException("Serviço de entrega indisponível"); }
    }
}
