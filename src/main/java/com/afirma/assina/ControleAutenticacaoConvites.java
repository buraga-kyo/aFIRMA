package com.afirma.assina;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convites/{token}")
public class ControleAutenticacaoConvites {
	private final ServicoAutenticacaoConvites autenticacao;
	private final boolean cookieSeguro;

	public ControleAutenticacaoConvites(ServicoAutenticacaoConvites autenticacao, Environment ambiente) {
		this.autenticacao = autenticacao;
		cookieSeguro = !ambiente.matchesProfiles("local-test");
	}

	@GetMapping("/acesso")
	public ServicoAutenticacaoConvites.Acesso acesso(@PathVariable String token, HttpServletRequest requisicao) {
		return autenticacao.consultarAcesso(token, FiltroAcessoConvite.segredoSessao(requisicao));
	}

	@PostMapping("/codigo")
	public ServicoAutenticacaoConvites.Acesso solicitarCodigo(@PathVariable String token) {
		autenticacao.solicitarCodigo(token);
		return autenticacao.consultarAcesso(token, null);
	}

	@PostMapping("/autenticar")
	public ResponseEntity<Void> autenticar(@PathVariable String token, @RequestBody ConfirmacaoCodigo pedido,
			HttpServletRequest requisicao) {
		var autenticado = autenticacao.autenticar(token, pedido.codigo(), requisicao.getRemoteAddr(), requisicao.getHeader("User-Agent"));
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie(token, autenticado.segredoSessao(), Duration.ofMinutes(30))).build();
	}

	@PostMapping("/sair")
	public ResponseEntity<Void> sair(@PathVariable String token, HttpServletRequest requisicao) {
		autenticacao.encerrar(token, FiltroAcessoConvite.segredoSessao(requisicao));
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie(token, "", Duration.ZERO)).build();
	}

	private String cookie(String token, String segredo, Duration duracao) {
		return ResponseCookie.from(FiltroAcessoConvite.NOME_COOKIE, segredo).httpOnly(true).secure(cookieSeguro)
				.sameSite("Strict").path("/api/convites/" + token).maxAge(duracao).build().toString();
	}
	public record ConfirmacaoCodigo(String codigo) { }
}
