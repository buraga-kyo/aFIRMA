package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class RelacionamentosAssinaturaTest {
    private final RepositorioEnvelopeAssinatura envelopes = mock(RepositorioEnvelopeAssinatura.class);
    private final RepositorioSignatarioEnvelope pessoas = mock(RepositorioSignatarioEnvelope.class);
    private final RepositorioCampoAssinatura campos = mock(RepositorioCampoAssinatura.class);
    private final RepositorioDocumentoAssinatura documentos = mock(RepositorioDocumentoAssinatura.class);
    private final RepositorioSignatarioHistorico historicos = mock(RepositorioSignatarioHistorico.class);
    private final ValidadorPedidoAssinatura validador = mock(ValidadorPedidoAssinatura.class);
    private final ServicoEnvelopes servico = new ServicoEnvelopes(envelopes, pessoas, campos, validador, new ServicoCamposPdf(),
        mock(ServicoAssinaturaPdf.class), mock(ArmazenamentoMinio.class), mock(ServicoAutenticacaoAssinatura.class), documentos, historicos);

    private MockMultipartFile pdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage()); pdf.save(saida);
            var arquivo = new MockMultipartFile("pdf", "contrato.pdf", "application/pdf", saida.toByteArray());
            when(validador.validarPdf(arquivo)).thenReturn(arquivo.getBytes());
            return arquivo;
        }
    }
    @Test void apiDePdfUnicoTambemRegistraDocumentoCampoEHistorico() throws Exception {
        var pedido = new ServicoEnvelopes.PedidoCriacao("Firma", "Contrato", List.of(new ServicoEnvelopes.SignatarioNovo(
            "Ana", "", null, null, null, "Empresa", List.of(new ServicoEnvelopes.CampoNovo(CampoAssinatura.Tipo.ASSINATURA, 1, .1, .2, .3, .1)))));
        var criado = servico.criar(pdf(), pedido, "dono");
        var documento = ArgumentCaptor.forClass(DocumentoAssinatura.class);
        var campo = ArgumentCaptor.forClass(CampoAssinatura.class);
        var pessoa = ArgumentCaptor.forClass(SignatarioEnvelope.class);
        var evento = ArgumentCaptor.forClass(SignatarioHistorico.class);
        verify(documentos).save(documento.capture()); verify(campos).save(campo.capture());
        verify(pessoas).save(pessoa.capture()); verify(historicos).save(evento.capture());
        assertEquals(criado.identificador(), documento.getValue().getEnvelopeId());
        assertEquals(criado.identificador(), campo.getValue().getEnvelopeId());
        assertEquals(documento.getValue().getId(), campo.getValue().getDocumentoId());
        assertEquals(pessoa.getValue().getId(), evento.getValue().getSignatarioId());
        assertEquals("convite_criado", evento.getValue().getAcao());
        assertNull(evento.getValue().getEnderecoIp());
        assertFalse(pessoa.getValue().getAssinou());
    }
    @Test void acessoAtualizaContadorSemReabrirSituacaoAssinada() {
        var pessoa = new SignatarioEnvelope(UUID.randomUUID(), UUID.randomUUID(), "Ana", "", "a".repeat(64));
        pessoa.registrarAcesso("Navegador"); pessoa.registrarAcesso("Navegador");
        assertEquals("2", pessoa.getQuantidadeDeAcessosNoLinkDeAssinatura());
        assertEquals("Visualizou o documento", pessoa.getSituacaoAssinatura());
        Instant agora = Instant.now(); pessoa.assinar(new byte[]{1}, null, null, "127.0.0.1", agora);
        pessoa.setDispositivo("Dispositivo da assinatura"); pessoa.registrarAcesso("Outro navegador");
        assertEquals("3", pessoa.getQuantidadeDeAcessosNoLinkDeAssinatura());
        assertTrue(pessoa.getAssinou()); assertEquals("Assinado", pessoa.getSituacaoAssinatura());
        assertEquals(agora.toString(), pessoa.getDataAssinatura());
        assertEquals("Dispositivo da assinatura", pessoa.getDispositivo());
    }
    @Test void campoNaoAceitaDocumentoDeOutraPagina() {
        var documento = new DocumentoAssinatura(UUID.randomUUID(), UUID.randomUUID(), "Anexo"); documento.setPaginas(3, 4);
        var campo = new CampoAssinatura(UUID.randomUUID(), UUID.randomUUID(), CampoAssinatura.Tipo.ASSINATURA, 1, .1, .2, .3, .1);
        assertThrows(IllegalArgumentException.class, () -> campo.vincularDocumento(documento));
    }
    @Test void consultaDeOutroCriadorNaoLeHistoricoNemDocumentos() {
        UUID id = UUID.randomUUID();
        when(envelopes.findById(id)).thenReturn(Optional.of(new EnvelopeAssinatura(id, "Firma", "contrato.pdf", "dono", new byte[]{1}, Instant.now(), Instant.now().plusSeconds(60))));
        assertThrows(ResponseStatusException.class, () -> servico.consultar(id, "outro"));
        verifyNoInteractions(historicos, documentos, pessoas);
    }
    @Test void eventosLimitamMetadadosSemInventarOrigem() {
        var evento = new SignatarioHistorico(UUID.randomUUID(), "assinado", "assinatura_confirmada", "1".repeat(100), "A".repeat(3000));
        assertEquals(64, evento.getEnderecoIp().length()); assertEquals(1024, evento.getDispositivo().length());
        assertNotNull(evento.getCriadoEm());
    }
}
