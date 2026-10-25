package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ControleDocumentosAssinaturaTest {
	@Test
	void impedeRenomearDocumentoDeOutroUsuario() {
		var envelopes = mock(RepositorioEnvelopeAssinatura.class);
		var signatarios = mock(RepositorioSignatarioEnvelope.class);
		var planos = mock(ServicoPlanoBuscaAssineAi.class);
		var controle = new ControleDocumentosAssinatura(envelopes, signatarios, planos);
		UUID id = UUID.randomUUID();
		when(envelopes.findById(id)).thenReturn(Optional.of(new EnvelopeAssinatura(id, "Empresa",
				"contrato.pdf", "dono", new byte[] {1}, Instant.now(), Instant.now().plusSeconds(60))));
		Principal intruso = () -> "outro";
		var erro = assertThrows(ResponseStatusException.class,
				() -> controle.renomear(id, new ControleDocumentosAssinatura.AlteracaoTitulo("novo"), intruso));
		assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
		verifyNoInteractions(signatarios);
	}

	@Test
	void impedeExcluirDocumentoDeOutroUsuario() {
		var envelopes = mock(RepositorioEnvelopeAssinatura.class);
		var controle = new ControleDocumentosAssinatura(envelopes,
				mock(RepositorioSignatarioEnvelope.class), mock(ServicoPlanoBuscaAssineAi.class));
		UUID id = UUID.randomUUID();
		when(envelopes.findById(id)).thenReturn(Optional.of(new EnvelopeAssinatura(id, "Empresa",
				"contrato.pdf", "dono", new byte[] {1}, Instant.now(), Instant.now().plusSeconds(60))));
		var erro = assertThrows(ResponseStatusException.class, () -> controle.excluir(id, () -> "outro"));
		assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
	}
}
