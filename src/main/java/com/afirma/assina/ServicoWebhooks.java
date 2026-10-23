package com.afirma.assina;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoWebhooks {
	private static final Logger registro = LoggerFactory.getLogger(ServicoWebhooks.class);
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final NamedParameterJdbcTemplate banco;
	private final TransactionTemplate transacoes;
	private final EmpresaAtual empresas;
	private final Environment ambiente;

	public ServicoWebhooks(NamedParameterJdbcTemplate banco, TransactionTemplate transacoes,
			EmpresaAtual empresas, Environment ambiente) {
		this.banco = banco;
		this.transacoes = transacoes;
		this.empresas = empresas;
		this.ambiente = ambiente;
	}

	private static MapSqlParameterSource dados(Object... pares) {
		MapSqlParameterSource valores = new MapSqlParameterSource();
		for (int indice = 0; indice < pares.length; indice += 2)
			valores.addValue((String) pares[indice], pares[indice + 1]);
		return valores;
	}

	public List<Map<String, Object>> listar(Authentication usuario) {
		return banco.queryForList("""
			SELECT webhook.id,webhook.nome,webhook.destino,webhook.eventos,
			webhook.todos_canais,webhook.ativo,webhook.criado_em,
			COALESCE(array_agg(vinculo.canal_id) FILTER (WHERE vinculo.canal_id IS NOT NULL),
			'{}'::uuid[]) AS canais
			FROM chat_webhook webhook LEFT JOIN chat_webhook_canal vinculo
			ON vinculo.webhook_id=webhook.id
			WHERE webhook.empresa_id=:empresa GROUP BY webhook.id ORDER BY webhook.criado_em DESC
			""", dados("empresa", empresas.identificar(usuario)));
	}

	@Transactional
	public Map<String, Object> criar(Authentication usuario, String nome, String destino,
			String referenciaSegredo, List<String> eventos,
			boolean todosCanais, List<UUID> canais) {
		long empresa = empresas.identificar(usuario);
		if (nome == null || nome.isBlank() || nome.length() > 120 || destino == null
				|| referenciaSegredo == null || !referenciaSegredo.matches("APP_CHAT_WEBHOOK_[A-Z0-9_]{1,80}"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook inválido");
		verificarDestino(destino);
		if (eventos == null || eventos.stream().anyMatch(evento -> !evento.matches("[a-z.]{1,80}")))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eventos inválidos");
		if (!todosCanais && (canais == null || canais.isEmpty()))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha os canais do webhook");
		Integer permitido = banco.queryForObject("""
			SELECT count(*) FROM chat_assinatura_plano assinatura JOIN chat_plano plano
			ON plano.id=assinatura.plano_id WHERE assinatura.empresa_id=:empresa
			AND assinatura.estado='ATIVA' AND plano.permite_webhook
			""", dados("empresa", empresa), Integer.class);
		if (permitido != null && permitido == 0) {
			Integer assinaturas = banco.queryForObject("""
				SELECT count(*) FROM chat_assinatura_plano WHERE empresa_id=:empresa
				""", dados("empresa", empresa), Integer.class);
			if (assinaturas != null && assinaturas > 0)
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Plano não permite webhooks");
		}
		UUID id = UUID.randomUUID();
		Map<String, Object> webhook = banco.queryForMap("""
			INSERT INTO chat_webhook(id,empresa_id,nome,destino,referencia_segredo,eventos,todos_canais)
			VALUES (:id,:empresa,:nome,:destino,:segredo,:eventos,:todos)
			RETURNING id,nome,destino,eventos,todos_canais,ativo,criado_em
			""", dados("id", id, "empresa", empresa, "nome", nome.trim(),
				"destino", destino, "segredo", referenciaSegredo,
				"eventos", eventos.toArray(String[]::new), "todos", todosCanais));
		if (!todosCanais) for (UUID canal : canais) {
			int incluido = banco.update("""
				INSERT INTO chat_webhook_canal(webhook_id,canal_id,empresa_id)
				SELECT :webhook,id,:empresa FROM chat_canal
				WHERE id=:canal AND empresa_id=:empresa AND ativo ON CONFLICT DO NOTHING
				""", dados("webhook", id, "canal", canal, "empresa", empresa));
			if (incluido != 1)
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal inválido ou repetido");
		}
		return webhook;
	}

	public void excluir(Authentication usuario, UUID id) {
		if (banco.update("DELETE FROM chat_webhook WHERE id=:id AND empresa_id=:empresa",
				dados("id", id, "empresa", empresas.identificar(usuario))) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public void ativar(Authentication usuario, UUID id, boolean ativo) {
		if (banco.update("""
			UPDATE chat_webhook SET ativo=:ativo WHERE id=:id AND empresa_id=:empresa
			""", dados("id", id, "empresa", empresas.identificar(usuario), "ativo", ativo)) != 1)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	public List<Map<String, Object>> entregas(Authentication usuario, UUID id) {
		return banco.queryForList("""
			SELECT entrega.id,entrega.evento,entrega.estado,entrega.tentativas,
			entrega.proxima_tentativa_em,entrega.codigo_resposta,entrega.erro,
			entrega.criada_em,entrega.enviada_em,entrega.respondida_em
			FROM chat_webhook_entrega entrega JOIN chat_webhook webhook ON webhook.id=entrega.webhook_id
			WHERE webhook.id=:id AND webhook.empresa_id=:empresa ORDER BY entrega.id DESC LIMIT 100
			""", dados("id", id, "empresa", empresas.identificar(usuario)));
	}

	@Scheduled(fixedDelay = 10000)
	public void entregarPendentes() {
		for (int indice = 0; indice < 20; indice++) {
			Map<String, Object> entrega = transacoes.execute(estado -> reservar());
			if (entrega == null) break;
			entregar(entrega);
		}
	}

	private Map<String, Object> reservar() {
		List<Map<String, Object>> entregas = banco.queryForList("""
			SELECT entrega.id,entrega.evento,entrega.dados,entrega.tentativas,
			webhook.destino,webhook.referencia_segredo
			FROM chat_webhook_entrega entrega JOIN chat_webhook webhook ON webhook.id=entrega.webhook_id
			WHERE webhook.ativo AND entrega.estado IN ('PENDENTE','ENVIANDO')
			AND entrega.proxima_tentativa_em <= NOW()
			ORDER BY entrega.id LIMIT 1 FOR UPDATE OF entrega SKIP LOCKED
			""", dados());
		if (entregas.isEmpty()) return null;
		Map<String, Object> entrega = entregas.getFirst();
		banco.update("""
			UPDATE chat_webhook_entrega SET estado='ENVIANDO',tentativas=tentativas+1,
			enviada_em=NOW(),
			proxima_tentativa_em=NOW()+INTERVAL '2 minutes' WHERE id=:id
			""", dados("id", entrega.get("id")));
		return entrega;
	}

	private void entregar(Map<String, Object> entrega) {
		long id = ((Number) entrega.get("id")).longValue();
		int tentativas = ((Number) entrega.get("tentativas")).intValue() + 1;
		try {
			String destino = (String) entrega.get("destino");
			verificarDestino(destino);
			String segredo = ambiente.getProperty((String) entrega.get("referencia_segredo"));
			if (segredo == null || segredo.length() < 32)
				throw new IllegalStateException("Segredo do webhook indisponível");
			String corpo = "{\"evento\":\"" + entrega.get("evento") + "\",\"dados\":"
					+ entrega.get("dados") + "}";
			String instante = Long.toString(Instant.now().getEpochSecond());
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String assinatura = HexFormat.of().formatHex(mac.doFinal((instante + "." + corpo)
					.getBytes(StandardCharsets.UTF_8)));
			HttpRequest pedido = HttpRequest.newBuilder(URI.create(destino))
					.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
					.header("X-Firma-Evento", (String) entrega.get("evento"))
					.header("X-Firma-Instante", instante)
					.header("X-Firma-Assinatura", "sha256=" + assinatura)
					.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<Void> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.discarding());
			if (resposta.statusCode() < 200 || resposta.statusCode() >= 300)
				throw new IllegalStateException("HTTP " + resposta.statusCode());
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_webhook_entrega SET estado='ENTREGUE',codigo_resposta=:codigo,
				respondida_em=NOW(),erro=NULL
				WHERE id=:id
				""", dados("id", id, "codigo", resposta.statusCode())));
		} catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			registro.warn("Entrega de webhook {} falhou: {}", id, erro.getMessage());
			String mensagem = erro.getMessage() == null ? "Falha de entrega" : erro.getMessage();
			transacoes.executeWithoutResult(estado -> banco.update("""
				UPDATE chat_webhook_entrega SET estado=:estado,erro=:erro,respondida_em=NOW(),
				proxima_tentativa_em=NOW()+(:espera * INTERVAL '1 minute') WHERE id=:id
				""", dados("id", id, "estado", tentativas >= 5 ? "FALHOU" : "PENDENTE",
					"erro", mensagem.substring(0, Math.min(500, mensagem.length())),
					"espera", Math.min(60, 1 << tentativas))));
		}
	}

	private static void verificarDestino(String destino) {
		try {
			URI endereco = URI.create(destino);
			if (!"https".equalsIgnoreCase(endereco.getScheme()) || endereco.getHost() == null
					|| endereco.getUserInfo() != null || destino.length() > 2000)
				throw new IllegalArgumentException();
			for (InetAddress resolvido : InetAddress.getAllByName(endereco.getHost()))
				if (resolvido.isAnyLocalAddress() || resolvido.isLoopbackAddress()
						|| resolvido.isLinkLocalAddress() || resolvido.isSiteLocalAddress()
						|| resolvido.isMulticastAddress()) throw new IllegalArgumentException();
		} catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destino HTTPS público obrigatório");
		}
	}
}
