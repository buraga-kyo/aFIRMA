package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Busca os pedidos de resposta automática e passa o texto para a fila de DisparosFirma. */
@Service
public class AutomacaoFirma {
  private final BancoAssineai banco;
  private final TransactionTemplate transacoes;
  private final FirmaOperacoes operacoes;
  private final DisparosFirma disparos;
  private final boolean ativo;
  private final ServicoFirma dados;

  public AutomacaoFirma(
      BancoAssineai banco,
      TransactionTemplate transacoes,
      FirmaOperacoes operacoes,
      DisparosFirma disparos,
      ServicoFirma dados,
      @Value("${APP_FIRMA_ROTINAS:true}") boolean ativo) {
    this.banco = banco;
    this.transacoes = transacoes;
    this.operacoes = operacoes;
    this.disparos = disparos;
    this.dados = dados;
    this.ativo = ativo;
  }

  @Scheduled(fixedDelay = 3000)
  public void executar() {
    if (!ativo) return;
    for (int indice = 0; indice < 10; indice++) {
      var fila =
          transacoes.execute(transacao -> {
            var execucao =
                banco.um(
                    "SELECT e.*,m.\"AtendimentoId\",m.\"CanalId\",a.\"ClienteId\",co.\"ContatoNumero\" FROM firma_relacionamento_ia_execucao e JOIN firma.\"Mensagem\" m ON m.id=e.mensagem_id JOIN firma.\"Atendimento\" a ON a.id=m.\"AtendimentoId\" JOIN firma.\"Contato\" co ON co.id=a.\"ContatoId\" WHERE e.estado='PENDENTE' ORDER BY e.id LIMIT 1 FOR UPDATE OF e SKIP LOCKED",
                    Map.of());
            if (execucao != null)
              banco.executar(
                  "UPDATE firma_relacionamento_ia_execucao SET estado='EXECUTANDO',atualizada_em=now() WHERE id=:id",
                  Map.of("id", execucao.get("id")));
            return execucao;
          });
      if (fila == null) break;
      processar(fila);
    }
  }

  private void processar(Map<String, Object> campo) {
    try {
      long cliente = ((Number) campo.get("ClienteId")).longValue();
      var vinculo =
          banco.um(
              "SELECT * FROM firma_relacionamento_empresa WHERE cliente_id=:c LIMIT 1",
              Map.of("c", cliente));
      if (vinculo == null) throw new IllegalStateException();
      var contexto =
          new ServicoFirma.Contexto(
              ((Number) vinculo.get("empresa_id")).longValue(),
              cliente,
              ((Number) vinculo.get("repositorio_id")).longValue(),
              0);
      long atendimento = ((Number) campo.get("AtendimentoId")).longValue(),
          agente = ((Number) campo.get("agente_id")).longValue();
      var atendimentoAtual = dados.registro("Atendimento", atendimento, contexto);
      var configuracao = dados.registro("InteligenciaArtificial", agente, contexto);
      if ("finalizado".equals(atendimentoAtual.get("AtendimentoStatus"))
          || Boolean.FALSE.equals(configuracao.get("InteligenciaArtificialStatus"))) {
        terminar(campo, "CANCELADA", null);
        return;
      }
      var ultima =
          banco.um(
              "SELECT id FROM firma.\"Mensagem\" WHERE \"AtendimentoId\"=:a ORDER BY id DESC LIMIT 1",
              Map.of("a", atendimento));
      if (ultima == null || !Objects.equals(ultima.get("id"), campo.get("mensagem_id"))) {
        terminar(campo, "CANCELADA", "Uma nova mensagem substituiu esta solicitação");
        return;
      }
      String texto = operacoes.sugerir(atendimento, agente, contexto);
      var envio =
          disparos.avulso(
              Map.of(
                  "CanalId",
                  campo.get("CanalId"),
                  "AtendimentoId",
                  atendimento,
                  "Destinatario",
                  campo.get("ContatoNumero"),
                  "texto",
                  texto,
                  "origem",
                  "IA"),
              contexto,
              "ia:" + campo.get("mensagem_id") + ":" + agente);
      terminar(
          campo,
          "ENVIADO".equals(envio.get("estado")) ? "CONCLUIDA" : "FALHOU",
          Objects.toString(envio.get("erro"), null));
    } catch (Exception erroAtual) {
      terminar(
          campo,
          "FALHOU",
          "Não foi possível gerar ou entregar a resposta; confira a configuração da IA e do canal");
    }
  }

  private void terminar(Map<String, Object> campo, String estado, String erro) {
    var parametros = new HashMap<String, Object>();
    parametros.put("id", campo.get("id"));
    parametros.put("e", estado);
    parametros.put("erro", erro);
    banco.executar(
        "UPDATE firma_relacionamento_ia_execucao SET estado=:e,erro=:erro,atualizada_em=now() WHERE id=:id",
        parametros);
  }

  public Object execucoes(long agente, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "ia", "read");
    dados.registro("InteligenciaArtificial", agente, contexto);
    return banco.listar(
        "SELECT id,mensagem_id,estado,erro,atualizada_em FROM firma_relacionamento_ia_execucao WHERE agente_id=:a ORDER BY id DESC LIMIT 100",
        Map.of("a", agente));
  }
}
