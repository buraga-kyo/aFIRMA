package com.afirma.assina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssinaApplication {

	public static void main(String[] argumentos) {
		SpringApplication.run(AssinaApplication.class, argumentos);
	}

}
