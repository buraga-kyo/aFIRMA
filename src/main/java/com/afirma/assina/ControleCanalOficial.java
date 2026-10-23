package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/canais/oficiais")
public class ControleCanalOficial {
	public record NovoCanal(String nome, String telefone, Long setorPadraoId,
			String identificadorMeta, String identificadorContaMeta, String referenciaSegredoMeta) {}
	public record EnvioModelo(String telefone, String nome, String idioma) {}

	private final NamedParameterJdbcTemplate banco;
	private final EmpresaAtual empresas;
	private final ServicoCanalOficial oficial;
	private final ChatServico chat;
	private final ObjectMapper json;
	private final byte[] segredoAplicativo;
	private final byte[] tokenVerificacao;

	public ControleCanalOficial(NamedParameterJdbcTemplate banco, EmpresaAtual empresas,
			ServicoCanalOficial oficial, ChatServico chat, ObjectMapper json,
			@Value("${APP_META_APP_SECRET:}") String segredoAplicativo,
			@Value("${APP_META_WEBHOOK_TOKEN:}") String tokenVerificacao) {
		this.banco = banco;
		this.empresas = empresas;
		this.oficial = oficial;
		this.chat = chat;
		this.json = json;
		this.segredoAplicativo = segredoAplicativo.getBytes(StandardCharsets.UTF_8);
		this.tokenVerificacao = tokenVerificacao.getBytes(StandardCharsets.UTF_8);
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	@PostMapping
	public Map<String, Object> criar(Authentication usuario, @RequestBody NovoCanal pedido) {
		long empresa = empresas.identificar(usuario);
		if (pedido.nome() == null || pedido.nome().isBlank() || pedido.nome().length() > 80
				|| pedido.telefone() == null || !pedido.telefone().matches("[0-9]{8,15}")
				|| pedido.identificadorMeta() == null || !pedido.identificadorMeta().matches("[0-9]{5,30}")
				|| pedido.referenciaSegredoMeta() == null
				|| !pedido.referenciaSegredoMeta().matches("APP_META_TOKEN_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração de canal inválida");
		if (pedido.setorPadraoId() != null) {
			Integer quantidade = banco.queryForObject("""
				SELECT count(*) FROM chat_setor WHERE id=:id AND empresa_id=:empresa AND ativo
				""", dados("id", pedido.setorPadraoId(), "empresa", empresa), Integer.class);
			if (quantidade == null || quantidade != 1)
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setor inválido");
		}
		UUID id = UUID.randomUUID();
		return banco.queryForMap("""
			INSERT INTO chat_canal(id,empresa_id,nome,telefone,setor_padrao_id,provedor,
			identificador_meta,identificador_conta_meta,referencia_segredo_meta,conexao)
			VALUES (:id,:empresa,:nome,:telefone,:setor,'META',:meta,:conta,:segredo,'CONECTADO')
			RETURNING id,empresa_id,nome,telefone,setor_padrao_id,provedor,identificador_meta,
			identificador_conta_meta,conexao,ativo
			""", dados("id", id, "empresa", empresa, "nome", pedido.nome().trim(),
				"telefone", pedido.telefone(), "setor", pedido.setorPadraoId(),
				"meta", pedido.identificadorMeta(), "conta", pedido.identificadorContaMeta(),
				"segredo", pedido.referenciaSegredoMeta()));
	}

	@PostMapping("/{canal}/modelos")
	public Map<String, String> enviarModelo(Authentication usuario, @PathVariable UUID canal,
			@RequestBody EnvioModelo pedido) {
		long empresa = empresas.identificar(usuario);
		Integer quantidade = banco.queryForObject("""
			SELECT count(*) FROM chat_canal WHERE id=:id AND empresa_id=:empresa AND provedor='META' AND ativo
			""", dados("id", canal, "empresa", empresa), Integer.class);
		if (quantidade == null || quantidade != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		return Map.of("identificador", oficial.enviarModelo(canal, pedido.telefone(),
				pedido.nome(), pedido.idioma()));
	}

	@PostMapping("/{canal}/modelos/{modelo}/publicar")
	public Map<String, Object> publicarModelo(Authentication usuario, @PathVariable UUID canal,
			@PathVariable long modelo) {
		return oficial.publicarModelo(canal, modelo, empresas.identificar(usuario));
	}

	@PostMapping("/{canal}/modelos/{modelo}/sincronizar")
	public Map<String, Object> sincronizarModelo(Authentication usuario, @PathVariable UUID canal,
			@PathVariable long modelo) {
		return oficial.sincronizarModelo(canal, modelo, empresas.identificar(usuario));
	}

	@GetMapping("/webhook")
	public String verificar(@RequestParam(name = "hub.mode", required = false) String modo,
			@RequestParam(name = "hub.verify_token", required = false) String token,
			@RequestParam(name = "hub.challenge", required = false) String desafio) {
		if (!"subscribe".equals(modo) || token == null || desafio == null
				|| tokenVerificacao.length < 32 || !MessageDigest.isEqual(tokenVerificacao,
					token.getBytes(StandardCharsets.UTF_8)))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		return desafio;
	}

	@PostMapping("/webhook")
	public void receber(@RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura,
			@RequestBody byte[] corpo) {
		if (segredoAplicativo.length < 32 || assinatura == null
				|| !assinatura.matches("sha256=[0-9a-fA-F]{64}"))
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(segredoAplicativo, "HmacSHA256"));
			byte[] esperado = mac.doFinal(corpo);
			if (!MessageDigest.isEqual(esperado, HexFormat.of().parseHex(assinatura.substring(7))))
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
			JsonNode evento = json.readTree(corpo);
			for (JsonNode entrada : evento.path("entry")) for (JsonNode alteracao : entrada.path("changes")) {
				JsonNode valor = alteracao.path("value");
				String identificador = valor.path("metadata").path("phone_number_id").asText();
				if (!identificador.matches("[0-9]{5,30}")) continue;
				var canais = banco.queryForList("""
					SELECT id FROM chat_canal WHERE identificador_meta=:identificador
					AND provedor='META' AND ativo
					""", dados("identificador", identificador));
				if (canais.isEmpty()) continue;
				UUID canal = (UUID) canais.getFirst().get("id");
				String nome = valor.path("contacts").path(0).path("profile").path("name").asText("");
				for (JsonNode mensagem : valor.path("messages")) {
					String tipo = mensagem.path("type").asText();
					String texto = "text".equals(tipo) ? mensagem.path("text").path("body").asText()
							: "[" + tipo + "]";
					String telefone = mensagem.path("from").asText();
					String externo = mensagem.path("id").asText();
					if (!telefone.matches("[0-9]{8,15}") || externo.isBlank()) continue;
					chat.receber(canal, externo, telefone, nome, texto);
					banco.update("""
						UPDATE chat_mensagem SET tipo=:tipo,dados_externos=CAST(:dados AS jsonb)
						WHERE canal_id=:canal AND identificador_externo=:externo
						""", dados("tipo", tipo.toUpperCase(), "dados", json.writeValueAsString(mensagem),
							"canal", canal, "externo", externo));
				}
				for (JsonNode confirmacao : valor.path("statuses")) {
					String estado = confirmacao.path("status").asText();
					banco.update("""
						UPDATE chat_mensagem SET confirmacao=CASE :estado
						WHEN 'sent' THEN 1 WHEN 'delivered' THEN 2 WHEN 'read' THEN 3 ELSE confirmacao END,
						lida_em=CASE WHEN :estado='read' THEN NOW() ELSE lida_em END
						WHERE canal_id=:canal AND identificador_externo=:externo
						""", dados("estado", estado, "canal", canal,
							"externo", confirmacao.path("id").asText()));
				}
			}
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido", erro); }
	}
}
