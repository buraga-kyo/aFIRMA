package com.afirma.assina;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/envelopes/documentos")
public class ControleDocumentosAssinatura {
	private final RepositorioEnvelopeAssinatura envelopes;
	private final RepositorioSignatarioEnvelope signatarios;
	private final ServicoPlanoBuscaAssineAi planos;

	public ControleDocumentosAssinatura(RepositorioEnvelopeAssinatura envelopes,
			RepositorioSignatarioEnvelope signatarios, ServicoPlanoBuscaAssineAi planos) {
		this.envelopes = envelopes;
		this.signatarios = signatarios;
		this.planos = planos;
	}

	@PostMapping("/plano-busca")
	public ServicoPlanoBuscaAssineAi.Plano plano(@RequestBody ConsultaIA consulta) {
		return planos.interpretar(consulta.consulta());
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<DocumentoLista> listar(Principal usuario) {
		return envelopes.findResumoByCriadorAndExcluidoEmIsNullOrderByCriadoEmDesc(usuario.getName()).stream()
				.map(envelope -> {
					var pessoas = signatarios.findByEnvelopeIdOrderByNome(envelope.getId());
					long assinados = pessoas.stream().filter(p -> p.getAssinadoEm() != null).count();
					String status = envelope.getFinalizadoEm() != null ? "Assinado"
							: assinados > 0 ? "Em Processo" : "Pendente";
					return new DocumentoLista(envelope.getId(), envelope.getNomeDocumento(),
							envelope.getCriadoEm(), envelope.getCriador(), status, pessoas.size(), assinados,
							pessoas.stream().map(p -> new SignatarioLista(p.getNome(), p.getEmail(),
									p.getAssinadoEm())).toList());
				}).toList();
	}

	@PatchMapping("/{id}")
	@Transactional
	public DocumentoLista renomear(@PathVariable UUID id, @RequestBody AlteracaoTitulo alteracao,
			Principal usuario) {
		String titulo = alteracao.titulo() == null ? "" : alteracao.titulo().trim();
		if (titulo.isEmpty() || titulo.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Título deve ter entre 1 e 255 caracteres");
		}
		var envelope = proprio(id, usuario);
		envelope.renomearDocumento(titulo);
		return listar(usuario).stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
	}

	@DeleteMapping("/{id}")
	@Transactional
	public void excluir(@PathVariable UUID id, Principal usuario) {
		proprio(id, usuario).excluir(Instant.now());
	}

	private EnvelopeAssinatura proprio(UUID id, Principal usuario) {
		var envelope = envelopes.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (envelope.getExcluidoEm() != null || !envelope.getCriador().equals(usuario.getName())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return envelope;
	}

	public record AlteracaoTitulo(String titulo) { }
	public record ConsultaIA(String consulta) { }
	public record SignatarioLista(String nome, String email, Instant assinadoEm) { }
	public record DocumentoLista(UUID id, String titulo, Instant data, String responsavel,
			String status, int quantidadeAssinaturas, long assinados, List<SignatarioLista> signatarios) { }
}
