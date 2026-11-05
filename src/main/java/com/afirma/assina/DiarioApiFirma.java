package com.afirma.assina;

import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;

@Configuration
/** Depois da resposta, grava o caminho, a duração e o resultado da chamada, sem copiar o corpo. */
public class DiarioApiFirma implements WebMvcConfigurer {
  private final FirmaPersistencia persistencia;

  public DiarioApiFirma(FirmaPersistencia persistencia) {
    this.persistencia = persistencia;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registro) {
    registro
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest requisicao,
                  HttpServletResponse resposta,
                  Object metodoChamado) {
                requisicao.setAttribute("firma.api.inicio", System.nanoTime());
                resposta.setHeader("Cache-Control", "no-store");
                return true;
              }

              @Override
              public void afterCompletion(
                  HttpServletRequest requisicao,
                  HttpServletResponse resposta,
                  Object metodoChamado,
                  Exception erro) {
                var dados = new HashMap<String, Object>();
                dados.put("APILogDataHoraRecebimento", java.time.OffsetDateTime.now());
                dados.put("APILogMetodoHTTP", requisicao.getMethod());
                dados.put("APILogStatusCode", resposta.getStatus());
                dados.put("APILogURL", requisicao.getRequestURI());
                dados.put("APILogIP", requisicao.getRemoteAddr());
                Object inicio = requisicao.getAttribute("firma.api.inicio");
                dados.put(
                    "APILogTempoDecorridoMS",
                    inicio instanceof Long valor ? (System.nanoTime() - valor) / 1000000 : 0);
                dados.put("APILogRespostaEnviada", "HTTP " + resposta.getStatus());
                try {
                  persistencia.gravar("APILog", null, dados);
                } catch (RuntimeException erroAtual) {
                  org.slf4j.LoggerFactory.getLogger(DiarioApiFirma.class)
                      .warn(
                          "Não foi possível gravar o diário da API: {}",
                          erroAtual.getClass().getSimpleName());
                }
              }
            })
        .addPathPatterns("/api/firma/v1/**", "/API/v1/**", "/n8n/**");
  }
}
