package com.afirma.assina;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** ChatControle confere a assinatura do motor. A resposta passa daqui para a fila de DisparosFirma. */
@Service
public class RespostaMotorChatFirma {
  private final BancoAssineai banco;
  private final DisparosFirma envios;

  public RespostaMotorChatFirma(BancoAssineai banco, DisparosFirma envios) {
    this.banco = banco;
    this.envios = envios;
  }

  public void receber(UUID canal, int caixa, JsonNode evento, String telefone, String texto) {
    long mensagem = evento.path("id").asLong(0);
    if (mensagem <= 0 || texto.length() > 4096)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Mensagem do motor de chat inválida");
    var contexto =
        banco.um(
            """
            SELECT v.empresa_id,v.cliente_id,v.repositorio_id,ca.id AS canal_id,a.id AS atendimento_id
            FROM firma."Canal" ca JOIN firma_relacionamento_empresa v ON v.cliente_id=ca."ClienteId"
            JOIN firma."Contato" co ON co."ClienteId"=ca."ClienteId" AND co."ContatoNumero"=:telefone
            JOIN firma."Atendimento" a ON a."CanalId"=ca.id AND a."ContatoId"=co.id
            WHERE ca."CanalGUID"=:canal AND COALESCE(ca."CanalStatusUso",true)
            AND a."AtendimentoStatus" IN ('pendente','emandamento')
            AND NOT EXISTS(SELECT 1 FROM firma_relacionamento_exclusao e WHERE e.tabela='Canal' AND e.registro_id=ca.id)
            ORDER BY a.id DESC LIMIT 1
            """,
            Map.of("canal", canal, "telefone", telefone));
    if (contexto == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Não há atendimento aberto para esta resposta");
    var contextoAtual =
        new ServicoFirma.Contexto(
            ((Number) contexto.get("empresa_id")).longValue(),
            ((Number) contexto.get("cliente_id")).longValue(),
            ((Number) contexto.get("repositorio_id")).longValue(),
            0);
    var envio =
        envios.avulso(
            Map.of(
                "CanalId",
                contexto.get("canal_id"),
                "AtendimentoId",
                contexto.get("atendimento_id"),
                "Destinatario",
                telefone,
                "texto",
                texto,
                "origem",
                "MOTOR_CHAT"),
            contextoAtual,
            "motor-chat:" + caixa + ":" + mensagem);
    if (java.util.Set.of("FALHOU", "INCERTO").contains(envio.get("estado")))
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Confira o envio desta resposta no atendimento");
  }
}
