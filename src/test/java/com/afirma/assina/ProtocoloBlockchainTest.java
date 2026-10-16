package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.junit.jupiter.api.Test;

class ProtocoloBlockchainTest {
	private final ProtocoloBlockchain protocolo = new ProtocoloBlockchain();

	@Test
	void recuperaCarteiraERejeitaMensagemAlterada() {
		var curva = SECNamedCurves.getByName("secp256k1");
		BigInteger chavePrivada = new BigInteger("123456789012345678901234567890");
		ECPoint chavePublica = curva.getG().multiply(chavePrivada).normalize();
		byte[] enderecoCalculado = new Keccak.Digest256().digest(
				Arrays.copyOfRange(chavePublica.getEncoded(false), 1, 65));
		String carteira = "0x" + HexFormat.of().formatHex(
				Arrays.copyOfRange(enderecoCalculado, 12, 32));
		String resumoPdf = "0x" + "ab".repeat(32);
		UUID identificador = UUID.randomUUID();
		long prazo = 2_000_000_000L;
		byte[] resumo = protocolo.resumoAssinatura(resumoPdf, carteira, identificador, prazo);
		var dominio = new ECDomainParameters(curva.getCurve(), curva.getG(), curva.getN(), curva.getH());
		var assinador = new ECDSASigner();
		assinador.init(true, new ECPrivateKeyParameters(chavePrivada, dominio));
		BigInteger[] partes = assinador.generateSignature(resumo);
		BigInteger r = partes[0];
		BigInteger s = partes[1];
		ECPoint ponto = curva.getG().multiply(r.multiply(chavePrivada).add(new BigInteger(1, resumo))
				.multiply(s.modInverse(curva.getN())).mod(curva.getN())).normalize();
		int recuperacao = ponto.getYCoord().toBigInteger().testBit(0) ? 1 : 0;
		if (s.compareTo(curva.getN().shiftRight(1)) > 0) {
			s = curva.getN().subtract(s);
			recuperacao ^= 1;
		}
		byte[] assinaturaBytes = new byte[65];
		System.arraycopy(BigIntegers.asUnsignedByteArray(32, r), 0, assinaturaBytes, 0, 32);
		System.arraycopy(BigIntegers.asUnsignedByteArray(32, s), 0, assinaturaBytes, 32, 32);
		assinaturaBytes[64] = (byte) (27 + recuperacao);
		String assinatura = "0x" + HexFormat.of().formatHex(assinaturaBytes);
		assertEquals(carteira, protocolo.recuperarCarteira(resumoPdf, carteira, identificador,
				prazo, assinatura));
		assertNotEquals(carteira, protocolo.recuperarCarteira("0x" + "cd".repeat(32),
				carteira, identificador, prazo, assinatura));
		assertThrows(IllegalArgumentException.class,
				() -> protocolo.recuperarCarteira(resumoPdf, carteira, identificador,
						prazo + 1, assinatura.substring(0, 130) + "ff"));
		assertEquals(7 + 32 + 32, protocolo.bytesHex(
				protocolo.dadosTransacao(resumoPdf, assinatura), 71).length);
	}
}
