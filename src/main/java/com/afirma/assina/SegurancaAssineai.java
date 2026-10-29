package com.afirma.assina;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaAssineai {

  @Bean
  @Order(0)
  SecurityFilterChain rotasCompativeis(HttpSecurity http) throws Exception {
    return http
      .securityMatcher(
        "/api/assineai/**",
        "/CriarAssinaturaViaAPI",
        "/AssineAi_AssinarDocumentoViaAPI",
        "/ValidarToken",
        "/EnviarToken",
        "/enviarToken",
        "/ListarTodasAssinaturas",
        "/ListarDetalheAssinatura",
        "/ListarDetalhesDoSignatario",
        "/PegarTemaTelaAssinatura",
        "/AtualizarTemaTelaAssinatura",
        "/JLv3HEzbcO2uKlg0rsELA66dou",
        "/q9zePu74UYPUaDqUyay4fIjwNV"
      )
      .csrf(c -> c.disable())
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a -> a.anyRequest().permitAll())
      .build();
    // Rotas públicas usam tokens de capacidade. As operações administrativas validam JWT e proprietário no controlador/serviço.
  }
}
