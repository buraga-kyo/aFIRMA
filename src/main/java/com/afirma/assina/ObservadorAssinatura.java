package com.afirma.assina;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record ObservadorAssinatura(String email, String whatsapp) {
  public static final int LIMITE = 20;

  static Set<Destino> destinos(List<ObservadorAssinatura> observadores) {
    Set<Destino> destinos = new LinkedHashSet<>();
    if (observadores == null) return destinos;
    if (observadores.size() > LIMITE) throw erro("Adicione até 20 observadores.");
    for (ObservadorAssinatura observador : observadores) {
      if (observador == null) throw erro("Informe e-mail ou WhatsApp de cada observador.");
      String email = observador.email() == null ? "" : observador.email().strip().toLowerCase(Locale.ROOT);
      String whatsapp = DadosPessoaisSignatario.whatsapp(observador.whatsapp());
      if (email.isEmpty() && whatsapp.isEmpty()) throw erro("Informe e-mail ou WhatsApp de cada observador.");
      if (!email.isEmpty()) {
        if (email.length() > 254 || email.chars().anyMatch(Character::isISOControl)
            || !email.matches("[^\\s,;<>@]+@[^\\s,;<>@]+\\.[^\\s,;<>@]+")) {
          throw erro("Informe um e-mail válido para o observador.");
        }
        destinos.add(new Destino("email", email));
      }
      if (!whatsapp.isEmpty()) destinos.add(new Destino("whatsapp", whatsapp));
    }
    return destinos;
  }

  // Só vai para o observador o resumo de cada movimentação.
  static String mensagem(String evento, String participante) {
    String quemAssinou = participante == null ? "Um signatário" : participante;
    return switch (evento) {
      case "ENVELOPE_CRIADO" -> "O envelope foi criado.";
      case "CONVITE_ABERTO" -> quemAssinou + " abriu o convite.";
      case "CONVITE_SUBSTITUIDO" -> "O convite de " + quemAssinou + " foi substituído.";
      case "CONVITE_RECUSADO" -> quemAssinou + " recusou a assinatura.";
      case "ACEITE_REGISTRADO" -> quemAssinou + " assinou.";
      case "ENVELOPE_FINALIZADO" -> "A assinatura foi concluída.";
      case "ENVELOPE_CANCELADO" -> "A assinatura foi cancelada.";
      default -> null;
    };
  }

  private static ResponseStatusException erro(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  record Destino(String canal, String contato) {}
}
