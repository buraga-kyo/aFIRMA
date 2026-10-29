package com.afirma.assina;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
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
							: assinados > 0 || "Em Processo".equals(envelope.getStatus()) ? "Em Processo" : "Pendente";
					return new DocumentoLista(envelope.getId(), envelope.getNomeDocumento(),
							envelope.getCriadoEm(), envelope.getCriador(), status, pessoas.size(), assinados,
							pessoas.stream().map(p -> new SignatarioLista(p.getNome(), p.getEmail(),
									p.getAssinadoEm())).toList());
				}).toList();
	}

	@GetMapping(value = "/importar/modelo", produces = "text/csv")
	public ResponseEntity<byte[]> modeloImportacao() {
		String cabecalho = "titulo;data;responsavel;status;quantidadeAssinaturas;signatarios\n";
		String exemplo = "Contrato de Prestação de Serviços;25/10/2026;João da Silva;Pendente;2;joao@empresa.com,maria@empresa.com\n";
		byte[] conteudo = (cabecalho + exemplo).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"modelo_importacao.csv\"")
				.body(conteudo);
	}

	@PostMapping(value = "/importar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	@Transactional
	public ResponseEntity<String> importarDocumentos(@org.springframework.web.bind.annotation.RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo, Principal usuario) {
		if (arquivo.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio");
		}
		String nomeArquivo = arquivo.getOriginalFilename();
		if (nomeArquivo == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido");
		}
		String nomeLower = nomeArquivo.toLowerCase();
		if (!nomeLower.endsWith(".csv") && !nomeLower.endsWith(".xlsx") && !nomeLower.endsWith(".xls")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Apenas arquivos Excel ou CSV são permitidos");
		}

		if (nomeLower.endsWith(".csv")) {
			try {
				String conteudoCsv = new String(arquivo.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
				String[] linhas = conteudoCsv.split("\n");
				java.time.format.DateTimeFormatter formatador = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
				java.time.ZoneId zone = java.time.ZoneId.systemDefault();
				for (int i = 1; i < linhas.length; i++) {
					String linha = linhas[i].trim();
					if (linha.isEmpty()) continue;
					String[] colunas = linha.split(";");
					if (colunas.length >= 6) {
						String titulo = colunas[0].trim();
						String dataTexto = colunas[1].trim();
						String responsavel = colunas[2].trim();
						String signatariosRaw = colunas[5].trim();
						try {
							java.time.LocalDate dataLocal = java.time.LocalDate.parse(dataTexto, formatador);
							Instant criadoEm = dataLocal.atStartOfDay(zone).toInstant();
							UUID envelopeId = UUID.randomUUID();
							EnvelopeAssinatura env = new EnvelopeAssinatura(envelopeId, "Importação via CSV", titulo, usuario.getName(), new byte[0], criadoEm, criadoEm.plusSeconds(86400 * 30));
							envelopes.save(env);
							
							if (!signatariosRaw.isEmpty()) {
								String[] emails = signatariosRaw.split(",");
								for (String email : emails) {
									email = email.trim();
									if (!email.isEmpty()) {
										signatarios.save(new SignatarioEnvelope(UUID.randomUUID(), envelopeId, email, email, UUID.randomUUID().toString().replace("-", "")));
									}
								}
							}
						} catch (java.time.format.DateTimeParseException ex) {
							throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inválida na linha " + (i + 1) + ". O formato esperado é dd/MM/yyyy");
						}
					}
				}
			} catch (IOException e) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao ler arquivo");
			}
		}

		return ResponseEntity.ok()
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body("{\"mensagem\":\"Importação concluída com sucesso\"}");
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
