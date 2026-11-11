package com.afirma.assina;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoPedidosAssinatura {
	private final JdbcTemplate banco;
	private final SegredosAssinatura segredos;
	private final ObjectMapper json;

	public ServicoPedidosAssinatura(JdbcTemplate banco, SegredosAssinatura segredos, ObjectMapper json) {
		this.banco = banco; this.segredos = segredos; this.json = json;
	}

	public String resumir(Object dados, byte[] pdf, byte[] certificado, byte[] imagem) {
		return SegredosAssinatura.resumo(json.writeValueAsBytes(Map.of("dados", dados, "pdf", SegredosAssinatura.resumo(pdf),
				"certificado", certificado == null ? "" : SegredosAssinatura.resumo(certificado),
				"imagem", imagem == null ? "" : SegredosAssinatura.resumo(imagem))));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Anterior iniciar(UUID requisicao, String criador, String tipo, String resumo) {
		segredos.exigirConfiguracao();
		if (requisicao == null) throw new ErroAssinatura("Informe um identificador único para a requisição");
		long bloqueio = ByteBuffer.wrap(HexFormat.of().parseHex(SegredosAssinatura.resumo(criador + "\n" + requisicao))).getLong();
		// Bloqueia também a primeira tentativa, quando ainda não existe uma linha para bloquear.
		banco.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conexao -> {
			try (var comando = conexao.prepareStatement("SELECT pg_advisory_xact_lock(?)")) { comando.setLong(1, bloqueio); comando.execute(); }
			return null;
		});
		var existentes = banco.queryForList("SELECT * FROM pedido_assinatura WHERE criador=? AND requisicao_id=?", criador, requisicao);
		if (existentes.isEmpty()) return null;
		var anterior = existentes.getFirst();
		if (!tipo.equals(anterior.get("tipo")) || !resumo.equals(anterior.get("resumo_pedido"))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "O identificador já foi usado com outro conteúdo. Retome o envio original ou inicie uma nova operação");
		}
		if (!Instant.now().isBefore(((Timestamp) anterior.get("criado_em")).toInstant().plusSeconds(86400))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "O prazo para repetir este envio terminou. Consulte o documento antes de iniciar outra operação");
		}
		byte[] resposta = (byte[]) anterior.get("resposta_cifrada");
		return new Anterior((UUID) anterior.get("envelope_id"), resposta == null ? null : cifrar(false,
				(String) anterior.get("chave_resposta"), criador + "\n" + requisicao, resposta));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void concluir(UUID requisicao, String criador, String tipo, String resumo, UUID envelope, Object resposta) {
		byte[] conteudo = resposta == null ? null : json.writeValueAsBytes(resposta);
		byte[] cifrado = conteudo == null ? null : cifrar(true, segredos.identificadorChave(), criador + "\n" + requisicao, conteudo);
		banco.update("""
				INSERT INTO pedido_assinatura(criador,requisicao_id,tipo,resumo_pedido,envelope_id,chave_resposta,resposta_cifrada,criado_em)
				VALUES (?,?,?,?,?,?,?,?)
				""", criador, requisicao, tipo, resumo, envelope, segredos.identificadorChave(), cifrado, Timestamp.from(Instant.now()));
		if (conteudo != null) java.util.Arrays.fill(conteudo, (byte) 0);
	}

	public <T> T lerResposta(Anterior anterior, Class<T> tipo) {
		try { return json.readValue(anterior.resposta(), tipo); }
		finally { java.util.Arrays.fill(anterior.resposta(), (byte) 0); }
	}

	@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
	@Transactional
	public void limparRespostasExpiradas() {
		banco.update("UPDATE pedido_assinatura SET resposta_cifrada=NULL WHERE resposta_cifrada IS NOT NULL AND criado_em<CURRENT_TIMESTAMP-INTERVAL '24 hours'");
	}

	private byte[] cifrar(boolean gravar, String chave, String contexto, byte[] dados) {
		try {
			byte[] segredo = HexFormat.of().parseHex(segredos.selarComChave(chave, "cifra-resposta", "afirma-pedidos-1"));
			try {
				byte[] nonce = new byte[12];
				if (gravar) new SecureRandom().nextBytes(nonce);
				else {
					if (dados.length < 28) throw new IllegalArgumentException("Resposta cifrada inválida");
					System.arraycopy(dados, 0, nonce, 0, nonce.length);
				}
				Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
				cifrador.init(gravar ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(segredo, "AES"), new GCMParameterSpec(128, nonce));
				cifrador.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
				if (!gravar) return cifrador.doFinal(dados, 12, dados.length - 12);
				byte[] resultado = cifrador.doFinal(dados);
				return ByteBuffer.allocate(12 + resultado.length).put(nonce).put(resultado).array();
			} finally { java.util.Arrays.fill(segredo, (byte) 0); }
		} catch (Exception erro) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível recuperar a resposta protegida deste envio"); }
	}
	public record Anterior(UUID envelope, byte[] resposta) { }
}
