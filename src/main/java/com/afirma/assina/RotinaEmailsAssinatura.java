package com.afirma.assina;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class RotinaEmailsAssinatura {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RotinaEmailsAssinatura.class);
  private static final int MAX_TENTATIVAS = 5;
  private static final TypeReference<Map<String, String>> DADOS = new TypeReference<>() {};
  private static final String ENFILEIRAR = """
      WITH configurados AS (
        SELECT t.empresa_id, t.etapa, min(p.publicado_em) AS desde
        FROM firma_email_template t
        JOIN firma_email_assinatura_publicacao p ON p.empresa_id=t.empresa_id AND p.etapa=t.etapa
        WHERE t.ativo AND t.excluido_em IS NULL AND t.etapa <> 'verificacao'
        GROUP BY t.empresa_id,t.etapa
      ), momentos AS (
        SELECT e.envelope_id, e.participante_id, e.sequencia::text AS ocorrencia, e.ocorrido_em,
          CASE e.tipo
            WHEN 'ACEITE_REGISTRADO' THEN 'assinatura-individual'
            WHEN 'ENVELOPE_FINALIZADO' THEN 'documento-concluido'
            WHEN 'CONVITE_RECUSADO' THEN 'signatario-recusou'
            WHEN 'ENVELOPE_CANCELADO' THEN 'assinatura-cancelada'
          END AS etapa
        FROM evento_assinatura e
        WHERE e.tipo IN ('ACEITE_REGISTRADO', 'ENVELOPE_FINALIZADO', 'CONVITE_RECUSADO', 'ENVELOPE_CANCELADO')
          AND e.ocorrido_em >= (SELECT min(desde) FROM configurados)
        UNION ALL
        SELECT e.id, NULL, extract(epoch FROM e.expira_em)::text, now(), 'prazo-proximo'
        FROM envelope_assinatura e
        WHERE e.finalizado_em IS NULL AND e.cancelado_em IS NULL AND e.excluido_em IS NULL
          AND e.expira_em > now() AND e.expira_em <= now() + interval '48 hours'
        UNION ALL
        SELECT e.id, NULL, extract(epoch FROM e.expira_em)::text, e.expira_em, 'prazo-vencido'
        FROM envelope_assinatura e
        WHERE e.finalizado_em IS NULL AND e.cancelado_em IS NULL AND e.excluido_em IS NULL
          AND e.expira_em <= now()
          AND e.expira_em >= (SELECT min(desde) FROM configurados WHERE etapa='prazo-vencido')
          AND EXISTS (SELECT 1 FROM signatario_envelope pendente WHERE pendente.envelope_id=e.id
            AND pendente.assinado_em IS NULL AND pendente.recusado_em IS NULL)
      ), candidatos AS (
        SELECT e.id AS envelope_id, c.empresa_id, m.etapa, destinatario.id AS signatario_id,
          lower(btrim(destinatario.email)) AS email,
          concat_ws(':', e.id, m.etapa, m.ocorrencia, lower(btrim(destinatario.email))) AS chave,
          jsonb_build_object(
            'id_assinatura', e.id::text, 'nome_empresa', e.nome_empresa,
            'nome_assinatura', COALESCE(NULLIF(btrim(e.nome), ''), e.nome_documento),
            'nome_documento', e.nome_documento, 'tipo_documento', e.tipo_documento,
            'data_criacao', e.criado_em, 'data_expiracao_convite',
              CASE WHEN m.etapa='documento-concluido' THEN e.finalizado_em + interval '168 hours' ELSE e.expira_em END,
            'nome_signatario', coalesce(autor.nome, destinatario.nome),
            'email_signatario', coalesce(autor.email, destinatario.email)) AS dados,
          m.ocorrido_em
        FROM momentos m
        JOIN envelope_assinatura e ON e.id=m.envelope_id
        JOIN configurados c ON c.etapa=m.etapa
          AND c.empresa_id::text=e.configuracao_registro_assinatura::jsonb->>'empresaId'
          AND m.ocorrido_em >= c.desde
        JOIN "Empresas" empresa ON empresa."EmpresaId"=c.empresa_id
        LEFT JOIN signatario_envelope autor ON autor.id=m.participante_id
        CROSS JOIN LATERAL (
          SELECT s.id, s.nome, s.email FROM signatario_envelope s
          WHERE s.envelope_id=e.id AND (
            m.etapa IN ('documento-concluido', 'assinatura-cancelada')
            OR (m.etapa='assinatura-individual' AND s.id=m.participante_id)
            OR (m.etapa='prazo-proximo' AND s.assinado_em IS NULL AND s.recusado_em IS NULL))
          UNION ALL
          SELECT NULL, NULL, COALESCE(NULLIF(btrim(empresa."EmpresaResponsavelEmail"), ''),
                                      NULLIF(btrim(empresa."EmpresaEmail"), ''))
          WHERE m.etapa IN ('documento-concluido','signatario-recusou','prazo-vencido')
        ) destinatario
        WHERE e.excluido_em IS NULL
          AND (m.etapa='assinatura-cancelada' OR e.cancelado_em IS NULL)
          AND (m.etapa <> 'assinatura-individual' OR e.finalizado_em IS NULL OR NOT EXISTS (
            SELECT 1 FROM configurados conclusao WHERE conclusao.empresa_id=c.empresa_id
              AND conclusao.etapa='documento-concluido' AND conclusao.desde <= e.finalizado_em))
          AND destinatario.email ~ '^[^[:space:],;<>@]+@[^[:space:],;<>@]+$'
      )
      INSERT INTO firma_email_assinatura_fila (chave, empresa_id, envelope_id, signatario_id, etapa, destinatario, dados)
      SELECT c.chave,c.empresa_id,c.envelope_id,c.signatario_id,c.etapa,c.email,c.dados
      FROM candidatos c
      WHERE NOT EXISTS (SELECT 1 FROM firma_email_assinatura_fila f WHERE f.chave=c.chave)
      ORDER BY c.ocorrido_em,c.chave,c.signatario_id NULLS LAST
      LIMIT 250 ON CONFLICT (chave) DO NOTHING
      """;
  private final JdbcTemplate banco;
  private final ObjectMapper json;
  private final EnviadorCodigoConvite enviador;
  private final ModelosEmailAssinatura modelos;

  public RotinaEmailsAssinatura(JdbcTemplate banco, ObjectMapper json, EnviadorCodigoConvite enviador,
      ModelosEmailAssinatura modelos) {
    this.banco = banco;
    this.json = json;
    this.enviador = enviador;
    this.modelos = modelos;
  }

  @Scheduled(fixedDelayString = "${ASSINEAI_EMAIL_AGENDA_INTERVALO_MS:60000}")
  public void agendar() {
    if (enviador.configurado()) banco.update(ENFILEIRAR);
  }

  @Scheduled(fixedDelayString = "${ASSINEAI_EMAIL_FILA_INTERVALO_MS:5000}")
  public void entregar() {
    if (!enviador.configurado()) return;
    banco.update("""
        UPDATE firma_email_assinatura_fila SET estado='falhou', reserva=NULL, reservado_ate=NULL, atualizado_em=now()
        WHERE estado='enviando' AND reservado_ate <= now() AND tentativas >= ?
        """, MAX_TENTATIVAS);
    UUID reserva = UUID.randomUUID();
    var lotes = banco.query("""
        WITH proximo AS (
          SELECT id FROM firma_email_assinatura_fila
          WHERE tentativas < ? AND tentar_em <= now()
            AND (estado='pendente' OR estado='enviando' AND reservado_ate <= now())
          ORDER BY tentar_em,id LIMIT 1 FOR UPDATE SKIP LOCKED
        )
        UPDATE firma_email_assinatura_fila f
        SET estado='enviando', tentativas=tentativas+1, reserva=?, reservado_ate=now()+interval '5 minutes'
        FROM proximo p WHERE p.id=f.id
        RETURNING f.id,f.empresa_id,f.etapa,f.destinatario,f.dados,f.tentativas
        """, (linha, indice) -> new Entrega(linha.getLong(1), linha.getLong(2),
            MomentoEmailAssinatura.de(linha.getString(3)), linha.getString(4), linha.getString(5), linha.getInt(6)),
        MAX_TENTATIVAS, reserva);
    for (Entrega entrega : lotes) entregar(entrega, reserva);
  }

  private void entregar(Entrega entrega, UUID reserva) {
    String estado;
    try {
      var modelo = modelos.publicado(entrega.empresa(), entrega.etapa());
      if (modelo.isEmpty() || !vigente(entrega)) {
        estado = "descartado";
      } else {
        Map<String, String> valores = new LinkedHashMap<>(json.readValue(entrega.dados(), DADOS));
        valores.computeIfPresent("tipo_documento", (chave, valor) -> VariaveisEmailAssinatura.rotulo(valor));
        for (String campo : new String[]{"data_criacao", "data_expiracao_convite"})
          valores.computeIfPresent(campo, (chave, valor) -> VariaveisEmailAssinatura.data(Instant.parse(valor)));
        enviador.enviarHtml(entrega.destino(), ModelosEmailAssinatura.renderizar(modelo.get(), valores));
        estado = "enviado";
      }
    } catch (RuntimeException falha) {
      estado = entrega.tentativas() >= MAX_TENTATIVAS ? "falhou" : "pendente";
      LOG.warn("Falha no e-mail de assinatura da fila {}, etapa {}, tentativa {}. Motivo: {}",
          entrega.id(), entrega.etapa().id(), entrega.tentativas(), falha.getClass().getSimpleName());
    }
    banco.update("""
        UPDATE firma_email_assinatura_fila SET estado=?, reserva=NULL, reservado_ate=NULL,
          atualizado_em=now(), tentar_em=now() + (? * interval '1 minute')
        WHERE id=? AND reserva=?
        """, estado, entrega.tentativas() * 2, entrega.id(), reserva);
  }

  private boolean vigente(Entrega entrega) {
    return Boolean.TRUE.equals(banco.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM firma_email_assinatura_fila f
          JOIN envelope_assinatura e ON e.id=f.envelope_id
          LEFT JOIN signatario_envelope p ON p.id=f.signatario_id
          WHERE f.id=? AND e.excluido_em IS NULL
          AND (f.etapa='assinatura-cancelada' OR e.cancelado_em IS NULL)
          AND (f.etapa <> 'assinatura-individual' OR e.finalizado_em IS NULL OR NOT EXISTS (
            SELECT 1 FROM firma_email_template t
            JOIN firma_email_assinatura_publicacao p ON p.empresa_id=t.empresa_id AND p.etapa=t.etapa
            WHERE t.empresa_id=f.empresa_id AND t.etapa='documento-concluido' AND t.ativo
              AND p.publicado_em <= e.finalizado_em))
          AND (f.etapa <> 'prazo-proximo' OR (e.finalizado_em IS NULL AND e.expira_em > now()
            AND p.assinado_em IS NULL AND p.recusado_em IS NULL))
          AND (f.etapa <> 'prazo-vencido' OR (e.finalizado_em IS NULL AND e.expira_em <= now()
            AND EXISTS (SELECT 1 FROM signatario_envelope pendente WHERE pendente.envelope_id=e.id
              AND pendente.assinado_em IS NULL AND pendente.recusado_em IS NULL)))
        )
        """, Boolean.class, entrega.id()));
  }

  private record Entrega(long id, long empresa, MomentoEmailAssinatura etapa,
      String destino, String dados, int tentativas) {}
}
