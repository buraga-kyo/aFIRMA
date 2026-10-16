package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class SegurancaLocalTest {

	private static final String TOKEN_README = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIn0.n1fXvSU8qCLktT4SwVGcerEsfzXi8e7KVvTaT4XRSDU";

	@Test
	void aceitaTokenLocalDocumentado() {
		var decodificador = new SegurancaApi().decodificadorJwtLocal();
		assertEquals("pdf.sign", decodificador.decode(TOKEN_README).getClaimAsString("scope"));
		assertThrows(JwtException.class, () -> decodificador.decode(TOKEN_README + "x"));
	}
}
