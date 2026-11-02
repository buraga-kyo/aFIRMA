package com.afirma.assina;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;

@Configuration
public class DiarioApiAutentica implements WebMvcConfigurer {
 private final AutenticaPersistencia persistencia;
 public DiarioApiAutentica(AutenticaPersistencia persistencia){this.persistencia=persistencia;}
 @Override public void addInterceptors(InterceptorRegistry registro){registro.addInterceptor(new HandlerInterceptor(){
  @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){req.setAttribute("firma.api.inicio",System.nanoTime());res.setHeader("Cache-Control","no-store");return true;}
  @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception erro){var v=new HashMap<String,Object>();v.put("APILogDataHoraRecebimento",java.time.OffsetDateTime.now());v.put("APILogMetodoHTTP",req.getMethod());v.put("APILogStatusCode",res.getStatus());v.put("APILogURL",req.getRequestURI());v.put("APILogIP",req.getRemoteAddr());Object inicio=req.getAttribute("firma.api.inicio");v.put("APILogTempoDecorridoMS",inicio instanceof Long n?(System.nanoTime()-n)/1000000:0);v.put("APILogRespostaEnviada","HTTP "+res.getStatus());try{persistencia.gravar("APILog",null,v);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(DiarioApiAutentica.class).warn("Não foi possível gravar o diário da API: {}",e.getClass().getSimpleName());}}
 }).addPathPatterns("/api/autentica/v1/**","/API/v1/**","/n8n/**");}
}
