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

/** Mesmo SDK oficial utilizado na origem, com credenciais próprias da instalação FIRMA. */
@Service
public class NotificacoesAutentica {
 private final BancoAssineai banco;private final ServicoAutentica dados;private final AutenticaPersistencia persistencia;private final Environment ambiente;private FirebaseApp app;
 public NotificacoesAutentica(BancoAssineai banco,ServicoAutentica dados,AutenticaPersistencia persistencia,Environment ambiente){this.banco=banco;this.dados=dados;this.persistencia=persistencia;this.ambiente=ambiente;}
 private synchronized FirebaseMessaging cliente(){if(!ambiente.getProperty("APP_FIREBASE_ATIVO",Boolean.class,false))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Notificações push não configuradas");try{if(app==null){var opcoes=FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault());String projeto=ambiente.getProperty("FIREBASE_PROJECT_ID");if(projeto!=null&&!projeto.isBlank())opcoes.setProjectId(projeto);app=FirebaseApp.initializeApp(opcoes.build(),"firma-"+UUID.randomUUID());}return FirebaseMessaging.getInstance(app);}catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Credenciais de notificações indisponíveis");}}
 @jakarta.annotation.PreDestroy void fechar(){if(app!=null)app.delete();}
 @Transactional public void registrar(String token,long atendente,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");var a=dados.registro("Atendente",atendente,c);RegrasAutentica.exigir(c.usuario()==0||Objects.equals(a.get("AtendenteAutenticaAIUsuarioId"),Math.toIntExact(c.usuario())),"Registre notificações apenas para seu usuário");RegrasAutentica.exigir(token!=null&&token.length()>=20&&token.length()<=4096,"Token de notificação inválido");banco.um("SELECT pg_advisory_xact_lock(hashtextextended(:t,0))",Map.of("t",token));var existente=banco.um("SELECT id,\"AtendenteId\" FROM autentica.\"FCMToken\" WHERE \"Token\"=:t",Map.of("t",token));if(existente!=null)dados.registro("Atendente",((Number)existente.get("AtendenteId")).longValue(),c);persistencia.gravar("FCMToken",existente==null?null:((Number)existente.get("id")).longValue(),Map.of("Token",token,"AtendenteId",atendente));}
 public Map<String,Object> config(){var r=new LinkedHashMap<String,Object>();for(var e:Map.of("apiKey","FIREBASE_API_KEY","authDomain","FIREBASE_AUTH_DOMAIN","projectId","FIREBASE_PROJECT_ID","storageBucket","FIREBASE_STORAGE_BUCKET","messagingSenderId","FIREBASE_MESSAGING_SENDER_ID","appId","FIREBASE_APP_ID").entrySet())r.put(e.getKey(),ambiente.getProperty(e.getValue(),""));r.put("ativo",ambiente.getProperty("APP_FIREBASE_ATIVO",Boolean.class,false));return r;}
 @Scheduled(cron="0 0 3 * * *",zone="America/Sao_Paulo")public void limpar(){if(!ambiente.getProperty("APP_AUTENTICA_ROTINAS",Boolean.class,true)||!ambiente.getProperty("APP_FIREBASE_ATIVO",Boolean.class,false))return;FirebaseMessaging cliente;try{cliente=cliente();}catch(ResponseStatusException e){return;}for(var r:banco.listar("SELECT id,\"Token\" FROM autentica.\"FCMToken\" ORDER BY \"updatedAt\" LIMIT 500",Map.of())){try{cliente.send(Message.builder().setToken(r.get("Token").toString()).build(),true);}catch(FirebaseMessagingException e){if(e.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED||e.getMessagingErrorCode()==MessagingErrorCode.INVALID_ARGUMENT)banco.executar("DELETE FROM autentica.\"FCMToken\" WHERE id=:id",Map.of("id",r.get("id")));}}}
}
