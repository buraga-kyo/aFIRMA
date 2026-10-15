package com.afirma.assina;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaApi {

	@Bean
	SecurityFilterChain configurar(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/pdfs/assinar"))
				.authorizeHttpRequests(regras -> regras.anyRequest().authenticated())
				.httpBasic(basic -> {});
		return http.build();
	}
}
