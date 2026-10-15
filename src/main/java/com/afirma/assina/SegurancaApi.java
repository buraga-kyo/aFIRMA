package com.afirma.assina;

import java.util.Collection;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaApi {

	@Bean
	SecurityFilterChain configurar(HttpSecurity http,
			@Value("${APP_API_KEY_SHA256}") String resumoChaveApi) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(regras -> regras
						.requestMatchers(HttpMethod.POST, "/api/pdfs/assinar").hasAuthority("SCOPE_pdf.sign")
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.addFilterAfter(new FiltroChaveApi(resumoChaveApi), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	@Bean
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
}
