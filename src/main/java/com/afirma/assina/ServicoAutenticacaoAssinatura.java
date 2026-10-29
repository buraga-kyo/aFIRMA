package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoAutenticacaoAssinatura {
    private final RepositorioSignatarioEnvelope pessoas;
    private final RepositorioEnvelopeAssinatura envelopes;
    private final RepositorioDesafioAssinatura desafios;
    private final ServicoEntregaAssinatura entrega;
    private final RepositorioSignatarioHistorico historicos;
    private static final SecureRandom RANDOM = new SecureRandom();
    public ServicoAutenticacaoAssinatura(RepositorioSignatarioEnvelope pessoas, RepositorioEnvelopeAssinatura envelopes,
            RepositorioDesafioAssinatura desafios, ServicoEntregaAssinatura entrega, RepositorioSignatarioHistorico historicos) {
        this.pessoas = pessoas; this.envelopes = envelopes; this.desafios = desafios; this.entrega = entrega; this.historicos = historicos;
    }
    public void configurar(SignatarioEnvelope pessoa, List<String> autenticacao, List<String> notificacao) {
        List<String> auth = autenticacao == null ? List.of() : autenticacao;
        List<String> avisos = notificacao == null ? List.of() : notificacao;
        if (auth.stream().anyMatch(a -> a == null || !Set.of("tela", "email", "whatsapp", "selfie").contains(a))
                || avisos.stream().anyMatch(a -> a == null || !Set.of("email", "whatsapp").contains(a))) throw erro("Método inválido");
        if ((auth.contains("email") || avisos.contains("email")) && pessoa.getEmail().isBlank()) throw erro("Informe e-mail para o canal selecionado");
        if ((auth.contains("whatsapp") || avisos.contains("whatsapp")) && (pessoa.getWhatsapp() == null
                || !pessoa.getWhatsapp().replaceAll("[^0-9]", "").matches("[0-9]{10,15}"))) throw erro("Informe WhatsApp válido para o canal selecionado");
        if ((!avisos.isEmpty() || auth.contains("email") || auth.contains("whatsapp")) && !entrega.disponivel())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure o serviço de entrega antes de selecionar códigos ou notificações");
        pessoa.setFormaDeAutenticacao(List.copyOf(auth)); pessoa.setFormaDeNotificacao(List.copyOf(avisos));
    }
    private SignatarioEnvelope convite(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var pessoa = pessoas.findByResumoConvite(hash(token)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var env = envelopes.findById(pessoa.getEnvelopeId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (env.getExcluidoEm() != null || env.getFinalizadoEm() != null || pessoa.getAssinadoEm() != null || Instant.now().isAfter(env.getExpiraEm()))
            throw new ResponseStatusException(HttpStatus.GONE, "Convite encerrado");
        return pessoa;
    }
    @Transactional
    public void enviarCodigo(String token, String canal) {
        enviarCodigo(token, canal, null, null);
    }
    @Transactional
    public void enviarCodigo(String token, String canal, String ip, String dispositivo) {
        var pessoa = convite(token);
        if (pessoa.getFormaDeAutenticacao() == null || !pessoa.getFormaDeAutenticacao().contains(canal)
                || !Set.of("email", "whatsapp").contains(canal)) throw erro("Canal não solicitado neste convite");
        // Serializa também a primeira emissão, quando ainda não existe um desafio.
        pessoas.buscarConviteParaAssinar(pessoa.getResumoConvite()).orElseThrow();
        String id = pessoa.getId() + ":" + canal;
        var anterior = desafios.bloquear(id);
        if (anterior.isPresent() && anterior.get().getEnviadoEm().plusSeconds(60).isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde um minuto antes de reenviar");
        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        entrega.enviar(canal, canal.equals("email") ? pessoa.getEmail() : pessoa.getWhatsapp(), "codigo", codigo);
        desafios.save(new DesafioAssinatura(pessoa.getId(), canal, hash(id + ":" + codigo)));
        historicos.save(new SignatarioHistorico(pessoa.getId(), "aguardando_codigo", "codigo_enviado_" + canal, ip, dispositivo));
    }
    @Transactional
    public boolean verificarCodigo(String token, String canal, String codigo) {
        return verificarCodigo(token, canal, codigo, null, null);
    }
    @Transactional
    public boolean verificarCodigo(String token, String canal, String codigo, String ip, String dispositivo) {
        var pessoa = convite(token);
        if (!Set.of("email", "whatsapp").contains(canal) || codigo == null || !codigo.matches("[0-9]{6}")) return false;
        String id = pessoa.getId() + ":" + canal;
        if (pessoa.getFormaDeAutenticacao() == null || !pessoa.getFormaDeAutenticacao().contains(canal)) return false;
        boolean valido = desafios.bloquear(id).map(d -> d.verificar(hash(id + ":" + codigo))).orElse(false);
        historicos.save(new SignatarioHistorico(pessoa.getId(), valido ? "autenticado" : "codigo_recusado",
            (valido ? "codigo_validado_" : "codigo_recusado_") + canal, ip, dispositivo));
        return valido;
    }
    public void exigirVerificacao(SignatarioEnvelope pessoa) {
        for (String canal : pessoa.getFormaDeAutenticacao() == null ? List.<String>of() : pessoa.getFormaDeAutenticacao()) {
            if (Set.of("email", "whatsapp").contains(canal) && !desafios.findById(pessoa.getId() + ":" + canal).map(DesafioAssinatura::valido).orElse(false))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Valide o código por " + canal + " antes de assinar");
        }
    }
    @Transactional
    public List<Notificacao> notificar(UUID envelopeId, String criador, List<String> tokens, String origem) {
        var envelope = envelopes.findById(envelopeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!envelope.getCriador().equals(criador) || envelope.getExcluidoEm() != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (tokens == null || tokens.size() > 20) throw erro("Lista de convites inválida");
        var destinatarios = tokens.stream().distinct().map(token -> Map.entry(token, convite(token))).toList();
        for (var destinatario : destinatarios) {
            var pessoa = destinatario.getValue();
            if (!pessoa.getEnvelopeId().equals(envelopeId)) throw erro("Convite de outro envelope");
        }
        List<Notificacao> resultado = new ArrayList<>();
        for (var destinatario : destinatarios) {
            var pessoa = destinatario.getValue();
            String token = destinatario.getKey();
            for (String canal : pessoa.getFormaDeNotificacao() == null ? List.<String>of() : pessoa.getFormaDeNotificacao()) {
                boolean enviado;
                try {
                    entrega.enviar(canal, canal.equals("email") ? pessoa.getEmail() : pessoa.getWhatsapp(), "convite", origem + "/convite/" + token);
                    enviado = true;
                } catch (RuntimeException falhaEntrega) { enviado = false; }
                historicos.save(new SignatarioHistorico(pessoa.getId(), enviado ? "notificado" : "falha_entrega",
                    (enviado ? "convite_enviado_" : "convite_nao_enviado_") + canal, null, null));
                resultado.add(new Notificacao(pessoa.getNome(), canal, enviado));
            }
        }
        return resultado;
    }
    private String hash(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException erro(String texto) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, texto); }
    public record Notificacao(String nome, String canal, boolean enviado) {}
}
