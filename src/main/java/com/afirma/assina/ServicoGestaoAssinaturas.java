package com.afirma.assina;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoGestaoAssinaturas {
	private final RepositorioEnvelopeAssinatura envelopes;
	private final RepositorioSignatarioEnvelope participantes;
	private final JdbcTemplate banco;
	private final ServicoEvidenciasAssinatura evidencias;
	private final ServicoAutenticacaoConvites autenticacao;

	public ServicoGestaoAssinaturas(RepositorioEnvelopeAssinatura envelopes, RepositorioSignatarioEnvelope participantes,
			JdbcTemplate banco, ServicoEvidenciasAssinatura evidencias, ServicoAutenticacaoConvites autenticacao) {
		this.envelopes = envelopes;
		this.participantes = participantes;
		this.banco = banco;
		this.evidencias = evidencias;
		this.autenticacao = autenticacao;
	}

	@Transactional
	public void cancelar(UUID identificador, String criador, String motivo) {
		EnvelopeAssinatura envelope = proprio(identificador, criador);
		validarMotivo(motivo);
		if (envelope.getFinalizadoEm() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento finalizado não pode ser desfeito. Registre eventual distrato em outro documento");
		if (envelope.getCanceladoEm() != null) return;
		envelope.cancelar(motivo.strip(), Instant.now());
		revogarSessoes(identificador, null);
		evidencias.registrar(identificador, null, "ENVELOPE_CANCELADO", Map.of("criador", criador, "motivo", motivo.strip()));
	}

	@Transactional
	public NovoConvite substituirConvite(UUID identificador, UUID participante, String criador, String motivo) {
		EnvelopeAssinatura envelope = proprio(identificador, criador);
		validarMotivo(motivo);
		if (envelope.getCanceladoEm() != null || envelope.getExcluidoEm() != null || envelope.getFinalizadoEm() != null
				|| !Instant.now().isBefore(envelope.getExpiraEm())) throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Envelope encerrado. Não é possível gerar outro convite");
		SignatarioEnvelope pessoa = participantes.findById(participante).filter(item -> identificador.equals(item.getEnvelopeId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (pessoa.getAssinadoEm() != null || pessoa.getRecusadoEm() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Participante já concluiu sua decisão. Crie outro envelope para uma nova manifestação");
		String token = SegredosAssinatura.novoSegredo();
		pessoa.substituirConvite(SegredosAssinatura.resumo(token));
		revogarSessoes(identificador, participante);
		evidencias.registrar(identificador, participante, "CONVITE_SUBSTITUIDO", Map.of("criador", criador, "motivo", motivo.strip()));
		return new NovoConvite(token);
	}

	@Transactional
	public void recusar(String token, String motivo, ServicoAutenticacaoConvites.Sessao sessao) {
		validarMotivo(motivo);
		EnvelopeAssinatura envelope = envelopes.buscarParaFinalizar(sessao.envelope())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		SignatarioEnvelope pessoa = participantes.buscarConviteParaAssinar(SegredosAssinatura.resumo(token))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		autenticacao.conferirSessaoParaAceite(pessoa.getId(), sessao);
		if (envelope.getExcluidoEm() != null || envelope.getCanceladoEm() != null || !Instant.now().isBefore(envelope.getExpiraEm())) {
			throw new ResponseStatusException(HttpStatus.GONE, "Convite encerrado");
		}
		if (pessoa.getAssinadoEm() != null || envelope.getFinalizadoEm() != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Aceite já registrado. A recusa não desfaz um ato concluído");
		if (pessoa.getRecusadoEm() != null) return;
		pessoa.recusar(motivo.strip(), Instant.now());
		evidencias.registrar(envelope.getId(), pessoa.getId(), "CONVITE_RECUSADO", Map.of("motivo", motivo.strip(),
				"autenticacao", sessao.identificador().toString()));
	}

	@Transactional
	public void estenderPreservacao(UUID identificador, String criador, Instant limite, String motivo) {
		EnvelopeAssinatura envelope = proprio(identificador, criador);
		validarMotivo(motivo);
		if (limite == null || !limite.isAfter(Instant.now()) || limite.isAfter(Instant.now().plusSeconds(36500L * 86400))) {
			throw new ErroAssinatura("Informe uma data futura de preservação, limitada a 100 anos");
		}
		if (envelope.getReterEvidenciasAte() != null && !limite.isAfter(envelope.getReterEvidenciasAte())) {
			throw new ErroAssinatura("Esta operação somente estende o prazo de preservação");
		}
		envelope.estenderPreservacao(limite);
		evidencias.registrar(identificador, null, "PRESERVACAO_ESTENDIDA", Map.of("criador", criador, "preservarAte", limite.toString(), "motivo", motivo.strip()));
	}

	@Transactional(rollbackFor = Exception.class)
	public void anexarRelatorioExterno(UUID identificador, String criador, MultipartFile relatorio,
			String resumoDocumento, String referenciaConsulta, String resultadoDeclarado) throws Exception {
		EnvelopeAssinatura envelope = proprio(identificador, criador);
		if (envelope.getFinalizadoEm() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalize o documento antes de anexar o relatório");
		if (referenciaConsulta == null || referenciaConsulta.isBlank() || referenciaConsulta.length() > 500
				|| !java.util.Set.of("APROVADO", "REPROVADO", "INDETERMINADO").contains(resultadoDeclarado)) {
			throw new ErroAssinatura("Informe a referência da consulta externa e o resultado declarado");
		}
		var pdfFinal = evidencias.arquivos(identificador).stream().filter(arquivo -> "PDF_FINAL".equals(arquivo.tipo())).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "PDF final sem versão preservada"));
		if (!pdfFinal.resumoSha256().equals(resumoDocumento)) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"O relatório deve se referir ao resumo SHA-256 do PDF final preservado");
		if (relatorio == null || relatorio.isEmpty() || relatorio.getSize() > 5 * 1024 * 1024) throw new ErroAssinatura("O relatório deve ter até 5 MB");
		byte[] conteudo;
		try (var entrada = relatorio.getInputStream()) { conteudo = entrada.readNBytes(5 * 1024 * 1024 + 1); }
		if (conteudo.length > 5 * 1024 * 1024) throw new ErroAssinatura("O relatório deve ter até 5 MB");
		// Guarda o documento original do avaliador. Um resultado digitado não é convertido em aprovação automática.
		UUID arquivo = evidencias.guardarArquivo(identificador, null, "RELATORIO_EXTERNO", "relatorio-externo.bin", "application/octet-stream", conteudo, null);
		evidencias.registrar(identificador, null, "RELATORIO_EXTERNO_ANEXADO", Map.of("criador", criador,
				"resumoDocumento", resumoDocumento, "arquivo", arquivo.toString(), "referenciaConsulta", referenciaConsulta,
				"resultadoDeclarado", resultadoDeclarado, "verificacaoAutomatica", false));
	}

	private void revogarSessoes(UUID envelope, UUID participante) {
		String condicao = participante == null ? "signatario_id IN (SELECT id FROM signatario_envelope WHERE envelope_id=?)" : "signatario_id=?";
		Object chave = participante == null ? envelope : participante;
		banco.update("UPDATE sessao_convite SET revogada_em=? WHERE revogada_em IS NULL AND " + condicao, Timestamp.from(Instant.now()), chave);
		banco.update("UPDATE autenticacao_convite SET resumo_codigo=NULL,codigo_expira_em=NULL WHERE " + condicao, chave);
	}
	private EnvelopeAssinatura proprio(UUID identificador, String criador) {
		return envelopes.buscarParaFinalizar(identificador).filter(envelope -> envelope.getCriador().equals(criador))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
	private void validarMotivo(String motivo) {
		if (motivo == null || motivo.isBlank() || motivo.length() > 500) throw new ErroAssinatura("Informe o motivo, com até 500 caracteres");
	}
	public record NovoConvite(String token) { }
}
