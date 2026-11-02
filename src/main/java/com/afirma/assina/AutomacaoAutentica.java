package com.afirma.assina;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AutomacaoAutentica {
 private final BancoAssineai banco;private final TransactionTemplate tx;private final AutenticaOperacoes operacoes;private final DisparosAutentica disparos;private final boolean ativo;private final ServicoAutentica dados;
 public AutomacaoAutentica(BancoAssineai banco,TransactionTemplate tx,AutenticaOperacoes operacoes,DisparosAutentica disparos,ServicoAutentica dados,@Value("${APP_AUTENTICA_ROTINAS:true}")boolean ativo){this.banco=banco;this.tx=tx;this.operacoes=operacoes;this.disparos=disparos;this.dados=dados;this.ativo=ativo;}
 @Scheduled(fixedDelay=3000)public void executar(){if(!ativo)return;for(int i=0;i<10;i++){var fila=tx.execute(s->{var f=banco.um("SELECT e.*,m.\"AtendimentoId\",m.\"CanalId\",a.\"ClienteId\",co.\"ContatoNumero\" FROM firma_autentica_ia_execucao e JOIN autentica.\"Mensagem\" m ON m.id=e.mensagem_id JOIN autentica.\"Atendimento\" a ON a.id=m.\"AtendimentoId\" JOIN autentica.\"Contato\" co ON co.id=a.\"ContatoId\" WHERE e.estado='PENDENTE' ORDER BY e.id LIMIT 1 FOR UPDATE OF e SKIP LOCKED",Map.of());if(f!=null)banco.executar("UPDATE firma_autentica_ia_execucao SET estado='EXECUTANDO',atualizada_em=now() WHERE id=:id",Map.of("id",f.get("id")));return f;});if(fila==null)break;processar(fila);}}
 private void processar(Map<String,Object> f){try{long cliente=((Number)f.get("ClienteId")).longValue();var vinculo=banco.um("SELECT * FROM firma_autentica_empresa WHERE cliente_id=:c LIMIT 1",Map.of("c",cliente));if(vinculo==null)throw new IllegalStateException();var c=new ServicoAutentica.Contexto(((Number)vinculo.get("empresa_id")).longValue(),cliente,((Number)vinculo.get("repositorio_id")).longValue(),0);long atendimento=((Number)f.get("AtendimentoId")).longValue(),agente=((Number)f.get("agente_id")).longValue();var a=dados.registro("Atendimento",atendimento,c);var config=dados.registro("InteligenciaArtificial",agente,c);if("finalizado".equals(a.get("AtendimentoStatus"))||Boolean.FALSE.equals(config.get("InteligenciaArtificialStatus"))){terminar(f,"CANCELADA",null);return;}
   var ultima=banco.um("SELECT id FROM autentica.\"Mensagem\" WHERE \"AtendimentoId\"=:a ORDER BY id DESC LIMIT 1",Map.of("a",atendimento));if(ultima==null||!Objects.equals(ultima.get("id"),f.get("mensagem_id"))){terminar(f,"CANCELADA","Uma nova mensagem substituiu esta solicitação");return;}
   String texto=operacoes.sugerir(atendimento,agente,c);var envio=disparos.avulso(Map.of("CanalId",f.get("CanalId"),"AtendimentoId",atendimento,"Destinatario",f.get("ContatoNumero"),"texto",texto,"origem","IA"),c,"ia:"+f.get("mensagem_id")+":"+agente);terminar(f,"ENVIADO".equals(envio.get("estado"))?"CONCLUIDA":"FALHOU",Objects.toString(envio.get("erro"),null));
  }catch(Exception e){terminar(f,"FALHOU","Não foi possível gerar ou entregar a resposta; confira a configuração da IA e do canal");}}
 private void terminar(Map<String,Object> f,String estado,String erro){var p=new HashMap<String,Object>();p.put("id",f.get("id"));p.put("e",estado);p.put("erro",erro);banco.executar("UPDATE firma_autentica_ia_execucao SET estado=:e,erro=:erro,atualizada_em=now() WHERE id=:id",p);}
 public Object execucoes(long agente,ServicoAutentica.Contexto c){dados.autorizar(c,"ia","read");dados.registro("InteligenciaArtificial",agente,c);return banco.listar("SELECT id,mensagem_id,estado,erro,atualizada_em FROM firma_autentica_ia_execucao WHERE agente_id=:a ORDER BY id DESC LIMIT 100",Map.of("a",agente));}
}
