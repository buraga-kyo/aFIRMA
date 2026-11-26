package com.afirma.assina;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VariaveisEmailAssinatura {
  public static final String CODIGO = "codigo_verificacao";
  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
      .withZone(ZoneId.of("America/Sao_Paulo"));
  private static final List<Variavel> CATALOGO = List.of(
      new Variavel(CODIGO, "Código de verificação", "12345678"),
      new Variavel("validade_codigo_minutos", "Validade do código em minutos", Long.toString(ServicoAutenticacaoConvites.VALIDADE_CODIGO.toMinutes())),
      new Variavel("nome_signatario", "Nome do signatário", "Ana de Souza"),
      new Variavel("email_signatario", "E-mail do signatário", "ana@example.test"),
      new Variavel("cpf_signatario", "CPF do signatário (mascarado)", "••••42"),
      new Variavel("rg_signatario", "RG do signatário (mascarado)", "••••57"),
      new Variavel("whatsapp_signatario", "WhatsApp do signatário (mascarado)", "••••1234"),
      new Variavel("qualificacao_signatario", "Qualificação do signatário", "Administradora"),
      new Variavel("papel_signatario", "Papel do signatário", "Representante"),
      new Variavel("representado", "Pessoa ou empresa representada", "Empresa de exemplo"),
      new Variavel("fundamento_representacao", "Fundamento da representação", "Procuração"),
      new Variavel("forma_autenticacao", "Métodos de autenticação configurados", "E-mail"),
      new Variavel("forma_notificacao", "Canais de notificação configurados", "E-mail, WhatsApp"),
      new Variavel("empresa_participante", "Empresa participa da assinatura", "Sim"),
      new Variavel("nome_empresa", "Nome da empresa", "Empresa de exemplo"),
      new Variavel("nome_assinatura", "Título da assinatura", "Contrato de prestação de serviços"),
      new Variavel("id_assinatura", "ID da assinatura", "00000000-0000-0000-0000-000000000001"),
      new Variavel("nome_documento", "Nome do documento", "Contrato de exemplo.pdf"),
      new Variavel("tipo_documento", "Tipo de documento", "Contrato particular"),
      new Variavel("data_criacao", "Data de criação da assinatura", "10/11/2026 às 09:30"),
      new Variavel("data_expiracao_convite", "Data de expiração do convite", "17/11/2026 às 09:30"));

  private VariaveisEmailAssinatura() {}

  public static List<Variavel> catalogo() { return CATALOGO; }

  private static final java.util.Set<String> COMUNS = java.util.Set.of("nome_empresa", "nome_assinatura",
      "id_assinatura", "nome_documento", "tipo_documento", "data_criacao", "data_expiracao_convite");
  private static final java.util.Set<String> SIGNATARIO = java.util.Set.of("nome_signatario", "email_signatario");

  static List<Variavel> para(MomentoEmailAssinatura etapa) {
    if (etapa.verificacao()) return CATALOGO;
    boolean temSignatario = switch (etapa) {
      case ASSINATURA, RECUSA, LEMBRETE -> true;
      default -> false;
    };
    return CATALOGO.stream().filter(item -> COMUNS.contains(item.nome())
        || temSignatario && SIGNATARIO.contains(item.nome())).toList();
  }

  static boolean permite(MomentoEmailAssinatura etapa, String nome, boolean corpo) {
    return (corpo || !CODIGO.equals(nome)) && para(etapa).stream().anyMatch(variavel -> variavel.nome().equals(nome));
  }

  static Map<String, String> exemplo(MomentoEmailAssinatura etapa) {
    var dados = new LinkedHashMap<String, String>();
    para(etapa).forEach(variavel -> dados.put(variavel.nome(), variavel.exemplo()));
    return dados;
  }

  static String data(Instant valor) { return valor == null ? "" : DATA.format(valor); }

  static String mascarar(String valor, int visiveis) {
    if (valor == null || valor.isBlank()) return "";
    String limpo = valor.strip();
    int tamanho = limpo.codePointCount(0, limpo.length());
    return tamanho <= visiveis ? "••••" : "••••" + limpo.substring(limpo.offsetByCodePoints(0, tamanho - visiveis));
  }

  static String rotulo(String valor) {
    if (valor == null || valor.isBlank()) return "";
    String texto = valor.replace('_', ' ').toLowerCase(java.util.Locale.forLanguageTag("pt-BR"));
    return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
  }

  public record Variavel(String nome, String rotulo, String exemplo) {}
}
