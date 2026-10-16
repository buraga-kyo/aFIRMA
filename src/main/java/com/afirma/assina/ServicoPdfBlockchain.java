package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import org.openpdf.text.pdf.PdfArray;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfSignature;
import org.openpdf.text.pdf.PdfSignatureAppearance;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfString;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoPdfBlockchain {
	private static final int ESPACO_PROVA = 4096;
	private static final String CAMPO_PROVA = "assinaturaBlockchain";
	private final ObjectMapper conversor;

	public ServicoPdfBlockchain(ObjectMapper conversor) {
		this.conversor = conversor;
	}

	public PdfPreparado preparar(byte[] pdf) throws Exception {
		try (PdfReader leitor = new PdfReader(pdf); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			validarConteudoEstatico(leitor);
			PdfStamper assinador = PdfStamper.createSignature(leitor, saida, null, null, true);
			PdfSignatureAppearance aparencia = assinador.getSignatureAppearance();
			aparencia.setFieldNameForInvisibleSignatures(CAMPO_PROVA);
			aparencia.setCryptoDictionary(new PdfSignature(new PdfName("aFIRMA"),
					new PdfName("aFIRMA.Blockchain.v1")));
			aparencia.preClose(Map.of(PdfName.CONTENTS, ESPACO_PROVA * 2 + 2));
			byte[] resumo;
			try (var intervalo = aparencia.getRangeStream()) {
				resumo = MessageDigest.getInstance("SHA-256").digest(intervalo.readAllBytes());
			}
			PdfDictionary conteudo = new PdfDictionary();
			conteudo.put(PdfName.CONTENTS, new PdfString(new byte[ESPACO_PROVA]).setHexWriting(true));
			aparencia.close(conteudo);
			return new PdfPreparado(saida.toByteArray(), "0x" + HexFormat.of().formatHex(resumo));
		}
	}

	public byte[] concluir(byte[] pdfPreparado, ProvaBlockchain prova) {
		byte[] dados = conversor.writeValueAsBytes(prova);
		if (dados.length > ESPACO_PROVA) {
			throw new IllegalArgumentException("Comprovante excede espaço reservado no PDF");
		}
		Intervalos intervalos = intervalos(pdfPreparado);
		byte[] saida = pdfPreparado.clone();
		byte[] preenchimento = new byte[ESPACO_PROVA];
		System.arraycopy(dados, 0, preenchimento, 0, dados.length);
		byte[] hexadecimal = HexFormat.of().formatHex(preenchimento).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(hexadecimal, 0, saida, intervalos.inicio() + 1, hexadecimal.length);
		return saida;
	}

	public ProvaLida ler(byte[] pdf) {
		Intervalos intervalos = intervalos(pdf);
		String hexadecimal = new String(pdf, intervalos.inicio() + 1,
				intervalos.fim() - intervalos.inicio() - 2, java.nio.charset.StandardCharsets.US_ASCII);
		byte[] conteudo = HexFormat.of().parseHex(hexadecimal);
		int tamanho = conteudo.length;
		while (tamanho > 0 && conteudo[tamanho - 1] == 0) tamanho--;
		if (tamanho == 0) throw new IllegalArgumentException("PDF sem comprovante blockchain");
		ProvaBlockchain prova = conversor.readValue(Arrays.copyOf(conteudo, tamanho), ProvaBlockchain.class);
		return new ProvaLida(prova, resumo(pdf, intervalos));
	}

	private Intervalos intervalos(byte[] pdf) {
		try (PdfReader leitor = new PdfReader(pdf)) {
			PdfDictionary assinatura = leitor.getAcroFields().getSignatureDictionary(CAMPO_PROVA);
			if (assinatura == null || !new PdfName("aFIRMA.Blockchain.v1").equals(assinatura.get(PdfName.SUBFILTER))) {
				throw new IllegalArgumentException("PDF sem assinatura blockchain aFIRMA");
			}
			PdfArray faixas = assinatura.getAsArray(PdfName.BYTERANGE);
			if (faixas == null || faixas.size() != 4 || Long.parseLong(faixas.getAsNumber(0).toString()) != 0) {
				throw new IllegalArgumentException("Intervalos de assinatura inválidos");
			}
			long inicio = Long.parseLong(faixas.getAsNumber(1).toString());
			long fim = Long.parseLong(faixas.getAsNumber(2).toString());
			long restante = Long.parseLong(faixas.getAsNumber(3).toString());
			if (inicio < 0 || fim <= inicio + 2 || fim > pdf.length || restante != pdf.length - fim
					|| fim - inicio != ESPACO_PROVA * 2L + 2 || pdf[(int) inicio] != '<'
					|| pdf[(int) fim - 1] != '>') {
				throw new IllegalArgumentException("PDF alterado fora da assinatura");
			}
			return new Intervalos((int) inicio, (int) fim);
		} catch (IOException erro) {
			throw new IllegalArgumentException("PDF inválido", erro);
		}
	}

	private String resumo(byte[] pdf, Intervalos intervalos) {
		try {
			MessageDigest digestor = MessageDigest.getInstance("SHA-256");
			digestor.update(pdf, 0, intervalos.inicio());
			digestor.update(pdf, intervalos.fim(), pdf.length - intervalos.fim());
			return "0x" + HexFormat.of().formatHex(digestor.digest());
		} catch (java.security.NoSuchAlgorithmException erro) {
			throw new IllegalStateException("SHA-256 indisponível", erro);
		}
	}

	private void validarConteudoEstatico(PdfReader leitor) {
		PdfDictionary catalogo = leitor.getCatalog();
		if (leitor.isEncrypted() || catalogo.get(PdfName.OPENACTION) != null
				|| catalogo.get(PdfName.AA) != null || catalogo.get(PdfName.ACROFORM) != null
				|| catalogo.get(PdfName.NAMES) != null) {
			throw new IllegalArgumentException("PDF com conteúdo ativo ou formulário não é aceito neste modo");
		}
	}

	private record Intervalos(int inicio, int fim) { }
	public record PdfPreparado(byte[] bytes, String resumo) { }
	public record ProvaLida(ProvaBlockchain prova, String resumoCalculado) { }
}
