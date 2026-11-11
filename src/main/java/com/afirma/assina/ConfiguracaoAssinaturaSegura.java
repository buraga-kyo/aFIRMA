package com.afirma.assina;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracaoAssinaturaSegura {
	private final boolean permitirCertificadoVencido;

	public ConfiguracaoAssinaturaSegura(Environment ambiente) {
		this.permitirCertificadoVencido = ambiente.getProperty("APP_PERMITIR_CERTIFICADO_VENCIDO", Boolean.class, false);
	}

	public boolean permitirCertificadoVencido() {
		return permitirCertificadoVencido;
	}
}
