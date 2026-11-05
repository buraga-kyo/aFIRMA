package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.*;

/** Liga os tokens de notificação dos atendentes ao Firebase da instalação. */
@Service
public class NotificacoesFirma {
  private final BancoAssineai banco;
  private final ServicoFirma dados;
  private final FirmaPersistencia persistencia;
  private final Environment ambiente;
  private FirebaseApp aplicativo;

  public NotificacoesFirma(
      BancoAssineai banco,
      ServicoFirma dados,
      FirmaPersistencia persistencia,
      Environment ambiente) {
    this.banco = banco;
    this.dados = dados;
    this.persistencia = persistencia;
    this.ambiente = ambiente;
  }

  private synchronized FirebaseMessaging cliente() {
    if (!ambiente.getProperty("APP_FIREBASE_ATIVO", Boolean.class, false))
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Notificações push não configuradas");
    try {
      if (aplicativo == null) {
        var opcoes =
            FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault());
        String projeto = ambiente.getProperty("FIREBASE_PROJECT_ID");
        if (projeto != null && !projeto.isBlank()) opcoes.setProjectId(projeto);
        aplicativo = FirebaseApp.initializeApp(opcoes.build(), "firma-" + UUID.randomUUID());
      }
      return FirebaseMessaging.getInstance(aplicativo);
    } catch (Exception erro) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Credenciais de notificações indisponíveis");
    }
  }

  @jakarta.annotation.PreDestroy
  void fechar() {
    if (aplicativo != null) aplicativo.delete();
  }

  @Transactional
  public void registrar(String token, long atendente, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    var atendenteAtual = dados.registro("Atendente", atendente, contexto);
    RegrasFirma.exigir(
        contexto.usuario() == 0
            || Objects.equals(
                atendenteAtual.get("AtendenteUsuarioId"), Math.toIntExact(contexto.usuario())),
        "Registre notificações apenas para seu usuário");
    RegrasFirma.exigir(
        token != null && token.length() >= 20 && token.length() <= 4096,
        "Token de notificação inválido");
    banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:t,0))", Map.of("t", token));
    var existente =
        banco.um(
            "SELECT id,\"AtendenteId\" FROM firma.\"FCMToken\" WHERE \"Token\"=:t",
            Map.of("t", token));
    if (existente != null)
      dados.registro("Atendente", ((Number) existente.get("AtendenteId")).longValue(), contexto);
    persistencia.gravar(
        "FCMToken",
        existente == null ? null : ((Number) existente.get("id")).longValue(),
        Map.of("Token", token, "AtendenteId", atendente));
  }

  public Map<String, Object> config() {
    var dadosAtuais = new LinkedHashMap<String, Object>();
    for (var item :
        Map.of(
                "apiKey",
                "FIREBASE_API_KEY",
                "authDomain",
                "FIREBASE_AUTH_DOMAIN",
                "projectId",
                "FIREBASE_PROJECT_ID",
                "storageBucket",
                "FIREBASE_STORAGE_BUCKET",
                "messagingSenderId",
                "FIREBASE_MESSAGING_SENDER_ID",
                "appId",
                "FIREBASE_APP_ID")
            .entrySet()) dadosAtuais.put(item.getKey(), ambiente.getProperty(item.getValue(), ""));
    dadosAtuais.put("ativo", ambiente.getProperty("APP_FIREBASE_ATIVO", Boolean.class, false));
    return dadosAtuais;
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
  public void limpar() {
    if (!ambiente.getProperty("APP_FIRMA_ROTINAS", Boolean.class, true)
        || !ambiente.getProperty("APP_FIREBASE_ATIVO", Boolean.class, false)) return;
    FirebaseMessaging cliente;
    try {
      cliente = cliente();
    } catch (ResponseStatusException erro) {
      return;
    }
    for (var registroAtual :
        banco.listar(
            "SELECT id,\"Token\" FROM firma.\"FCMToken\" ORDER BY \"updatedAt\" LIMIT 500",
            Map.of())) {
      try {
        cliente.send(
            Message.builder().setToken(registroAtual.get("Token").toString()).build(), true);
      } catch (FirebaseMessagingException erro) {
        if (erro.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
            || erro.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)
          banco.executar(
              "DELETE FROM firma.\"FCMToken\" WHERE id=:id", Map.of("id", registroAtual.get("id")));
      }
    }
  }
}
