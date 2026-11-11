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
    @org.springframework.beans.factory.annotation.Autowired
    private EnviadorCodigoConvite email;
    private final String endereco;
    private final String segredo;
    private final ObjectMapper json;
    private final HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ServicoEntregaAssinatura(@Value("${APP_ASSINATURAS_ENTREGA_URL:}") String endereco,
            @Value("${APP_ASSINATURAS_ENTREGA_SEGREDO:}") String segredo, ObjectMapper json) {
        this.endereco = endereco; this.segredo = segredo; this.json = json;
    }
    public boolean disponivel() { return !endereco.isBlank() || email.configurado(); }
    public void enviar(String canal, String destino, String tipo, String conteudo) {
        if ("email".equals(canal) && email.configurado()) {
            email.enviarMensagem(destino, "codigo".equals(tipo) ? "Código de assinatura daFIRMA" : "Convite para assinatura daFIRMA", conteudo);
            return;
        }
        if (endereco.isBlank()) throw new IllegalStateException("Serviço de entrega de e-mail e WhatsApp não configurado");
        try {
            var pedido = HttpRequest.newBuilder(URI.create(endereco)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
            if (!segredo.isBlank()) pedido.header("Authorization", "Bearer " + segredo);
            var resposta = clienteHttp.send(pedido.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                Map.of("canal", canal, "destino", destino, "tipo", tipo, "conteudo", conteudo)))).build(), HttpResponse.BodyHandlers.discarding());
            if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) throw new IllegalStateException("Entrega recusada pelo provedor");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Entrega interrompida"); }
        catch (java.io.IOException e) { throw new IllegalStateException("Serviço de entrega indisponível"); }
    }
}
