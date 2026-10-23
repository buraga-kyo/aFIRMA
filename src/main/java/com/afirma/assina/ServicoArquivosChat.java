package com.afirma.assina;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoArquivosChat {
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final ChatTransporte transporte;
	private final ChatServico chat;
	private final Path diretorio;

	public ServicoArquivosChat(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			ChatTransporte transporte, ChatServico chat,
			@Value("${APP_CHAT_ARQUIVOS_DIR:./chat-arquivos}") String diretorio) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.transporte = transporte;
		this.chat = chat;
		this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	private Map<String, Object> atendimento(UUID id, Authentication usuario, boolean escrita) {
		List<Map<String, Object>> registros = banco.queryForList("""
			SELECT a.id,a.empresa_id,a.canal_id,a.atendente_id,a.setor_id,a.estado,c.telefone
			FROM chat_atendimento a JOIN chat_contato c ON c.id=a.contato_id
			WHERE a.id=:id
			""", dados("id", id));
		if (registros.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		Map<String, Object> ticket = registros.getFirst();
		boolean gestor = usuario.getAuthorities().stream().anyMatch(
				permissao -> "SCOPE_chat.manage".equals(permissao.getAuthority()));
		if (!gestor) {
			List<Map<String, Object>> agentes = banco.queryForList("""
				SELECT id FROM chat_atendente WHERE identidade=:identidade
				AND empresa_id=:empresa AND ativo
				""", dados("identidade", usuario.getName(), "empresa", ticket.get("empresa_id")));
			if (agentes.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
			long agente = ((Number) agentes.getFirst().get("id")).longValue();
			boolean responsavel = ticket.get("atendente_id") != null
					&& ((Number) ticket.get("atendente_id")).longValue() == agente;
			boolean pendenteDoSetor = !escrita && "PENDENTE".equals(ticket.get("estado"))
					&& (ticket.get("setor_id") == null || Boolean.TRUE.equals(banco.queryForObject("""
						SELECT EXISTS(SELECT 1 FROM chat_atendente_setor WHERE atendente_id=:agente
						AND setor_id=:setor AND empresa_id=:empresa)
						""", dados("agente", agente, "setor", ticket.get("setor_id"),
							"empresa", ticket.get("empresa_id")), Boolean.class)));
			if (!responsavel && !pendenteDoSetor) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		if (escrita && !"EM_ANDAMENTO".equals(ticket.get("estado")))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Atendimento não está em andamento");
		return ticket;
	}

	public Map<String, Object> enviar(UUID atendimento, MultipartFile arquivo, String legenda,
			Authentication usuario) {
		Map<String, Object> ticket = atendimento(atendimento, usuario, true);
		if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > 15 * 1024 * 1024)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido ou grande demais");
		String nome = arquivo.getOriginalFilename();
		String tipo = arquivo.getContentType();
		if (nome == null || nome.isBlank() || nome.length() > 255 || tipo == null
				|| !tipo.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")
				|| legenda != null && legenda.length() > 4096)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, tipo ou legenda inválido");
		UUID arquivoId = UUID.randomUUID();
		Path destino = diretorio.resolve(arquivoId.toString());
		byte[] conteudo;
		try {
			conteudo = arquivo.getBytes();
			Files.createDirectories(diretorio);
			Files.setPosixFilePermissions(diretorio, PosixFilePermissions.fromString("rwx------"));
			Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW);
			Files.setPosixFilePermissions(destino, PosixFilePermissions.fromString("rw-------"));
		} catch (IOException erro) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Arquivo não pôde ser salvo", erro);
		}
		long mensagem;
		try {
			mensagem = transacoes.execute(estado -> {
				banco.update("""
					INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento)
					VALUES (:id,:empresa,:nome,:tipo,:tamanho,:chave)
					""", dados("id", arquivoId, "empresa", ticket.get("empresa_id"), "nome", nome,
						"tipo", tipo, "tamanho", conteudo.length, "chave", arquivoId.toString()));
				return ((Number) banco.queryForMap("""
					INSERT INTO chat_mensagem(empresa_id,atendimento_id,canal_id,sentido,
					estado,texto,tipo,origem,arquivo_id)
					VALUES (:empresa,:atendimento,:canal,'SAIDA','ENVIANDO',:legenda,:tipo,'ATENDENTE',:arquivo)
					RETURNING id
					""", dados("empresa", ticket.get("empresa_id"), "atendimento", atendimento,
						"canal", ticket.get("canal_id"), "legenda", legenda,
						"tipo", tipo.substring(0, tipo.indexOf('/')).toUpperCase(),
						"arquivo", arquivoId)).get("id")).longValue();
			});
		} catch (RuntimeException erro) {
			try { Files.deleteIfExists(destino); } catch (IOException ignorado) { }
			throw erro;
		}
		try {
			String externo = transporte.enviarArquivo((UUID) ticket.get("canal_id"),
					(String) ticket.get("telefone"), nome, tipo, conteudo, legenda);
			transacoes.executeWithoutResult(estado -> {
				banco.update("""
					UPDATE chat_mensagem SET estado='ENVIADA',identificador_externo=:externo WHERE id=:id
					""", dados("id", mensagem, "externo", externo));
				banco.update("""
					UPDATE chat_atendimento SET atualizado_em=NOW(),
					primeira_resposta_em=COALESCE(primeira_resposta_em,NOW()) WHERE id=:id
					""", dados("id", atendimento));
			});
			return Map.of("mensagemId", mensagem, "arquivoId", arquivoId,
					"estado", "ENVIADA", "identificadorExterno", externo);
		} catch (RuntimeException erro) {
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_mensagem SET estado='FALHOU' WHERE id=:id
				""", dados("id", mensagem)));
			throw erro;
		}
	}

	public record Arquivo(String nome, String tipo, byte[] conteudo) {}

	public void receber(UUID canal, String externo, String telefone, String nomeContato,
			String legenda, String nomeArquivo, String tipoMime, String codificado) {
		if (codificado == null || codificado.length() > 21 * 1024 * 1024
				|| tipoMime == null || !tipoMime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anexo inválido");
		byte[] conteudo;
		try { conteudo = Base64.getDecoder().decode(codificado); }
		catch (IllegalArgumentException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anexo inválido", erro);
		}
		if (conteudo.length == 0 || conteudo.length > 15 * 1024 * 1024)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anexo grande demais");
		String arquivoNome = nomeArquivo == null || nomeArquivo.isBlank() ? "anexo" : nomeArquivo;
		if (arquivoNome.length() > 255) arquivoNome = arquivoNome.substring(0, 255);
		chat.receber(canal, externo, telefone, nomeContato,
				legenda == null || legenda.isBlank() ? "[anexo]" : legenda);
		Map<String, Object> mensagem = banco.queryForMap("""
			SELECT id,empresa_id,arquivo_id FROM chat_mensagem
			WHERE canal_id=:canal AND identificador_externo=:externo
			""", dados("canal", canal, "externo", externo));
		if (mensagem.get("arquivo_id") != null) return;
		UUID arquivoId = UUID.nameUUIDFromBytes((canal + ":" + externo).getBytes(StandardCharsets.UTF_8));
		Path destino = diretorio.resolve(arquivoId.toString());
		try {
			Files.createDirectories(diretorio);
			Files.setPosixFilePermissions(diretorio, PosixFilePermissions.fromString("rwx------"));
			if (!Files.exists(destino)) {
				Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW);
				Files.setPosixFilePermissions(destino, PosixFilePermissions.fromString("rw-------"));
			}
		} catch (IOException erro) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Anexo não pôde ser salvo", erro);
		}
		String nomeFinal = arquivoNome;
		transacoes.executeWithoutResult(estado -> {
			banco.update("""
				INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento)
				VALUES (:id,:empresa,:nome,:tipo,:tamanho,:chave) ON CONFLICT DO NOTHING
				""", dados("id", arquivoId, "empresa", mensagem.get("empresa_id"),
					"nome", nomeFinal, "tipo", tipoMime, "tamanho", conteudo.length,
					"chave", arquivoId.toString()));
			banco.update("""
				UPDATE chat_mensagem SET arquivo_id=:arquivo,tipo=:tipo
				WHERE id=:mensagem AND empresa_id=:empresa
				""", dados("arquivo", arquivoId,
					"tipo", tipoMime.substring(0, tipoMime.indexOf('/')).toUpperCase(),
					"mensagem", mensagem.get("id"), "empresa", mensagem.get("empresa_id")));
		});
	}

	public Arquivo baixar(UUID id, Authentication usuario) {
		List<Map<String, Object>> registros = banco.queryForList("""
			SELECT arquivo.nome,arquivo.tipo_mime,arquivo.chave_armazenamento,
			mensagem.atendimento_id FROM chat_arquivo arquivo JOIN chat_mensagem mensagem
			ON mensagem.arquivo_id=arquivo.id WHERE arquivo.id=:id
			""", dados("id", id));
		if (registros.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		Map<String, Object> registro = registros.getFirst();
		atendimento((UUID) registro.get("atendimento_id"), usuario, false);
		try {
			Path origem = diretorio.resolve((String) registro.get("chave_armazenamento")).normalize();
			if (!origem.startsWith(diretorio)) throw new IOException("Chave de arquivo inválida");
			return new Arquivo((String) registro.get("nome"),
					(String) registro.get("tipo_mime"), Files.readAllBytes(origem));
		} catch (IOException erro) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo indisponível", erro);
		}
	}
}
