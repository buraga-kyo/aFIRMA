package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SegredosAssinatura {
	private static final SecureRandom ALEATORIO = new SecureRandom();
	private final java.util.Map<String, byte[]> chaves;
	private final String chaveAtiva;

	public SegredosAssinatura(Environment ambiente) {
		java.util.Map<String, byte[]> configuradas = new java.util.LinkedHashMap<>();
		String conjunto = ambiente.getProperty("ASSINEAI_CHAVES_EVIDENCIAS", "").strip();
		if (conjunto.length() >= 2 && conjunto.startsWith("'") && conjunto.endsWith("'")) conjunto = conjunto.substring(1, conjunto.length() - 1);
		chaveAtiva = ambiente.getProperty("ASSINEAI_CHAVE_ATIVA", "principal");
		if (!chaveAtiva.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Identificador da chave de evidências inválido");
		if (!conjunto.isBlank()) {
			tools.jackson.databind.JsonNode dados;
			try { dados = new tools.jackson.databind.ObjectMapper().readTree(conjunto); }
			catch (RuntimeException erro) { throw new IllegalArgumentException("Não foi possível ler a configuração das chaves de evidências"); }
			if (!dados.isObject()) throw new IllegalArgumentException("As chaves de evidências precisam ser um objeto JSON");
			for (var entrada : dados.properties()) {
				if (!entrada.getKey().matches("[A-Za-z0-9_-]{1,64}") || !entrada.getValue().isString()
						|| entrada.getValue().asText().length() < 32) throw new IllegalArgumentException("Chave de evidências inválida ou curta");
				configuradas.put(entrada.getKey(), entrada.getValue().asText().getBytes(StandardCharsets.UTF_8));
			}
		} else {
			String configurado = ambiente.getProperty("ASSINEAI_SEGREDO_EVIDENCIAS", "");
			if (configurado.isBlank() && ambiente.matchesProfiles("local-test")) configurado = "afirma-segredo-exclusivo-para-testes-locais-2026";
			if (!configurado.isEmpty() && configurado.length() < 32) throw new IllegalArgumentException("ASSINEAI_SEGREDO_EVIDENCIAS precisa ter pelo menos 32 caracteres");
			if (!configurado.isEmpty()) configuradas.put("principal", configurado.getBytes(StandardCharsets.UTF_8));
		}
		chaves = java.util.Map.copyOf(configuradas);
		if (!chaves.isEmpty() && !chaves.containsKey(chaveAtiva)) throw new IllegalArgumentException("A chave ativa de evidências não foi configurada");
	}

	public String identificadorChave() { return chaveAtiva; }
	public boolean configurado() { return chaves.containsKey(chaveAtiva); }
	public void exigirConfiguracao() {
		if (!configurado()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Configure o segredo de autenticação e preservação das evidências");
	}
	public String selar(String finalidade, String conteudo) { return selarComChave(chaveAtiva, finalidade, conteudo); }
	public String selarComChave(String identificador, String finalidade, String conteudo) {
		byte[] segredo = chaves.get(identificador);
		if (segredo == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chave histórica de evidências indisponível");
		try {
			Mac autenticador = Mac.getInstance("HmacSHA256");
			autenticador.init(new SecretKeySpec(segredo, "HmacSHA256"));
			return HexFormat.of().formatHex(autenticador.doFinal((finalidade + "\n" + conteudo).getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException erro) { throw new IllegalStateException("Não foi possível proteger as evidências", erro); }
	}

	public static String resumo(byte[] dados) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados)); }
		catch (java.security.GeneralSecurityException erro) { throw new IllegalStateException(erro); }
	}
	public static String resumo(String texto) { return resumo(texto.getBytes(StandardCharsets.UTF_8)); }
	public static boolean iguais(String primeiro, String segundo) {
		return primeiro != null && segundo != null && MessageDigest.isEqual(
				primeiro.getBytes(StandardCharsets.UTF_8), segundo.getBytes(StandardCharsets.UTF_8));
	}
	public static String novoSegredo() {
		byte[] aleatorio = new byte[32];
		ALEATORIO.nextBytes(aleatorio);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
	}
	public static String novoCodigo() { return "%08d".formatted(ALEATORIO.nextInt(100_000_000)); }
}
