package com.afirma.assina;

import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum MomentoEmailAssinatura {
  VERIFICACAO("verificacao", "Código de verificação", ""),
  ASSINATURA("assinatura-individual", "Assinatura registrada",
      "<p>Olá, {{nome_signatario}}. Sua assinatura em {{nome_assinatura}} foi registrada.</p>"),
  CONCLUSAO("documento-concluido", "Documento concluído",
      "<p>A assinatura {{nome_assinatura}} foi concluída. Os signatários podem consultar o PDF final na página do convite original.</p>"),
  RECUSA("signatario-recusou", "Signatário recusou",
      "<p>{{nome_signatario}} recusou o convite para {{nome_assinatura}}. Consulte a assinatura no Cartório para ver os detalhes.</p>"),
  LEMBRETE("prazo-proximo", "Prazo próximo do fim",
      "<p>Olá, {{nome_signatario}}. Você pode assinar {{nome_assinatura}} até {{data_expiracao_convite}}. Use o convite que recebeu.</p>"),
  VENCIMENTO("prazo-vencido", "Prazo vencido",
      "<p>O prazo para assinar {{nome_assinatura}} terminou em {{data_expiracao_convite}} com assinaturas pendentes.</p>"),
  CANCELAMENTO("assinatura-cancelada", "Assinatura cancelada",
      "<p>A assinatura {{nome_assinatura}} foi cancelada. O convite para assinar perdeu a validade.</p>");

  private final String id;
  private final String titulo;
  private final String html;

  MomentoEmailAssinatura(String id, String titulo, String html) {
    this.id = id;
    this.titulo = titulo;
    this.html = html;
  }

  public String id() { return id; }
  public String titulo() { return titulo; }
  String html() { return html; }
  boolean verificacao() { return this == VERIFICACAO; }

  public static MomentoEmailAssinatura de(String valor) {
    return Arrays.stream(values()).filter(item -> item.id.equals(valor)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Momento de envio não encontrado."));
  }
}
