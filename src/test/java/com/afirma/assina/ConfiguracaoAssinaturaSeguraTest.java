package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfiguracaoAssinaturaSeguraTest {
	@Test void certificadoVencidoDependeDeConfiguracaoExplicita() {
		var ambiente = new MockEnvironment();
		assertFalse(new ConfiguracaoAssinaturaSegura(ambiente).permitirCertificadoVencido());
		ambiente.withProperty("APP_PERMITIR_CERTIFICADO_VENCIDO", "true");
		assertTrue(new ConfiguracaoAssinaturaSegura(ambiente).permitirCertificadoVencido());
	}
	@Test void configuracaoMalformadaNaoRevelaSegredosNaMensagem() {
		String segredo = "segredo-que-nao-pode-aparecer-nos-logs";
		var ambiente = new MockEnvironment().withProperty("ASSINEAI_CHAVES_EVIDENCIAS", "{\"principal\":\"" + segredo);
		var erro = assertThrows(IllegalArgumentException.class, () -> new SegredosAssinatura(ambiente));
		assertFalse(erro.getMessage().contains(segredo));
	}
}
