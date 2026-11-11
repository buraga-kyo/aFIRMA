package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class FiltroAcessoConviteTest {
	private final String token = "A".repeat(43);
	private final String segredo = "B".repeat(43);
	private MockHttpServletRequest requisicao(String metodo, String sufixo) {
		var requisicao = new MockHttpServletRequest(metodo, "/api/convites/" + token + sufixo);
		requisicao.setServletPath(requisicao.getRequestURI());
		requisicao.setServerName("127.0.0.1"); requisicao.setServerPort(8080);
		return requisicao;
	}
	private FiltroAcessoConvite filtro(ServicoAutenticacaoConvites autenticacao) {
		var ambiente = new MockEnvironment(); ambiente.setActiveProfiles("local-test");
		return new FiltroAcessoConvite(autenticacao, new ObjectMapper(), ambiente);
	}

	@Test void todosOsRecursosSensiveisExigemSessao() throws Exception {
		for (String sufixo : new String[] {"", "/pdf", "/paginas/1", "/final", "/verificacao"}) {
			var autenticacao = mock(ServicoAutenticacaoConvites.class);
			when(autenticacao.exigirSessao(token, null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Confirme o e-mail"));
			var resposta = new MockHttpServletResponse();
			AtomicBoolean encaminhada = new AtomicBoolean();
			filtro(autenticacao).doFilter(requisicao("GET", sufixo), resposta, (entrada, saida) -> encaminhada.set(true));
			assertEquals(401, resposta.getStatus(), sufixo); assertFalse(encaminhada.get());
			assertEquals("no-store", resposta.getHeader("Cache-Control"));
		}
	}
	@Test void origemExternaNaoEnviaCodigoENaoRegistraAceite() throws Exception {
		for (String sufixo : new String[] {"/codigo", "/autenticar", "/assinar", "/recusar", "/sair"}) {
			var autenticacao = mock(ServicoAutenticacaoConvites.class);
			var requisicao = requisicao("POST", sufixo); requisicao.addHeader("Origin", "https://origem-externa.example");
			var resposta = new MockHttpServletResponse();
			filtro(autenticacao).doFilter(requisicao, resposta, (entrada, saida) -> fail("A requisição não deveria ser encaminhada"));
			assertEquals(403, resposta.getStatus(), sufixo); verifyNoInteractions(autenticacao);
		}
	}
	@Test void cookieValidoVinculaASessaoAoPedido() throws Exception {
		var autenticacao = mock(ServicoAutenticacaoConvites.class);
		var sessao = new ServicoAutenticacaoConvites.Sessao(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(60));
		when(autenticacao.exigirSessao(token, segredo)).thenReturn(sessao);
		var requisicao = requisicao("POST", "/assinar"); requisicao.setCookies(new Cookie(FiltroAcessoConvite.NOME_COOKIE, segredo));
		requisicao.addHeader("Origin", "http://127.0.0.1:8080");
		AtomicBoolean encaminhada = new AtomicBoolean();
		filtro(autenticacao).doFilter(requisicao, new MockHttpServletResponse(), (entrada, saida) -> encaminhada.set(true));
		assertTrue(encaminhada.get()); assertSame(sessao, requisicao.getAttribute(FiltroAcessoConvite.ATRIBUTO_SESSAO));
	}
	@Test void cookiesDuplicadosNaoEscolhemUmaSessaoSilenciosamente() {
		var requisicao = requisicao("GET", "/pdf");
		requisicao.setCookies(new Cookie(FiltroAcessoConvite.NOME_COOKIE, segredo), new Cookie(FiltroAcessoConvite.NOME_COOKIE, "C".repeat(43)));
		assertNull(FiltroAcessoConvite.segredoSessao(requisicao));
	}
	@Test void sessaoDeProducaoUsaCookieProtegidoSemDevolverSegredoNoCorpo() throws Exception {
		var autenticacao = mock(ServicoAutenticacaoConvites.class);
		when(autenticacao.autenticar(eq(token), anyString(), any(), any())).thenReturn(new ServicoAutenticacaoConvites.Autenticado(segredo, Instant.now().plusSeconds(1800)));
		var controle = new ControleAutenticacaoConvites(autenticacao, new MockEnvironment());
		var resposta = controle.autenticar(token, new ControleAutenticacaoConvites.ConfirmacaoCodigo("12345678"), requisicao("POST", "/autenticar"));
		String cookie = resposta.getHeaders().getFirst("Set-Cookie");
		assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("SameSite=Strict"));
		assertTrue(cookie.contains("Path=/api/convites/" + token)); assertNull(resposta.getBody());
	}
	@Test void documentoNaoPodeSerEnquadradoPorOutroSite() throws Exception {
		var requisicao = new MockHttpServletRequest("GET", "/convite/" + token); requisicao.setServletPath(requisicao.getRequestURI());
		var resposta = new MockHttpServletResponse();
		new FiltroCabecalhosAssinatura().doFilter(requisicao, resposta, (entrada, saida) -> {});
		assertEquals("DENY", resposta.getHeader("X-Frame-Options"));
		assertTrue(resposta.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
		assertEquals("no-referrer", resposta.getHeader("Referrer-Policy"));
	}
	@Test void carteiraDesabilitadaNaoContornaOFluxoSuportado() {
		var servico = mock(ServicoAssinaturaBlockchain.class);
		var controle = new ControleAssinaturaBlockchain(servico, mock(PaginaAssinaturaEmpresa.class));
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(ResponseStatusException.class, () ->
				controle.concluir(new ControleAssinaturaBlockchain.PedidoConclusao(UUID.randomUUID(), null, null))).getStatusCode());
		verifyNoInteractions(servico);
	}

}
