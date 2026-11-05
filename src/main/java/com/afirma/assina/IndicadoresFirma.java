package com.afirma.assina;

import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;

/** Monta os indicadores com os atendimentos que a conta pode consultar. */
@Service
public class IndicadoresFirma {
  private final ServicoFirma dados;
  private final BancoAssineai banco;

  public IndicadoresFirma(ServicoFirma dados, BancoAssineai banco) {
    this.dados = dados;
    this.banco = banco;
  }

  public Object consultar(
      ServicoFirma.Contexto contexto, LocalDate inicio, LocalDate fim, Long setor) {
    dados.autorizar(contexto, "atendimentos", "read");
    RegrasFirma.exigir(
        !fim.isBefore(inicio) && java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) <= 3660,
        "Período inválido");
    var parametros =
        new HashMap<String, Object>(
            Map.of(
                "cliente",
                contexto.cliente(),
                "repositorio",
                contexto.repositorio(),
                "usuario",
                contexto.usuario(),
                "inicio",
                inicio.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime(),
                "fim",
                fim.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime()));
    String filtro =
        dados.escopo("Atendimento", "a", contexto)
            + " AND a.\"createdAt\">=:inicio AND a.\"createdAt\"<:fim";
    if (setor != null) {
      dados.registro("Setor", setor, contexto);
      parametros.put("setor", setor);
      filtro += " AND a.\"SetorId\"=:setor";
    }
    String base =
        "WITH atendimentos AS(SELECT a.* FROM firma.\"Atendimento\" a WHERE " + filtro + ") ";
    var dadosAtuais = new LinkedHashMap<String, Object>();
    dadosAtuais.put(
        "cards",
        banco.um(
            base
                + "SELECT count(*) total,count(*) FILTER(WHERE \"AtendimentoStatus\"='pendente') pendentes,count(*) FILTER(WHERE \"AtendimentoStatus\"='emandamento') em_atendimento,count(*) FILTER(WHERE \"AtendimentoStatus\"='finalizado') finalizados,COALESCE(avg(\"AtendimentoDuracao\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) duracao_media,COALESCE(avg(\"AtendimentoTempoPendente\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) espera_media,COALESCE(avg(\"AtendimentoTempoRespostaAtendente\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) resposta_media,COALESCE(avg(\"AtendimentoAvaliacao\"),0) avaliacao_media FROM atendimentos",
            parametros));
    dadosAtuais.put(
        "porAtendente",
        banco.listar(
            base
                + "SELECT a.\"AtendenteId\" id,COALESCE(at.\"AtendenteNome\",'Sem atendente') nome,count(*) total,COALESCE(avg(a.\"AtendimentoDuracao\"),0) duracao_media FROM atendimentos a LEFT JOIN firma.\"Atendente\" at ON at.id=a.\"AtendenteId\" GROUP BY a.\"AtendenteId\",at.\"AtendenteNome\" ORDER BY total DESC",
            parametros));
    dadosAtuais.put(
        "porSetor",
        banco.listar(
            base
                + "SELECT a.\"SetorId\" id,COALESCE(s.\"SetorNome\",'Sem setor') nome,count(*) total FROM atendimentos a LEFT JOIN firma.\"Setor\" s ON s.id=a.\"SetorId\" GROUP BY a.\"SetorId\",s.\"SetorNome\" ORDER BY total DESC",
            parametros));
    dadosAtuais.put(
        "porTag",
        banco.listar(
            base
                + "SELECT t.id,t.\"TagNome\" nome,count(*) total FROM atendimentos a JOIN firma.\"AtendimentoTag\" at ON at.\"AtendimentoId\"=a.id JOIN firma.\"Tag\" t ON t.id=at.\"TagId\" GROUP BY t.id,t.\"TagNome\" ORDER BY total DESC",
            parametros));
    dadosAtuais.put(
        "porHora",
        banco.listar(
            base
                + "SELECT extract(hour FROM timezone('America/Sao_Paulo',\"createdAt\")) hora,count(*) total FROM atendimentos GROUP BY hora ORDER BY hora",
            parametros));
    dadosAtuais.put(
        "porMes",
        banco.listar(
            base
                + "SELECT to_char(timezone('America/Sao_Paulo',\"createdAt\"),'YYYY-MM') mes,count(*) total FROM atendimentos GROUP BY mes ORDER BY mes",
            parametros));
    dadosAtuais.put(
        "porDia",
        banco.listar(
            base
                + "SELECT timezone('America/Sao_Paulo',\"createdAt\")::date dia,count(*) total FROM atendimentos GROUP BY dia ORDER BY dia",
            parametros));
    dadosAtuais.put(
        "origem",
        banco.listar(
            base
                + "SELECT COALESCE(\"AtendimentoReceptivo\",false) receptivo,count(*) total FROM atendimentos GROUP BY receptivo",
            parametros));
    return dadosAtuais;
  }
}
