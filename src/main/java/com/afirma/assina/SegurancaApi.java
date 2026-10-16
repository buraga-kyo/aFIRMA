package com.afirma.assina;

import java.util.Collection;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;

@Configuration
public class SegurancaApi {
	private static final String CHAVE_TESTE = "afirma-teste-local-2026-chave-api-exclusiva";
	private static final String SEGREDO_TESTE = "afirma-teste-local-2026-segredo-jwt-hs256-exclusivo";
	private static final String EMISSOR_TESTE = "http://127.0.0.1:8080/local-test";
	private static final String PUBLICO_TESTE = "afirma.local-test";

	@Bean
	SecurityFilterChain configurar(HttpSecurity http,
			@Value("${APP_API_KEY_SHA256:}") String resumoChaveApi, Environment ambiente) throws Exception {
		if (ambiente.matchesProfiles("local-test")) {
			try {
				byte[] resumo = java.security.MessageDigest.getInstance("SHA-256")
						.digest(CHAVE_TESTE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				resumoChaveApi = java.util.HexFormat.of().formatHex(resumo);
			} catch (java.security.NoSuchAlgorithmException erro) {
				throw new IllegalStateException("SHA-256 indisponível", erro);
			}
		}
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(regras -> regras
					.requestMatchers(HttpMethod.GET, "/", "/assinatura", "/css/**", "/js/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/blockchain/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/pdfs/assinar").hasAuthority("SCOPE_pdf.sign")
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.addFilterAfter(new FiltroChaveApi(resumoChaveApi), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	@Profile("!local-test")
	JwtDecoder decodificadorJwt(@Value("${APP_JWT_ISSUER_URI}") String emissor,
			@Value("${APP_JWT_AUDIENCE}") String audiencia) {
		if (!"https".equalsIgnoreCase(URI.create(emissor).getScheme()) || audiencia.isBlank()) {
			throw new IllegalArgumentException("Emissor JWT deve usar HTTPS e público não pode ser vazio");
		}
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withIssuerLocation(emissor).build();
		JwtClaimValidator<Collection<String>> validarPublico =
				new JwtClaimValidator<>(JwtClaimNames.AUD,
						publicos -> publicos != null && publicos.contains(audiencia));
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
				JwtValidators.createDefaultWithIssuer(emissor), validarPublico));
		return decodificador;
	}

	@Bean
	@Profile("local-test")
	JwtDecoder decodificadorJwtLocal() {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder
				.withSecretKey(new SecretKeySpec(SEGREDO_TESTE.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
				.macAlgorithm(MacAlgorithm.HS256).build();
		JwtClaimValidator<Collection<String>> validarPublico =
				new JwtClaimValidator<>(JwtClaimNames.AUD,
						publicos -> publicos != null && publicos.contains(PUBLICO_TESTE));
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
				JwtValidators.createDefaultWithIssuer(EMISSOR_TESTE), validarPublico));
		return decodificador;
	}
}
