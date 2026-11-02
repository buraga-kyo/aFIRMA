package com.afirma.assina;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
@Service
public class IndicadoresAutentica {
 private final ServicoAutentica dados;private final BancoAssineai banco;
 public IndicadoresAutentica(ServicoAutentica dados,BancoAssineai banco){this.dados=dados;this.banco=banco;}
 public Object consultar(ServicoAutentica.Contexto c,LocalDate inicio,LocalDate fim,Long setor){dados.autorizar(c,"atendimentos","read");RegrasAutentica.exigir(!fim.isBefore(inicio)&&java.time.temporal.ChronoUnit.DAYS.between(inicio,fim)<=3660,"Período inválido");var p=new HashMap<String,Object>(Map.of("cliente",c.cliente(),"repositorio",c.repositorio(),"usuario",c.usuario(),"inicio",inicio.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime(),"fim",fim.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime()));String filtro=dados.escopo("Atendimento","a",c)+" AND a.\"createdAt\">=:inicio AND a.\"createdAt\"<:fim";if(setor!=null){dados.registro("Setor",setor,c);p.put("setor",setor);filtro+=" AND a.\"SetorId\"=:setor";}
  String base="WITH atendimentos AS(SELECT a.* FROM autentica.\"Atendimento\" a WHERE "+filtro+") ";var r=new LinkedHashMap<String,Object>();
  r.put("cards",banco.um(base+"SELECT count(*) total,count(*) FILTER(WHERE \"AtendimentoStatus\"='pendente') pendentes,count(*) FILTER(WHERE \"AtendimentoStatus\"='emandamento') em_atendimento,count(*) FILTER(WHERE \"AtendimentoStatus\"='finalizado') finalizados,COALESCE(avg(\"AtendimentoDuracao\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) duracao_media,COALESCE(avg(\"AtendimentoTempoPendente\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) espera_media,COALESCE(avg(\"AtendimentoTempoRespostaAtendente\") FILTER(WHERE \"AtendimentoStatus\"='finalizado'),0) resposta_media,COALESCE(avg(\"AtendimentoAvaliacao\"),0) avaliacao_media FROM atendimentos",p));
  r.put("porAtendente",banco.listar(base+"SELECT a.\"AtendenteId\" id,COALESCE(at.\"AtendenteNome\",'Sem atendente') nome,count(*) total,COALESCE(avg(a.\"AtendimentoDuracao\"),0) duracao_media FROM atendimentos a LEFT JOIN autentica.\"Atendente\" at ON at.id=a.\"AtendenteId\" GROUP BY a.\"AtendenteId\",at.\"AtendenteNome\" ORDER BY total DESC",p));
  r.put("porSetor",banco.listar(base+"SELECT a.\"SetorId\" id,COALESCE(s.\"SetorNome\",'Sem setor') nome,count(*) total FROM atendimentos a LEFT JOIN autentica.\"Setor\" s ON s.id=a.\"SetorId\" GROUP BY a.\"SetorId\",s.\"SetorNome\" ORDER BY total DESC",p));
  r.put("porTag",banco.listar(base+"SELECT t.id,t.\"TagNome\" nome,count(*) total FROM atendimentos a JOIN autentica.\"AtendimentoTag\" at ON at.\"AtendimentoId\"=a.id JOIN autentica.\"Tag\" t ON t.id=at.\"TagId\" GROUP BY t.id,t.\"TagNome\" ORDER BY total DESC",p));
  r.put("porHora",banco.listar(base+"SELECT extract(hour FROM timezone('America/Sao_Paulo',\"createdAt\")) hora,count(*) total FROM atendimentos GROUP BY hora ORDER BY hora",p));
  r.put("porMes",banco.listar(base+"SELECT to_char(timezone('America/Sao_Paulo',\"createdAt\"),'YYYY-MM') mes,count(*) total FROM atendimentos GROUP BY mes ORDER BY mes",p));
  r.put("porDia",banco.listar(base+"SELECT timezone('America/Sao_Paulo',\"createdAt\")::date dia,count(*) total FROM atendimentos GROUP BY dia ORDER BY dia",p));
  r.put("origem",banco.listar(base+"SELECT COALESCE(\"AtendimentoReceptivo\",false) receptivo,count(*) total FROM atendimentos GROUP BY receptivo",p));return r;
 }
}
