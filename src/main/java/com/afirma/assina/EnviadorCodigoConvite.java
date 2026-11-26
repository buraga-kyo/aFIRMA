package com.afirma.assina;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EnviadorCodigoConvite {
	private final ObjectProvider<JavaMailSender> remetentes;
	private final String enderecoRemetente;
	private final boolean servidorConfigurado;
	private final boolean testeLocal;
	private final ModelosEmailAssinatura modelos;

	public EnviadorCodigoConvite(ObjectProvider<JavaMailSender> remetentes, Environment ambiente, ModelosEmailAssinatura modelos) {
		this.remetentes = remetentes;
		this.modelos = modelos;
		testeLocal = ambiente.matchesProfiles("local-test")
				&& java.util.Set.of("localhost", "127.0.0.1").contains(ambiente.getProperty("ASSINEAI_SMTP_HOST", ""))
				&& ambiente.getProperty("ASSINEAI_SMTP_PORT", Integer.class, 587) == 1025;
		enderecoRemetente = ambiente.getProperty("ASSINEAI_EMAIL_REMETENTE", "");
		servidorConfigurado = !ambiente.getProperty("ASSINEAI_SMTP_HOST", "").isBlank();
		if (servidorConfigurado && !ambiente.matchesProfiles("local-test")
				&& !ambiente.getProperty("ASSINEAI_SMTP_STARTTLS", Boolean.class, true)) {
			throw new IllegalArgumentException("O envio de códigos em produção exige SMTP com STARTTLS");
		}
	}

	public boolean configurado() { return servidorConfigurado && !enderecoRemetente.isBlank() && remetentes.getIfAvailable() != null; }

	public void enviar(String destinatario, java.util.Map<String, String> dados, String codigo, long empresaId) {
		var valores = new java.util.HashMap<>(dados);
		valores.put(VariaveisEmailAssinatura.CODIGO, codigo);
		valores.put("validade_codigo_minutos", Long.toString(ServicoAutenticacaoConvites.VALIDADE_CODIGO.toMinutes()));
		enviarHtml(destinatario, ModelosEmailAssinatura.renderizar(modelos.publicado(empresaId), valores));
	}

	public boolean testeDisponivel() {
		return testeLocal && configurado();
	}

	public void enviarTeste(ModelosEmailAssinatura.Mensagem mensagem) {
		if (!testeDisponivel()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"O teste requer SMTP local na porta 1025 com o perfil local-test.");
		enviarHtml("teste-assinatura@example.test", mensagem);
	}

	void enviarHtml(String destinatario, ModelosEmailAssinatura.Mensagem conteudo) {
		if (!configurado()) throw indisponivel();
		try {
			var remetente = remetentes.getObject();
			var mensagem = remetente.createMimeMessage();
			var mime = new MimeMessageHelper(mensagem, true, "UTF-8");
			mime.setFrom(enderecoRemetente);
			mime.setTo(destinatario);
			mime.setSubject(conteudo.assunto());
			mime.setText(conteudo.texto(), conteudo.html());
			remetente.send(mensagem);
		} catch (jakarta.mail.MessagingException | org.springframework.mail.MailException erro) {
			throw indisponivel();
		}
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
