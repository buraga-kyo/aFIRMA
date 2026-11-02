package com.afirma.assina;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhooksAutentica {
 private final BancoAssineai banco;private final TransactionTemplate tx;private final ObjectMapper json;private final ServicoAutentica dados;private final AutenticaPersistencia persistencia;private final boolean ativo;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public WebhooksAutentica(BancoAssineai banco,TransactionTemplate tx,ObjectMapper json,ServicoAutentica dados,AutenticaPersistencia persistencia,@Value("${APP_AUTENTICA_ROTINAS:true}")boolean ativo){this.banco=banco;this.tx=tx;this.json=json;this.dados=dados;this.persistencia=persistencia;this.ativo=ativo;}
 @Scheduled(fixedDelay=3000) public void executar(){if(!ativo)return;for(int i=0;i<20;i++){var e=tx.execute(s->{var r=banco.um("SELECT e.*,w.\"WebhookURL\" FROM firma_autentica_webhook_entrega e JOIN autentica.\"Webhook\" w ON w.id=e.webhook_id WHERE e.estado IN ('PENDENTE','ENVIANDO') AND e.proxima_tentativa<=now() ORDER BY e.id LIMIT 1 FOR UPDATE OF e SKIP LOCKED",Map.of());if(r!=null)banco.executar("UPDATE firma_autentica_webhook_entrega SET estado='ENVIANDO',tentativas=tentativas+1,proxima_tentativa=now()+interval '2 minutes' WHERE id=:id",Map.of("id",r.get("id")));return r;});if(e==null)break;entregar(e);}}
 private void entregar(Map<String,Object> e){int tentativa=((Number)e.get("tentativas")).intValue()+1;Integer codigo=null;try{String destino=e.get("WebhookURL").toString();ServicoWebhooks.verificarDestino(destino);var pedido=HttpRequest.newBuilder(URI.create(destino)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").header("X-Firma-Entrega",e.get("id").toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(e.get("dados")))).build();var r=http.send(pedido,HttpResponse.BodyHandlers.discarding());codigo=r.statusCode();if(codigo<200||codigo>=300)throw new IllegalStateException();final int c=codigo;tx.executeWithoutResult(s->{banco.executar("UPDATE firma_autentica_webhook_entrega SET estado='ENTREGUE',codigo=:c,erro=NULL,respondida_em=now() WHERE id=:id",Map.of("id",e.get("id"),"c",c));});}catch(Exception erro){if(erro instanceof InterruptedException)Thread.currentThread().interrupt();var p=new HashMap<String,Object>();p.put("id",e.get("id"));p.put("estado",tentativa>=5?"FALHOU":"PENDENTE");p.put("codigo",codigo);p.put("minutos",Math.min(60,1<<tentativa));banco.executar("UPDATE firma_autentica_webhook_entrega SET estado=:estado,codigo=:codigo,erro='Destino indisponível ou resposta recusada',respondida_em=now(),proxima_tentativa=now()+(:minutos*interval '1 minute') WHERE id=:id",p);}}
 public Object entregas(long webhook,ServicoAutentica.Contexto c){dados.autorizar(c,"webhooks","read");dados.registro("Webhook",webhook,c);return banco.listar("SELECT id,evento,estado,tentativas,codigo,erro,criada_em,respondida_em FROM firma_autentica_webhook_entrega WHERE webhook_id=:id AND cliente_id=:c ORDER BY id DESC LIMIT 100",Map.of("id",webhook,"c",c.cliente()));}
 @Scheduled(cron="0 0 1 * * *",zone="America/Sao_Paulo")public void limpar(){if(!ativo)return;banco.executar("DELETE FROM firma_autentica_token_revogado WHERE expira_em<now()",Map.of());banco.executar("DELETE FROM firma_autentica_webhook_entrega WHERE criada_em<now()-interval '30 days' AND estado IN ('ENTREGUE','FALHOU')",Map.of());banco.executar("DELETE FROM autentica.\"APILog\" WHERE \"createdAt\"<now()-interval '30 days'",Map.of());banco.executar("DELETE FROM autentica.\"WebhookLog\" WHERE \"createdAt\"<now()-interval '30 days'",Map.of());banco.executar("UPDATE autentica_identidade.\"Sessao\" SET \"SessaoStatus\"='F' WHERE \"SessaoStatus\"='A' AND \"SessaoUltimoAcesso\"<now()-interval '30 days'",Map.of());}
}
