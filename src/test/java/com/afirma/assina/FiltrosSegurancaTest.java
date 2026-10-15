package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltrosSegurancaTest {

	@Test
	void rejeitaChaveApiIncorreta() throws Exception {
		String chave = "chave-de-teste-com-mais-de-trinta-e-dois-caracteres";
		String resumo = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(chave.getBytes(StandardCharsets.UTF_8)));
		FiltroChaveApi filtro = new FiltroChaveApi(resumo);
		MockHttpServletRequest requisicao = requisicao();
		requisicao.addHeader("X-API-Key", "outra-chave-com-mais-de-trinta-e-dois-caracteres");
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		filtro.doFilter(requisicao, resposta, new MockFilterChain());
		assertEquals(401, resposta.getStatus());
	}

	@Test
	void aceitaChaveApiCorreta() throws Exception {
		String chave = "chave-de-teste-com-mais-de-trinta-e-dois-caracteres";
		String resumo = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(chave.getBytes(StandardCharsets.UTF_8)));
		FiltroChaveApi filtro = new FiltroChaveApi(resumo);
		MockHttpServletRequest requisicao = requisicao();
		requisicao.addHeader("X-API-Key", chave);
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		MockFilterChain cadeia = new MockFilterChain();
		filtro.doFilter(requisicao, resposta, cadeia);
		assertEquals(200, resposta.getStatus());
		assertEquals(requisicao, cadeia.getRequest());
	}

	@Test
	void bloqueiaAcimaDoLimiteDeRequisicoes() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class))).thenReturn(31L);
		FiltroLimitesAssinatura filtro = new FiltroLimitesAssinatura(redis, 30, 2);
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		filtro.doFilter(requisicao(), resposta, new MockFilterChain());
		assertEquals(429, resposta.getStatus());
		assertEquals("60", resposta.getHeader("Retry-After"));
	}

	@Test
	void permiteRequisicaoDentroDoLimite() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class))).thenReturn(1L);
		FiltroLimitesAssinatura filtro = new FiltroLimitesAssinatura(redis, 30, 2);
		MockHttpServletRequest requisicao = requisicao();
		MockFilterChain cadeia = new MockFilterChain();
		filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);
		assertEquals(requisicao, cadeia.getRequest());
	}

	@Test
	void falhaFechadoQuandoRedisFalha() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.execute(any(DefaultRedisScript.class), anyList(), any(String.class)))
				.thenThrow(new IllegalStateException("Redis indisponível"));
		FiltroLimitesAssinatura filtro = new FiltroLimitesAssinatura(redis, 30, 2);
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		filtro.doFilter(requisicao(), resposta, new MockFilterChain());
		assertEquals(503, resposta.getStatus());
	}

	private MockHttpServletRequest requisicao() {
		MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", "/api/pdfs/assinar");
		requisicao.setServletPath("/api/pdfs/assinar");
		requisicao.setRemoteAddr("192.0.2.1");
		return requisicao;
	}
}
