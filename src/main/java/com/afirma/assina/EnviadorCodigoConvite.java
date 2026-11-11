package com.afirma.assina;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EnviadorCodigoConvite {
	private final ObjectProvider<JavaMailSender> remetentes;
	private final String enderecoRemetente;
	private final boolean servidorConfigurado;

	public EnviadorCodigoConvite(ObjectProvider<JavaMailSender> remetentes, Environment ambiente) {
		this.remetentes = remetentes;
		enderecoRemetente = ambiente.getProperty("ASSINEAI_EMAIL_REMETENTE", "");
		servidorConfigurado = !ambiente.getProperty("ASSINEAI_SMTP_HOST", "").isBlank();
		if (servidorConfigurado && !ambiente.matchesProfiles("local-test")
				&& !ambiente.getProperty("ASSINEAI_SMTP_STARTTLS", Boolean.class, true)) {
			throw new IllegalArgumentException("O envio de códigos em produção exige SMTP com STARTTLS");
		}
	}

	public boolean configurado() { return servidorConfigurado && !enderecoRemetente.isBlank() && remetentes.getIfAvailable() != null; }

	public void enviar(String destinatario, String nomeDocumento, String codigo) {
		if (!configurado()) throw indisponivel();
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(enderecoRemetente);
		mensagem.setTo(destinatario);
		mensagem.setSubject("Seu código para acessar o documento");
		mensagem.setText("Código: " + codigo + "\n\nDocumento: " + nomeDocumento.replaceAll("[\\p{Cntrl}]", " ")
				+ "\nO código vale por 10 minutos. Digite-o apenas na página do convite que você abriu. "
				+ "Não compartilhe o código. Se você não solicitou este acesso, ignore esta mensagem.\n");
		try { remetentes.getObject().send(mensagem); }
		catch (org.springframework.mail.MailException erro) { throw indisponivel(); }
	}

	public void enviarMensagem(String destinatario, String assunto, String texto) {
		if (!configurado()) throw indisponivel();
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(enderecoRemetente);
		mensagem.setTo(destinatario);
		mensagem.setSubject(assunto);
		mensagem.setText(texto);
		try { remetentes.getObject().send(mensagem); }
		catch (org.springframework.mail.MailException erro) { throw indisponivel(); }
	}

	private ResponseStatusException indisponivel() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio de código indisponível. Contate a empresa responsável pelo documento");
	}
}
