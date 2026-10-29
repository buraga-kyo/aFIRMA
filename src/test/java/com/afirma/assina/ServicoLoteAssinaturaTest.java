package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ServicoLoteAssinaturaTest {
    private final ServicoEnvelopes envelopes = mock(ServicoEnvelopes.class);
    private final ValidadorPedidoAssinatura validador = mock(ValidadorPedidoAssinatura.class);
    private final ServicoLoteAssinatura servico = new ServicoLoteAssinatura(envelopes, validador, new ServicoCamposPdf());
    private MultipartFile arquivo(int paginas) throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int i = 0; i < paginas; i++) pdf.addPage(new PDPage());
            pdf.save(saida);
            var arquivo = new MockMultipartFile("pdfs", "original.pdf", "application/pdf", saida.toByteArray());
            when(validador.validarPdf(arquivo)).thenReturn(arquivo.getBytes());
            return arquivo;
        }
    }
    private ServicoEnvelopes.CampoNovo campo(int documento, int pagina) {
        return new ServicoEnvelopes.CampoNovo(CampoAssinatura.Tipo.ASSINATURA, pagina, .1, .2, .3, .1, documento);
    }
    private ServicoLoteAssinatura.Pedido pedido(List<ServicoEnvelopes.CampoNovo> campos) {
        return new ServicoLoteAssinatura.Pedido("Firma", "Contrato e anexo", List.of(new ServicoEnvelopes.SignatarioNovo(
            "Ana", "", null, null, null, "Associado", campos)),
            List.of(new ServicoLoteAssinatura.DocumentoNovo("Contrato"), new ServicoLoteAssinatura.DocumentoNovo("Anexo")));
    }
    @Test void conservaOriginaisEConvertePaginasLocaisSemMisturarDocumentos() throws Exception {
        var primeiro = arquivo(2); var segundo = arquivo(1);
        UUID id = UUID.randomUUID();
        when(envelopes.criarComDocumentos(any(), any(), eq("criador"), anyList())).thenReturn(new ServicoEnvelopes.Criado(id, 3, List.of()));
        servico.criar(List.of(primeiro, segundo), pedido(List.of(campo(0, 2), campo(1, 1))), "criador");
        var dados = ArgumentCaptor.forClass(ServicoEnvelopes.PedidoCriacao.class);
        var pdf = ArgumentCaptor.forClass(MultipartFile.class);
        @SuppressWarnings("unchecked")
        var fontes = ArgumentCaptor.forClass((Class<List<DocumentoAssinatura>>) (Class<?>) List.class);
        verify(envelopes).criarComDocumentos(pdf.capture(), dados.capture(), eq("criador"), fontes.capture());
        try (PDDocument conjunto = Loader.loadPDF(pdf.getValue().getBytes())) { assertEquals(3, conjunto.getNumberOfPages()); }
        assertEquals(List.of(2, 3), dados.getValue().signatarios().getFirst().campos().stream().map(ServicoEnvelopes.CampoNovo::pagina).toList());
        assertArrayEquals(primeiro.getBytes(), fontes.getValue().getFirst().getBuffer());
        assertEquals("Anexo", fontes.getValue().getLast().getTitulo());
        assertEquals(3, fontes.getValue().getLast().getPaginaInicio());
        assertNotNull(fontes.getValue().getLast().getId());
    }
    @Test void rejeitaPaginaQueExisteNoConjuntoMasNaoNoDocumento() throws Exception {
        var arquivos = List.of(arquivo(2), arquivo(1));
        assertThrows(ResponseStatusException.class, () -> servico.criar(arquivos, pedido(List.of(campo(0, 1), campo(1, 2))), "criador"));
        verifyNoInteractions(envelopes);
    }
    @Test void rejeitaDocumentoSemCampos() throws Exception {
        var arquivos = List.of(arquivo(2), arquivo(1));
        assertThrows(ResponseStatusException.class, () -> servico.criar(arquivos, pedido(List.of(campo(0, 1))), "criador"));
        verifyNoInteractions(envelopes);
    }
    @Test void rejeitaReferenciaADocumentoAusente() throws Exception {
        var arquivos = List.of(arquivo(2), arquivo(1));
        assertThrows(ResponseStatusException.class, () -> servico.criar(arquivos, pedido(List.of(campo(3, 1))), "criador"));
        verifyNoInteractions(envelopes);
    }
}
