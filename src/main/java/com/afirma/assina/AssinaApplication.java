package com.afirma.assina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssinaApplication {

  public static void main(String[] argumentos) {
    if (argumentos.length == 2 && "--preparar-nostr".equals(argumentos[0])) {
      try {
        PrepararConfigNostr.preparar(java.nio.file.Path.of(argumentos[1]));
      } catch (Exception falha) {
        System.err.println(
            "Não foi possível preparar a configuração Nostr. Confira o caminho absoluto, os campos"
                + " e as permissões.");
        System.exit(2);
      }
      return;
    }
    SpringApplication.run(AssinaApplication.class, argumentos);
  }
}
