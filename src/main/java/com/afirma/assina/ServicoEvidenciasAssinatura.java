package com.afirma.assina;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoEvidenciasAssinatura {
	private static final String RESUMO_INICIAL = "0".repeat(64);
	private final JdbcTemplate banco;
	private final ObjectMapper json;
	private final SegredosAssinatura segredos;
	private final ArmazenamentoMinio armazenamento;

	public ServicoEvidenciasAssinatura(JdbcTemplate banco, ObjectMapper json,
			SegredosAssinatura segredos, ArmazenamentoMinio armazenamento) {
		this.banco = banco;
		this.json = json;
		this.segredos = segredos;
		this.armazenamento = armazenamento;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String registrar(UUID envelope, UUID participante, String tipo, Map<String, ?> informacoes) {
		segredos.exigirConfiguracao();
		// O mesmo bloqueio serializa eventos e alterações do documento, inclusive entre instâncias da aplicação.
		banco.queryForObject("SELECT id FROM envelope_assinatura WHERE id = ? FOR UPDATE", UUID.class, envelope);
		var anteriores = banco.query("""
				SELECT sequencia,resumo_evento FROM evento_assinatura WHERE envelope_id=? ORDER BY sequencia DESC LIMIT 1
				""", (linha, indice) -> Map.entry(linha.getLong(1), linha.getString(2)), envelope);
		long sequencia = anteriores.isEmpty() ? 1 : anteriores.getFirst().getKey() + 1;
		String anterior = anteriores.isEmpty() ? RESUMO_INICIAL : anteriores.getFirst().getValue();
		Instant ocorridoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Map<String, Object> conteudo = new LinkedHashMap<>();
		conteudo.put("envelope", envelope.toString());
		conteudo.put("sequencia", sequencia);
		conteudo.put("ocorridoEm", ocorridoEm.toString());
		conteudo.put("tipo", tipo);
		conteudo.put("participante", participante == null ? null : participante.toString());
		conteudo.put("informacoes", new TreeMap<>(informacoes));
		conteudo.put("chaveSelo", segredos.identificadorChave());
		String texto = json.writeValueAsString(conteudo);
		String resumo = SegredosAssinatura.resumo(anterior + "\n" + texto);
		banco.update("""
				INSERT INTO evento_assinatura(envelope_id,sequencia,ocorrido_em,tipo,participante_id,conteudo,
				resumo_anterior,resumo_evento,selo_servidor,chave_selo) VALUES (?,?,?,?,?,?,?,?,?,?)
				""", envelope, sequencia, Timestamp.from(ocorridoEm), tipo, participante, texto,
				anterior, resumo, segredos.selar("evento-assinatura", resumo), segredos.identificadorChave());
		return resumo;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public UUID guardarArquivo(UUID envelope, UUID participante, String tipo, String nome, String mime,
			byte[] conteudo, String chaveMinio) {
		if (conteudo == null || conteudo.length == 0) throw new IllegalArgumentException("Evidência vazia");
		UUID identificador = UUID.randomUUID();
		String resumo = SegredosAssinatura.resumo(conteudo);
		banco.update("""
				INSERT INTO arquivo_evidencia_assinatura(id,envelope_id,participante_id,tipo,nome,mime,resumo_sha256,
				tamanho,dados,chave_minio,criado_em) VALUES (?,?,?,?,?,?,?,?,?,?,?)
				""", identificador, envelope, participante, tipo, nome, mime, resumo, (long) conteudo.length,
				chaveMinio == null ? conteudo : null, chaveMinio, Timestamp.from(Instant.now()));
		registrar(envelope, participante, "ARQUIVO_PRESERVADO", Map.of("arquivo", identificador.toString(),
				"tipo", tipo, "resumoSha256", resumo, "tamanho", conteudo.length, "nome", nome, "mime", mime));
		return identificador;
	}

	public List<Arquivo> arquivos(UUID envelope) {
		return banco.query("""
				SELECT id,participante_id,tipo,nome,mime,resumo_sha256,tamanho,criado_em FROM arquivo_evidencia_assinatura
				WHERE envelope_id=? ORDER BY ordem
				""", (linha, indice) -> new Arquivo(linha.getObject(1, UUID.class), linha.getObject(2, UUID.class),
				linha.getString(3), linha.getString(4), linha.getString(5), linha.getString(6), linha.getLong(7),
				linha.getTimestamp(8).toInstant()), envelope);
	}

	public List<Evento> eventos(UUID envelope) {
		return banco.query("""
				SELECT sequencia,ocorrido_em,tipo,participante_id,conteudo,resumo_anterior,resumo_evento,selo_servidor,chave_selo
				FROM evento_assinatura WHERE envelope_id=? ORDER BY sequencia
				""", (linha, indice) -> new Evento(linha.getLong(1), linha.getTimestamp(2).toInstant(), linha.getString(3),
				linha.getObject(4, UUID.class), linha.getString(5), linha.getString(6), linha.getString(7), linha.getString(8), linha.getString(9)), envelope);
	}

	public Conferencia conferir(UUID envelope) {
		List<Evento> eventos = eventos(envelope);
		String anterior = RESUMO_INICIAL;
		long sequencia = 1;
		for (Evento evento : eventos) {
			var conteudo = json.readTree(evento.conteudo());
			if (evento.sequencia() != sequencia++ || !anterior.equals(evento.resumoAnterior())
					|| !SegredosAssinatura.resumo(anterior + "\n" + evento.conteudo()).equals(evento.resumoEvento())
					|| !SegredosAssinatura.iguais(segredos.selarComChave(evento.chaveSelo(), "evento-assinatura", evento.resumoEvento()), evento.seloServidor())
					|| !evento.chaveSelo().equals(conteudo.path("chaveSelo").asText())
					|| !envelope.toString().equals(conteudo.path("envelope").asText())
					|| evento.sequencia() != conteudo.path("sequencia").asLong()
					|| !evento.tipo().equals(conteudo.path("tipo").asText())
					|| !evento.ocorridoEm().toString().equals(conteudo.path("ocorridoEm").asText())
					|| !(evento.participante() == null ? conteudo.path("participante").isNull()
							: evento.participante().toString().equals(conteudo.path("participante").asText()))) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Histórico de evidências inconsistente. Preserve os dados e investigue antes de continuar");
			}
			anterior = evento.resumoEvento();
		}
		List<Arquivo> arquivos = arquivos(envelope);
		var registrosArquivos = eventos.stream().filter(evento -> "ARQUIVO_PRESERVADO".equals(evento.tipo())).toList();
		if (arquivos.size() != registrosArquivos.size()) throw divergenciaArquivo();
		for (Arquivo arquivo : arquivos) {
			var evento = registrosArquivos.stream().filter(registro -> arquivo.identificador().toString().equals(
					json.readTree(registro.conteudo()).path("informacoes").path("arquivo").asText())).findFirst().orElseThrow(this::divergenciaArquivo);
			var metadados = json.readTree(evento.conteudo()).path("informacoes");
			if (!arquivo.resumoSha256().equals(metadados.path("resumoSha256").asText())
					|| arquivo.tamanho() != metadados.path("tamanho").asLong() || !arquivo.tipo().equals(metadados.path("tipo").asText())
					|| !arquivo.nome().equals(metadados.path("nome").asText()) || !arquivo.mime().equals(metadados.path("mime").asText())
					|| !java.util.Objects.equals(arquivo.participante(), evento.participante())) throw divergenciaArquivo();
		}
		return new Conferencia(!eventos.isEmpty(), eventos.size(), anterior);
	}

	public byte[] lerArquivo(UUID envelope, Arquivo arquivo) {
		byte[] conteudo = banco.queryForObject("SELECT dados FROM arquivo_evidencia_assinatura WHERE id=? AND envelope_id=?",
				byte[].class, arquivo.identificador(), envelope);
		if (conteudo == null) {
			String chave = banco.queryForObject("SELECT chave_minio FROM arquivo_evidencia_assinatura WHERE id=? AND envelope_id=?",
					String.class, arquivo.identificador(), envelope);
			conteudo = armazenamento.ler(chave);
		}
		if (conteudo.length != arquivo.tamanho() || !SegredosAssinatura.resumo(conteudo).equals(arquivo.resumoSha256())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Arquivo de evidência diverge do resumo preservado");
		}
		return conteudo;
	}

	public void conferirConteudoRegistrado(UUID envelope, byte[] conteudo, String tipo) {
		String resumo = SegredosAssinatura.resumo(conteudo);
		var registrados = arquivos(envelope).stream().filter(arquivo -> arquivo.tipo().equals(tipo)).toList();
		if (registrados.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Documento anterior à preservação de versões. Regularize as evidências antes de continuar");
		if (registrados.stream().noneMatch(arquivo -> arquivo.resumoSha256().equals(resumo))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Documento diverge das versões preservadas");
		}
	}

	public String avisoPrivacidade(UUID envelope) {
		var avisos = arquivos(envelope).stream().filter(arquivo -> "AVISO_PRIVACIDADE".equals(arquivo.tipo())).toList();
		if (avisos.isEmpty()) return "Documento anterior ao aviso de privacidade preservado nesta versão. Consulte o responsável antes de continuar.";
		if (avisos.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Aviso de privacidade inconsistente");
		return new String(lerArquivo(envelope, avisos.getFirst()), StandardCharsets.UTF_8);
	}

	public void conferirAceite(SignatarioEnvelope participante) {
		var registros = eventos(participante.getEnvelopeId()).stream().filter(evento ->
				"ACEITE_REGISTRADO".equals(evento.tipo()) && participante.getId().equals(evento.participante())).toList();
		if (registros.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Aceite sem registro único de evidências");
		var dados = json.readTree(registros.getFirst().conteudo()).path("informacoes");
		java.util.Map<String, String> valores = new LinkedHashMap<>();
		valores.put("nome", participante.getNome()); valores.put("email", participante.getEmail());
		valores.put("papel", participante.getPapel()); valores.put("representado", participante.getRepresentado());
		valores.put("fundamentoRepresentacao", participante.getFundamentoRepresentacao());
		valores.put("declaracao", participante.getTextoAceite()); valores.put("resumoDocumento", participante.getResumoDocumentoAceito());
		valores.put("assinadoEm", participante.getAssinadoEm() == null ? null : participante.getAssinadoEm().toString());
		valores.put("autenticadoEm", participante.getAutenticadoEm() == null ? null : participante.getAutenticadoEm().toString());
		valores.put("resumoEnvio", participante.getResumoEnvio()); valores.put("enderecoIp", participante.getEnderecoIp());
		valores.put("autenticacao", participante.getAutenticacaoId() == null ? null : participante.getAutenticacaoId().toString());
        // Aceites anteriores à coleta obrigatória continuam conferidos no seu formato original.
        if (dados.has("origemIp")) {
            valores.put("origemIp", participante.getOrigemIp()); valores.put("ipConexao", participante.getIpConexao());
            valores.put("geolocalizacao", participante.getGeolocalizacao()); valores.put("cpf", participante.getCpf());
            valores.put("rg", participante.getRg()); valores.put("whatsapp", participante.getWhatsapp());
        }
		for (var valor : valores.entrySet()) {
			boolean confere = valor.getValue() == null ? dados.path(valor.getKey()).isNull() : valor.getValue().equals(dados.path(valor.getKey()).asText());
			if (!confere) throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Os dados atuais do participante divergem do aceite preservado");
		}
	}

	public byte[] manifestoParaAssinar(UUID envelope) {
		Conferencia conferencia = conferir(envelope);
		Map<String, Object> manifesto = new LinkedHashMap<>();
		manifesto.put("formato", "afirma-evidencias-1");
		manifesto.put("envelope", envelope.toString());
		manifesto.put("quantidadeEventos", conferencia.quantidadeEventos());
		manifesto.put("resumoUltimoEvento", conferencia.resumoUltimoEvento());
		manifesto.put("arquivos", arquivos(envelope));
		return json.writeValueAsBytes(manifesto);
	}

	@Transactional(readOnly = true)
	public Resumo consultar(UUID envelope, String criador) {
		exigirCriador(envelope, criador);
		return new Resumo(conferir(envelope), arquivos(envelope));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public void exportar(UUID envelope, String criador, OutputStream destino) throws Exception {
		exigirCriador(envelope, criador);
		var conferencia = conferir(envelope);
		var arquivos = arquivos(envelope);
		if (!conferencia.historicoPresente()) throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Este documento não possui histórico preservado nesta versão do sistema");
		Map<String, Object> manifesto = new LinkedHashMap<>();
		manifesto.put("formato", "afirma-pacote-1");
		manifesto.put("envelope", envelope.toString());
		manifesto.put("conferencia", conferencia);
		manifesto.put("arquivos", arquivos);
		try (ZipOutputStream pacote = new ZipOutputStream(destino, StandardCharsets.UTF_8)) {
			acrescentar(pacote, "manifesto.json", json.writeValueAsBytes(manifesto));
			StringBuilder historico = new StringBuilder();
			for (Evento evento : eventos(envelope)) historico.append(json.writeValueAsString(evento)).append('\n');
			acrescentar(pacote, "eventos.jsonl", historico.toString().getBytes(StandardCharsets.UTF_8));
			for (Arquivo arquivo : arquivos) acrescentar(pacote, caminhoArquivo(arquivo), lerArquivo(envelope, arquivo));
			acrescentar(pacote, "LEIA-ME.txt", ("Confira os resumos de manifesto.json e a sequência de eventos.jsonl. "
					+ "O HMAC dos eventos é conferido pelo servidor e não é uma certificação independente. "
					+ "Quando finalizado, o pacote contém o manifesto e seu CMS destacado, assinados pelo certificado da empresa. "
					+ "A aprovação jurídica e a classificação da assinatura dependem do ato e das evidências disponíveis. "
					+ "O relatório externo anexado é uma declaração do responsável, sem aprovação automática pelo aFIRMA.\n")
					.getBytes(StandardCharsets.UTF_8));
			pacote.finish();
		}
	}

	public static String caminhoArquivo(Arquivo arquivo) {
		String extensao = switch (arquivo.mime()) {
			case "application/pdf" -> ".pdf";
			case "application/json" -> ".json";
			case "text/plain" -> ".txt";
			case "application/pkcs7-signature" -> ".p7s";
			case "image/png" -> ".png";
			case "image/jpeg" -> ".jpg";
			default -> ".bin";
		};
		return "arquivos/" + arquivo.identificador() + extensao;
	}

	private ResponseStatusException divergenciaArquivo() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "As versões preservadas divergem do histórico de evidências");
	}

	private void acrescentar(ZipOutputStream pacote, String caminho, byte[] conteudo) throws Exception {
		pacote.putNextEntry(new ZipEntry(caminho));
		pacote.write(conteudo);
		pacote.closeEntry();
	}

	private void exigirCriador(UUID envelope, String criador) {
		var donos = banco.queryForList("SELECT criador FROM envelope_assinatura WHERE id=?", String.class, envelope);
		// Exclusão lógica e expiração do convite não apagam o direito do criador de recuperar as provas preservadas.
		if (donos.isEmpty() || !donos.getFirst().equals(criador)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public record Arquivo(UUID identificador, UUID participante, String tipo, String nome, String mime,
			String resumoSha256, long tamanho, Instant criadoEm) { }
	public record Evento(long sequencia, Instant ocorridoEm, String tipo, UUID participante, String conteudo,
			String resumoAnterior, String resumoEvento, String seloServidor, String chaveSelo) { }
	public record Conferencia(boolean historicoPresente, long quantidadeEventos, String resumoUltimoEvento) { }
	public record Resumo(Conferencia conferencia, List<Arquivo> arquivos) { }
}
