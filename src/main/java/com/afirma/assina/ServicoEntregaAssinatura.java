package com.afirma.assina;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class ServicoEntregaAssinatura {
    private final EnviadorCodigoConvite email;
    private final String endereco;
    private final String segredo;
    private final ObjectMapper json;
    private final String enderecoSms;
    private final String autorizacaoSms;
    private final HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ServicoEntregaAssinatura(@Value("${APP_ASSINATURAS_ENTREGA_URL:}") String endereco,
            @Value("${APP_ASSINATURAS_ENTREGA_SEGREDO:}") String segredo,
            @Value("${APP_ASSINATURAS_SMS_URL:}") String enderecoSms,
            @Value("${APP_ASSINATURAS_SMS_USUARIO:}") String usuarioSms,
            @Value("${APP_ASSINATURAS_SMS_SENHA:}") String senhaSms,
            EnviadorCodigoConvite email, ObjectMapper json) {
        this.endereco = endereco;
        this.segredo = segredo;
        this.email = email;
        this.json = json;
        this.enderecoSms = enderecoSms.strip();
        this.autorizacaoSms = usuarioSms.isBlank() || senhaSms.isBlank() ? "" :
            "Basic " + Base64.getEncoder().encodeToString((usuarioSms + ":" + senhaSms).getBytes(StandardCharsets.UTF_8));
    }
    public boolean disponivel() { return !endereco.isBlank() || email.configurado(); }
    public boolean whatsappDisponivel() { return !endereco.isBlank(); }
    public boolean smsDisponivel() { return !enderecoSms.isBlank() && !autorizacaoSms.isBlank(); }

    public void enviar(String canal, String destino, String tipo, String conteudo) {
        if ("sms".equals(canal)) {
            enviarSms(destino, tipo, conteudo);
            return;
        }
        if ("email".equals(canal) && email.configurado()) {
            String assunto = switch (tipo) {
                case "codigo" -> "Código de assinatura daFIRMA";
                case "observador" -> "Atualização de assinatura daFIRMA";
                default -> "Convite para assinatura daFIRMA";
            };
            email.enviarMensagem(destino, assunto, conteudo);
            return;
        }
        if (endereco.isBlank()) throw new IllegalStateException("Serviço de entrega de e-mail e WhatsApp não configurado");
        postar(endereco, segredo.isBlank() ? "" : "Bearer " + segredo,
            Map.of("canal", canal, "destino", destino, "tipo", tipo, "conteudo", conteudo));
    }

    private void enviarSms(String destino, String tipo, String conteudo) {
        if (!smsDisponivel()) throw new IllegalStateException("Gateway SMS não configurado");
        String telefone = DadosPessoaisSignatario.whatsapp(destino);
        if (telefone.isEmpty()) throw new IllegalArgumentException("Informe um celular para SMS");
        boolean codigo = "codigo".equals(tipo);
        String texto = codigo ? "Código de assinatura aFIRMA: " + conteudo
            : "Convite para assinatura aFIRMA: " + conteudo;
        postar(enderecoSms, autorizacaoSms, Map.of(
            "phoneNumbers", List.of(telefone), "textMessage", Map.of("text", texto),
            "ttl", codigo ? DesafioAssinatura.VALIDADE_SEGUNDOS : 3600));
    }

    private void postar(String url, String autorizacao, Map<String, ?> corpo) {
        try {
            var pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
            if (!autorizacao.isBlank()) pedido.header("Authorization", autorizacao);
            var resposta = clienteHttp.send(pedido.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                .build(), HttpResponse.BodyHandlers.discarding());
            if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) throw new IllegalStateException("Entrega recusada pelo provedor");
        } catch (InterruptedException interrupcao) { Thread.currentThread().interrupt(); throw new IllegalStateException("Entrega interrompida"); }
        catch (java.io.IOException falha) { throw new IllegalStateException("Serviço de entrega indisponível"); }
    }
}
