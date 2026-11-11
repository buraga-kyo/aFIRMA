package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/acesso")
public class ControleAcessoCompartilhado {
    private final ConfiguracaoAcessoCompartilhado configuracao;
    private final IdentidadeAssineai identidade;
    private final ServicoAcessoNavegador acessos;
    private final String resumoChave;
    private final String origemPublica;

    public ControleAcessoCompartilhado(ConfiguracaoAcessoCompartilhado configuracao,
            IdentidadeAssineai identidade, Environment ambiente, ServicoAcessoNavegador acessos) {
        this.configuracao = configuracao;
        this.identidade = identidade;
        this.acessos = acessos;
        resumoChave = ambiente.getProperty("APP_API_KEY_SHA256", "");
        origemPublica = ambiente.getProperty("ASSINEAI_URL_PUBLICA", "").replaceAll("/$", "");
    }

    @GetMapping("/configuracao")
    public ResponseEntity<?> configuracao() {
        return semCache(Map.of("duracaoMinutos", configuracao.duracaoMinutos(), "testeLocal", configuracao.testeLocal()));
    }

    @PostMapping("/teste")
    public ResponseEntity<?> teste(HttpServletRequest requisicao) {
        if (!configuracao.testeLocal() || !"127.0.0.1".equals(requisicao.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        exigirOrigem(requisicao);
        return semCache(Map.of("token", configuracao.emitirTokenTeste(), "chave", ConfiguracaoAcessoCompartilhado.CHAVE_TESTE));
    }

    @PostMapping("/conectar")
    public ResponseEntity<?> conectar(HttpServletRequest requisicao) throws Exception {
        exigirOrigem(requisicao);
        identidade.autenticar(requisicao);
        String cabecalho = requisicao.getHeader("Authorization");
        var expiracaoToken = SignedJWT.parse(cabecalho.substring(7)).getJWTClaimsSet().getExpirationTime();
        if (expiracaoToken == null || !expiracaoToken.toInstant().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado ou sem validade definida");
        }
        String chave = requisicao.getHeader("X-API-Key");
        if (chave != null && !chave.isBlank()) {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8));
            String esperado = configuracao.testeLocal()
                    ? HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                            ConfiguracaoAcessoCompartilhado.CHAVE_TESTE.getBytes(StandardCharsets.UTF_8))) : resumoChave;
            if (!MessageDigest.isEqual(HexFormat.of().formatHex(resumo).getBytes(StandardCharsets.US_ASCII),
                    esperado.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chave da API inválida");
            }
        }
        Instant limite = configuracao.limite();
        if (expiracaoToken.toInstant().isBefore(limite)) limite = expiracaoToken.toInstant();
        String prazoAnterior = requisicao.getHeader("X-Acesso-Expira-Em");
        if (prazoAnterior != null) {
            try {
                Instant anterior = Instant.ofEpochMilli(Long.parseLong(prazoAnterior));
                if (!anterior.isAfter(Instant.now())) throw new IllegalArgumentException();
                if (anterior.isBefore(limite)) limite = anterior;
            } catch (RuntimeException erro) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A conexão expirou. Reconecte pelo rodapé");
            }
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Set-Cookie", acessos.abrir(requisicao, cabecalho.substring(7), chave, limite))
                .body(Map.of("expiraEm", limite.toEpochMilli(), "duracaoMinutos", configuracao.duracaoMinutos()));
    }

    @GetMapping("/sessao")
    public ResponseEntity<?> sessao(HttpServletRequest requisicao) {
        var acesso = acessos.exigir(requisicao);
        return semCache(Map.of("expiraEm", acesso.expiraEm().toEpochMilli()));
    }

    @PostMapping("/sair")
    public ResponseEntity<?> sair(HttpServletRequest requisicao) {
        exigirOrigem(requisicao);
        return ResponseEntity.noContent().header("Cache-Control", "no-store")
                .header("Set-Cookie", acessos.encerrar(requisicao)).build();
    }

    private void exigirOrigem(HttpServletRequest requisicao) {
        String origem = requisicao.getHeader("Origin");
        String esperada = origemPublica.isBlank() ? requisicao.getScheme() + "://" + requisicao.getHeader("Host") : origemPublica;
        if (origem == null || !origem.equals(esperada)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conecte pelo rodapé da aplicação");
        }
    }

    private ResponseEntity<?> semCache(Object dados) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dados);
    }
}
