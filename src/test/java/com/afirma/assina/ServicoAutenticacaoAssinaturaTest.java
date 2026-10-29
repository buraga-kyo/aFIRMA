package com.afirma.assina;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ServicoAutenticacaoAssinaturaTest {
    private final RepositorioDesafioAssinatura desafios = mock(RepositorioDesafioAssinatura.class);
    private final ServicoEntregaAssinatura entrega = mock(ServicoEntregaAssinatura.class);
    private final RepositorioSignatarioEnvelope pessoas = mock(RepositorioSignatarioEnvelope.class);
    private final RepositorioEnvelopeAssinatura envelopes = mock(RepositorioEnvelopeAssinatura.class);
    private final RepositorioSignatarioHistorico historicos = mock(RepositorioSignatarioHistorico.class);
    private final ServicoAutenticacaoAssinatura servico = new ServicoAutenticacaoAssinatura(pessoas, envelopes, desafios, entrega, historicos);
    private static String hash(String texto) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private SignatarioEnvelope prepararConvite(String token) throws Exception {
        var pessoa = new SignatarioEnvelope(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@example.com", hash(token));
        pessoa.setFormaDeAutenticacao(List.of("email"));
        when(pessoas.findByResumoConvite(hash(token))).thenReturn(Optional.of(pessoa));
        when(pessoas.buscarConviteParaAssinar(hash(token))).thenReturn(Optional.of(pessoa));
        when(envelopes.findById(pessoa.getEnvelopeId())).thenReturn(Optional.of(new EnvelopeAssinatura(pessoa.getEnvelopeId(),
            "Firma", "contrato.pdf", "dono", new byte[]{1}, java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600))));
        return pessoa;
    }
    private SignatarioEnvelope pessoa() { return new SignatarioEnvelope(UUID.randomUUID(), UUID.randomUUID(), "Ana", "", "0".repeat(64)); }
    @Test void aceitaEmailOpcionalSemCanaisExternos() {
        var pessoa = pessoa(); servico.configurar(pessoa, List.of("tela", "selfie"), List.of());
        assertEquals(List.of("tela", "selfie"), pessoa.getFormaDeAutenticacao());
    }
    @Test void exigeContatoDoCanalEscolhido() {
        when(entrega.disponivel()).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> servico.configurar(pessoa(), List.of("email"), List.of()));
        assertThrows(ResponseStatusException.class, () -> servico.configurar(pessoa(), List.of(), List.of("whatsapp")));
    }
    @Test void rejeitaCanalSemEntregaConfigurada() {
        var pessoa = new SignatarioEnvelope(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@example.com", "0".repeat(64));
        assertThrows(ResponseStatusException.class, () -> servico.configurar(pessoa, List.of("email"), List.of()));
    }
    @Test void exigeTodosOsCodigosSelecionados() {
        var pessoa = pessoa(); pessoa.setFormaDeAutenticacao(List.of("email", "whatsapp"));
        var verificado = new DesafioAssinatura(UUID.randomUUID(), "email", "correto"); assertTrue(verificado.verificar("correto"));
        when(desafios.findById(pessoa.getId() + ":email")).thenReturn(Optional.of(verificado));
        when(desafios.findById(pessoa.getId() + ":whatsapp")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> servico.exigirVerificacao(pessoa));
        when(desafios.findById(pessoa.getId() + ":whatsapp")).thenReturn(Optional.of(verificado));
        assertDoesNotThrow(() -> servico.exigirVerificacao(pessoa));
    }
    @Test void limitaTentativasENaoReutilizaCodigo() {
        var desafio = new DesafioAssinatura(UUID.randomUUID(), "email", "correto");
        for (int i = 0; i < 5; i++) assertFalse(desafio.verificar("errado"));
        assertFalse(desafio.verificar("correto")); assertFalse(desafio.valido());
        var outro = new DesafioAssinatura(UUID.randomUUID(), "email", "correto");
        assertTrue(outro.verificar("correto")); assertFalse(outro.verificar("correto")); assertTrue(outro.valido());
    }

    @Test void emiteCodigoSemGuardarTextoAbertoEValidaPeloMesmoCanal() throws Exception {
        String token = "a".repeat(43);
        var pessoa = prepararConvite(token);
        var codigo = org.mockito.ArgumentCaptor.forClass(String.class);
        var entidade = org.mockito.ArgumentCaptor.forClass(DesafioAssinatura.class);
        servico.enviarCodigo(token, "email");
        verify(entrega).enviar(eq("email"), eq("ana@example.com"), eq("codigo"), codigo.capture());
        verify(desafios).save(entidade.capture());
        assertTrue(codigo.getValue().matches("[0-9]{6}"));
        when(desafios.bloquear(pessoa.getId() + ":email")).thenReturn(Optional.of(entidade.getValue()));
        assertFalse(servico.verificarCodigo(token, "email", "inválido"));
        assertTrue(servico.verificarCodigo(token, "email", codigo.getValue()));
        assertFalse(servico.verificarCodigo(token, "email", codigo.getValue()));
    }
    @Test void impedeReenvioImediatoECanaisNaoSolicitados() throws Exception {
        String token = "b".repeat(43);
        var pessoa = prepararConvite(token);
        when(desafios.bloquear(pessoa.getId() + ":email")).thenReturn(Optional.of(new DesafioAssinatura(UUID.randomUUID(), "email", "hash")));
        assertThrows(ResponseStatusException.class, () -> servico.enviarCodigo(token, "email"));
        assertThrows(ResponseStatusException.class, () -> servico.enviarCodigo(token, "whatsapp"));
        verifyNoInteractions(entrega);
    }
    @Test void impedeNotificacoesDeOutroCriador() throws Exception {
        var pessoa = prepararConvite("c".repeat(43));
        assertThrows(ResponseStatusException.class, () -> servico.notificar(pessoa.getEnvelopeId(), "intruso", List.of("c".repeat(43)), "https://firma.example"));
        verifyNoInteractions(entrega);
    }
    @Test void codigoExpiradoNaoAutorizaAssinatura() throws Exception {
        var pessoa = pessoa(); pessoa.setFormaDeAutenticacao(List.of("email"));
        var desafio = new DesafioAssinatura(UUID.randomUUID(), "email", "correto"); assertTrue(desafio.verificar("correto"));
        var campo = DesafioAssinatura.class.getDeclaredField("expiraEm"); campo.setAccessible(true);
        campo.set(desafio, java.time.Instant.now().minusSeconds(1));
        when(desafios.findById(pessoa.getId() + ":email")).thenReturn(Optional.of(desafio));
        assertThrows(ResponseStatusException.class, () -> servico.exigirVerificacao(pessoa));
    }

    @Test void falhaDeEntregaMantemResultadoEHistoricoCoerentes() throws Exception {
        String token = "d".repeat(43);
        var pessoa = prepararConvite(token); pessoa.setFormaDeNotificacao(List.of("email"));
        doThrow(new IllegalStateException("indisponível")).when(entrega).enviar(anyString(), anyString(), eq("convite"), anyString());
        var resultado = servico.notificar(pessoa.getEnvelopeId(), "dono", List.of(token), "https://firma.example");
        assertFalse(resultado.getFirst().enviado());
        var evento = org.mockito.ArgumentCaptor.forClass(SignatarioHistorico.class);
        verify(historicos).save(evento.capture());
        assertEquals("falha_entrega", evento.getValue().getStatus());
        assertEquals("convite_nao_enviado_email", evento.getValue().getAcao());
    }
}
