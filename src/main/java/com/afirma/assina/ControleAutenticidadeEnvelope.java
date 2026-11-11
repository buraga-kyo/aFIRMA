package com.afirma.assina;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ControleAutenticidadeEnvelope {
    private final RepositorioEnvelopeAssinatura envelopes;
    private final ServicoEvidenciasAssinatura evidencias;
    public ControleAutenticidadeEnvelope(RepositorioEnvelopeAssinatura envelopes, ServicoEvidenciasAssinatura evidencias) {
        this.envelopes = envelopes; this.evidencias = evidencias;
    }
    @GetMapping("/api/assineai/autenticidade-envelope/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable UUID id) {
        var envelope = envelopes.findById(id).filter(registro -> registro.getExcluidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        evidencias.conferir(id);
        var original = evidencias.arquivos(id).stream().filter(arquivo -> "PDF_ORIGINAL".equals(arquivo.tipo()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String resumo = SegredosAssinatura.resumo(evidencias.lerArquivo(id, original));
        // O QR consulta somente o documento. Dados pessoais, convites e arquivos continuam protegidos.
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of(
                "DocumentoTitulo", envelope.getNomeDocumento(), "DocumentoToken", id.toString(),
                "DocumentoHashDoPDFOriginal", resumo, "originalValido", true,
                "assinado", envelope.getEmpresaAssinouEm() != null, "certificado", envelope.getFinalizadoEm() != null));
    }
}
