package com.afirma.assina;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
public class ChatRotina {
	private static final Logger log = LoggerFactory.getLogger(ChatRotina.class);
	private final ChatServico servico;
	private final ChatTransporte transporte;
	private final Environment ambiente;

	public ChatRotina(ChatServico servico, ChatTransporte transporte, Environment ambiente) {
		this.servico = servico;
		this.transporte = transporte;
		this.ambiente = ambiente;
	}

	@Scheduled(initialDelay = 30000, fixedDelay = 30000)
	public void reconectarCanais() {
		if (ambiente.matchesProfiles("local-test") || !transporte.configurado()) return;
		for (var canal : servico.canaisAtivos()) {
			try {
				transporte.conectar(canal);
				servico.estadoCanal(canal, transporte.estado(canal));
			}
			catch (RuntimeException erro) { log.warn("Canal {} aguardando ponte WhatsApp", canal); }
		}
	}

	@Scheduled(initialDelay = 60000, fixedDelay = 60000)
	public void encerrarInativos() {
		for (var aviso : servico.finalizarInativos()) {
			servico.enviarAvisoEncerramento(aviso);
		}
	}
}
