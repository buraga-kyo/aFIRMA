package com.afirma.assina;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.springframework.stereotype.Component;

@Component
public class ProtocoloBlockchain {
	public static final long REDE_SEPOLIA = 11155111L;
	public static final String DESTINO_REGISTRO = "0x000000000000000000000000000000000000dEaD";
	private static final byte[] PREFIXO = "AFIRMA1".getBytes(StandardCharsets.US_ASCII);
	private static final X9ECParameters CURVA = SECNamedCurves.getByName("secp256k1");
	private static final byte[] TIPO_DOMINIO = hashTexto("EIP712Domain(string name,string version,uint256 chainId)");
	private static final byte[] TIPO_ASSINATURA = hashTexto(
			"AssinaturaPdf(bytes32 resumo,address carteira,bytes32 identificador,uint256 prazo)");

	public String identificadorAssinado(UUID identificador) {
		return "0x" + "0".repeat(32) + identificador.toString().replace("-", "");
	}

	public String dadosTransacao(String resumoPdf, String assinatura) {
		return "0x" + HexFormat.of().formatHex(juntar(PREFIXO, bytesHex(resumoPdf, 32),
				resumoSha256(bytesHex(assinatura, 65))));
	}

	public String recuperarCarteira(String resumoPdf, String carteira, UUID identificador,
			long prazo, String assinatura) {
		return recuperarCarteira(resumoPdf, carteira, identificador, prazo, assinatura, "aFIRMA");
	}

	public String recuperarCarteira(String resumoPdf, String carteira, UUID identificador,
			long prazo, String assinatura, String nomeEmpresa) {
		byte[] digest = resumoAssinatura(resumoPdf, carteira, identificador, prazo, nomeEmpresa);
		byte[] dadosAssinatura = bytesHex(assinatura, 65);
		BigInteger r = new BigInteger(1, Arrays.copyOfRange(dadosAssinatura, 0, 32));
		BigInteger s = new BigInteger(1, Arrays.copyOfRange(dadosAssinatura, 32, 64));
		int recuperacao = Byte.toUnsignedInt(dadosAssinatura[64]);
		if (recuperacao >= 27) recuperacao -= 27;
		BigInteger ordem = CURVA.getN();
		if (recuperacao > 1 || r.signum() <= 0 || r.compareTo(ordem) >= 0
				|| s.signum() <= 0 || s.compareTo(ordem.shiftRight(1)) > 0) {
			throw new IllegalArgumentException("Assinatura da carteira inválida");
		}
		BigInteger x = r;
		byte[] comprimido = new byte[33];
		comprimido[0] = (byte) (2 + recuperacao);
		System.arraycopy(BigIntegers.asUnsignedByteArray(32, x), 0, comprimido, 1, 32);
		ECPoint ponto;
		try {
			ponto = CURVA.getCurve().decodePoint(comprimido);
		} catch (IllegalArgumentException erro) {
			throw new IllegalArgumentException("Assinatura da carteira inválida", erro);
		}
		if (!ponto.multiply(ordem).isInfinity()) {
			throw new IllegalArgumentException("Assinatura da carteira inválida");
		}
		BigInteger inverso = r.modInverse(ordem);
		ECPoint publica = ECAlgorithms.sumOfTwoMultiplies(CURVA.getG(),
				new BigInteger(1, digest).negate().multiply(inverso).mod(ordem), ponto,
				s.multiply(inverso).mod(ordem)).normalize();
		if (publica.isInfinity()) throw new IllegalArgumentException("Assinatura da carteira inválida");
		byte[] chave = publica.getEncoded(false);
		byte[] hashChave = keccak(Arrays.copyOfRange(chave, 1, chave.length));
		return "0x" + HexFormat.of().formatHex(Arrays.copyOfRange(hashChave, 12, 32));
	}

	byte[] resumoAssinatura(String resumoPdf, String carteira, UUID identificador, long prazo) {
		return resumoAssinatura(resumoPdf, carteira, identificador, prazo, "aFIRMA");
	}

	byte[] resumoAssinatura(String resumoPdf, String carteira, UUID identificador, long prazo,
			String nomeEmpresa) {
		if (prazo < 0) throw new IllegalArgumentException("Prazo inválido");
		if (nomeEmpresa == null || nomeEmpresa.isBlank() || nomeEmpresa.length() > 120) {
			throw new IllegalArgumentException("Nome da empresa inválido");
		}
		byte[] resumo = bytesHex(resumoPdf, 32);
		byte[] endereco = bytesHex(carteira, 20);
		byte[] identificadorBytes = bytesHex(identificadorAssinado(identificador), 32);
		byte[] dominio = keccak(juntar(TIPO_DOMINIO, hashTexto(nomeEmpresa), hashTexto("1"),
				uint256(REDE_SEPOLIA)));
		byte[] mensagem = keccak(juntar(TIPO_ASSINATURA, resumo, juntar(new byte[12], endereco),
				identificadorBytes, uint256(prazo)));
		return keccak(juntar(new byte[] {0x19, 0x01}, dominio, mensagem));
	}

	public byte[] bytesHex(String texto, int tamanho) {
		if (texto == null || !texto.matches("0x[0-9a-fA-F]{" + (tamanho * 2) + "}")) {
			throw new IllegalArgumentException("Dado hexadecimal inválido");
		}
		return HexFormat.of().parseHex(texto.substring(2));
	}

	private static byte[] uint256(long valor) {
		byte[] dados = new byte[32];
		ByteBuffer.wrap(dados, 24, 8).putLong(valor);
		return dados;
	}

	private static byte[] hashTexto(String texto) {
		return keccak(texto.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] keccak(byte[] dados) {
		return new Keccak.Digest256().digest(dados);
	}

	private static byte[] resumoSha256(byte[] dados) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(dados);
		} catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException("SHA-256 indisponível", erro);
		}
	}

	private static byte[] juntar(byte[]... partes) {
		int tamanho = Arrays.stream(partes).mapToInt(parte -> parte.length).sum();
		byte[] resultado = new byte[tamanho];
		int posicao = 0;
		for (byte[] parte : partes) {
			System.arraycopy(parte, 0, resultado, posicao, parte.length);
			posicao += parte.length;
		}
		return resultado;
	}
}
