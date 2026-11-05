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
  SecurityFilterChain rotasCompativeis(HttpSecurity segurancaHttp, IdentidadeAssineai identidade,ChavesIntegracaoFirma chaves) throws Exception {
    return segurancaHttp
      .securityMatcher(
        "/api/assineai/**",
        "/api/firma/**",
        "/API/v1/**",
        "/n8n/**",
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
      .addFilterBefore(new FiltroAcessoFirma(identidade,chaves), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
      .csrf(c -> c.disable())
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a -> a.anyRequest().permitAll())
      .build();
    // Rotas públicas usam tokens de capacidade. As operações administrativas validam JWT e proprietário no controlador/serviço.
  }
}
