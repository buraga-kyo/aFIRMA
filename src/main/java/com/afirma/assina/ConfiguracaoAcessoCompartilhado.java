package com.afirma.assina;

import java.time.Instant;
import java.util.Date;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracaoAcessoCompartilhado {
    public static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";
    private final int duracaoMinutos;
    private final boolean testeLocal;

    public ConfiguracaoAcessoCompartilhado(Environment ambiente) {
        duracaoMinutos = ambiente.getProperty("APP_SESSAO_ACESSO_MINUTOS", Integer.class, 30);
        if (duracaoMinutos < 1 || duracaoMinutos > 1440) {
            throw new IllegalArgumentException("APP_SESSAO_ACESSO_MINUTOS deve estar entre 1 e 1440");
        }
        testeLocal = ambiente.matchesProfiles("local-test");
    }

    public int duracaoMinutos() { return duracaoMinutos; }
    public boolean testeLocal() { return testeLocal; }
    public Instant limite() { return Instant.now().plusSeconds(duracaoMinutos * 60L); }

    public String emitirTokenTeste() {
        if (!testeLocal) throw new IllegalStateException("Credenciais locais indisponíveis");
        Instant agora = Instant.now();
        var dados = new JWTClaimsSet.Builder()
                .issuer("http://127.0.0.1:8080/local-test").subject("teste-local")
                .audience("afirma.local-test").issueTime(Date.from(agora))
                .expirationTime(Date.from(agora.plusSeconds(duracaoMinutos * 60L)))
                .claim("scope", "pdf.sign chat.use chat.manage").build();
        try {
            var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), dados);
            token.sign(new MACSigner("afirma-teste-local-2026-segredo-jwt-hs256-exclusivo"));
            return token.serialize();
        } catch (com.nimbusds.jose.JOSEException erro) {
            throw new IllegalStateException("Não foi possível emitir o acesso local");
        }
    }
}
