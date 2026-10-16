package com.afirma.assina;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConsultaSepolia {
	private final URI endereco;
	private final ObjectMapper conversor;
	private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public ConsultaSepolia(
			@Value("${APP_SEPOLIA_RPC_URL:https://ethereum-sepolia-rpc.publicnode.com}") String endereco,
			ObjectMapper conversor) {
		this.endereco = URI.create(endereco);
		if (!"https".equalsIgnoreCase(this.endereco.getScheme())) {
			throw new IllegalArgumentException("RPC da Sepolia deve usar HTTPS");
		}
		this.conversor = conversor;
	}

	public Registro conferir(String hashTransacao, String carteira, String dadosEsperados) {
		if (!"0xaa36a7".equalsIgnoreCase(campo(consultar("eth_chainId"), "result"))) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RPC não está na Sepolia");
		}
		JsonNode transacao = consultar("eth_getTransactionByHash", hashTransacao).path("result");
		JsonNode recibo = consultar("eth_getTransactionReceipt", hashTransacao).path("result");
		if (transacao.isMissingNode() || transacao.isNull() || recibo.isMissingNode() || recibo.isNull()) {
			return null;
		}
		if (!carteira.equalsIgnoreCase(campo(transacao, "from"))
				|| !ProtocoloBlockchain.DESTINO_REGISTRO.equalsIgnoreCase(campo(transacao, "to"))
				|| !dadosEsperados.equalsIgnoreCase(campo(transacao, "input"))
				|| !"0x0".equalsIgnoreCase(campo(transacao, "value"))
				|| !hashTransacao.equalsIgnoreCase(campo(recibo, "transactionHash"))
				|| !carteira.equalsIgnoreCase(campo(recibo, "from"))
				|| !ProtocoloBlockchain.DESTINO_REGISTRO.equalsIgnoreCase(campo(recibo, "to"))) {
			throw new IllegalArgumentException("Transação não corresponde à assinatura");
		}
		if (!"0x1".equalsIgnoreCase(campo(recibo, "status"))) {
			throw new IllegalArgumentException("Transação falhou na Sepolia");
		}
		String numero = campo(recibo, "blockNumber");
		String hashBloco = campo(recibo, "blockHash");
		if (numero == null || hashBloco == null) return null;
		JsonNode finalizado = consultar("eth_getBlockByNumber", "finalized", false).path("result");
		if (finalizado.isNull() || finalizado.isMissingNode()) return null;
		if (valorHex(campo(finalizado, "number")) < valorHex(numero)) return null;
		JsonNode bloco = consultar("eth_getBlockByNumber", numero, false).path("result");
		if (bloco.isNull() || bloco.isMissingNode()
				|| !hashBloco.equalsIgnoreCase(campo(bloco, "hash"))
				|| !numero.equalsIgnoreCase(campo(transacao, "blockNumber"))) {
			throw new IllegalArgumentException("Bloco da transação não é canônico");
		}
		return new Registro(hashBloco, valorHex(campo(bloco, "timestamp")));
	}

	private JsonNode consultar(String metodo, Object... parametros) {
		try {
			String corpo = conversor.writeValueAsString(Map.of("jsonrpc", "2.0", "method", metodo,
					"params", parametros, "id", 1));
			HttpRequest pedido = HttpRequest.newBuilder(endereco).timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
			HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
			if (resposta.statusCode() != 200 || resposta.body().length() > 4_000_000) {
				throw new IllegalStateException("Resposta RPC inválida");
			}
			JsonNode dados = conversor.readTree(resposta.body());
			if (dados.hasNonNull("error")) throw new IllegalStateException("Erro RPC");
			return dados;
		} catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta à Sepolia interrompida", erro);
		} catch (Exception erro) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta à Sepolia indisponível", erro);
		}
	}

	private String campo(JsonNode dados, String nome) {
		JsonNode valor = dados.path(nome);
		return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
	}

	private long valorHex(String valor) {
		if (valor == null || !valor.matches("0x[0-9a-fA-F]+")) {
			throw new IllegalArgumentException("Resposta RPC inválida");
		}
		return Long.parseUnsignedLong(valor.substring(2), 16);
	}

	public record Registro(String bloco, long dataHora) { }
}
