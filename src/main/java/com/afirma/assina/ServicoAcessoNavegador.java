package com.afirma.assina;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoAcessoNavegador {
    private static final String NOME_COOKIE = "afirma_acesso_navegador";
    private final ConcurrentHashMap<String, Acesso> acessos = new ConcurrentHashMap<>();
    private final IdentidadeAssineai identidade;
    private final ConfiguracaoAcessoCompartilhado configuracao;

    public ServicoAcessoNavegador(IdentidadeAssineai identidade, ConfiguracaoAcessoCompartilhado configuracao) {
        this.identidade = identidade;
        this.configuracao = configuracao;
    }

    public String abrir(HttpServletRequest requisicao, String token, String chave, Instant expiraEm) {
        encerrar(requisicao);
        acessos.entrySet().removeIf(item -> !item.getValue().expiraEm().isAfter(Instant.now()));
        if (acessos.size() >= 10000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível abrir outra conexão agora");
        String segredo = SegredosAssinatura.novoSegredo();
        acessos.put(SegredosAssinatura.resumo(segredo), new Acesso(token, chave, expiraEm));
        return cookie(segredo, Duration.between(Instant.now(), expiraEm)).toString();
    }

    public Acesso exigir(HttpServletRequest requisicao) {
        String segredo = segredo(requisicao);
        String resumo = segredo == null ? null : SegredosAssinatura.resumo(segredo);
        Acesso acesso = resumo == null ? null : acessos.get(resumo);
        if (acesso == null || !acesso.expiraEm().isAfter(Instant.now())) {
            if (resumo != null) acessos.remove(resumo);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Conecte novamente pelo rodapé do trator");
        }
        // O cookie identifica a conexão; o JWT continua sendo validado no servidor a cada entrada.
        try {
            identidade.autenticar(new HttpServletRequestWrapper(requisicao) {
                @Override public String getHeader(String nome) {
                    if ("Authorization".equalsIgnoreCase(nome)) return "Bearer " + acesso.token();
                    return super.getHeader(nome);
                }
            });
        } catch (RuntimeException erro) {
            acessos.remove(resumo);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A conexão expirou ou foi recusada");
        }
        return acesso;
    }

    public String encerrar(HttpServletRequest requisicao) {
        String segredo = segredo(requisicao);
        if (segredo != null) acessos.remove(SegredosAssinatura.resumo(segredo));
        return cookie("", Duration.ZERO).toString();
    }

    private String segredo(HttpServletRequest requisicao) {
        if (requisicao.getCookies() == null) return null;
        var encontrados = Arrays.stream(requisicao.getCookies()).filter(cookie -> NOME_COOKIE.equals(cookie.getName())).toList();
        return encontrados.size() == 1 ? encontrados.getFirst().getValue() : null;
    }

    private ResponseCookie cookie(String segredo, Duration duracao) {
        return ResponseCookie.from(NOME_COOKIE, segredo).httpOnly(true).secure(!configuracao.testeLocal())
                .sameSite("Strict").path("/").maxAge(duracao).build();
    }

    public record Acesso(String token, String chave, Instant expiraEm) { }
}
