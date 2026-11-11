package com.afirma.assina;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assineai/empresa/certificado")
public class ControleCertificadoEmpresa {
    private final IdentidadeAssineai identidade;
    private final CofreCertificadoEmpresa cofre;
    public ControleCertificadoEmpresa(IdentidadeAssineai identidade, CofreCertificadoEmpresa cofre) {
        this.identidade = identidade; this.cofre = cofre;
    }
    private Acesso acesso(HttpServletRequest requisicao) {
        String responsavel = identidade.autenticar(requisicao);
        var empresa = identidade.empresaParaAdministrar(responsavel);
        return new Acesso(BancoAssineai.id(empresa, "EmpresaId"), responsavel);
    }
    @GetMapping
    public ResponseEntity<CofreCertificadoEmpresa.Situacao> situacao(HttpServletRequest requisicao) {
        var acesso = acesso(requisicao);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(cofre.situacao(acesso.empresa()));
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CofreCertificadoEmpresa.Situacao> salvar(HttpServletRequest requisicao,
            @RequestParam MultipartFile certificado, @RequestParam String senha, @RequestParam String declaracaoPoderes,
            @RequestParam boolean assinaturaAutomatica) {
        var acesso = acesso(requisicao);
        if (!PoliticaDocumentosAssinatura.DECLARACAO_PODERES.equals(declaracaoPoderes)) {
            throw new ErroAssinatura("Confirme a autorização para cadastrar o certificado da empresa");
        }
        var situacao = cofre.salvar(acesso.empresa(), acesso.responsavel(), certificado, senha);
        identidade.salvar(acesso.responsavel(), java.util.Map.of("EmpresaAssinaturaAutomatica", assinaturaAutomatica,
                "EmpresaAutorizacaoCertificado", declaracaoPoderes));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(situacao);
    }
    @DeleteMapping
    public ResponseEntity<Void> remover(HttpServletRequest requisicao) {
        var acesso = acesso(requisicao);
        cofre.remover(acesso.empresa(), acesso.responsavel());
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
    private record Acesso(long empresa, String responsavel) { }
}
