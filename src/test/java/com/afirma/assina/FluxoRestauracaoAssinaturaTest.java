package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(FluxoAssinaturaBancoTest.Configuracao.class)
@EnabledIfEnvironmentVariable(named = "AFIRMA_TESTE_RESTAURACAO", matches = "true")
class FluxoRestauracaoAssinaturaTest {
	@Autowired JdbcTemplate banco;
	@Autowired ServicoEvidenciasAssinatura evidencias;

	@Test void pacoteRestauradoMantemHistoricoArquivosEAssinaturas() throws Exception {
		var identificadores = banco.queryForList("""
				SELECT envelope.id FROM envelope_assinatura envelope WHERE envelope.finalizado_em IS NOT NULL
				AND envelope.criador='criador' AND EXISTS (SELECT 1 FROM arquivo_evidencia_assinatura arquivo
				WHERE arquivo.envelope_id=envelope.id AND arquivo.tipo='SELO_MANIFESTO') ORDER BY envelope.criado_em DESC
				""", UUID.class);
		assertFalse(identificadores.isEmpty(), "A restauração precisa conter documentos finalizados pelos testes anteriores");
		ByteArrayOutputStream pacote = new ByteArrayOutputStream();
		evidencias.exportar(identificadores.getFirst(), "criador", pacote);
		var arquivo = Files.createTempFile("afirma-prova-restaurada-", ".zip");
		try {
			Files.write(arquivo, pacote.toByteArray());
			var processo = new ProcessBuilder("python3", "scripts/verificar-evidencias.py", arquivo.toString(), "--verificar-pdf")
					.redirectErrorStream(true).start();
			String resultado = new String(processo.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(processo.waitFor(30, TimeUnit.SECONDS));
			assertEquals(0, processo.exitValue(), resultado);
		} finally { Files.deleteIfExists(arquivo); }
	}
}
