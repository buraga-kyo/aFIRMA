package com.afirma.assina;

import java.util.*;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Cuida da senha e da recuperação por e-mail. Ao trocar a senha, encerra as sessões do usuário. */
@Service
public class ContaFirma {
  private final BancoAssineai banco;
  private final FirmaPersistencia persistencia;
  private final ObjectProvider<JavaMailSender> emails;
  private final Environment ambiente;

  public ContaFirma(
      BancoAssineai banco,
      FirmaPersistencia persistencia,
      ObjectProvider<JavaMailSender> emails,
      Environment ambiente) {
    this.banco = banco;
    this.persistencia = persistencia;
    this.emails = emails;
    this.ambiente = ambiente;
  }

  @Transactional
  public void solicitar(String email) {
    RegrasFirma.exigir(email != null && email.length() <= 254, "E-mail inválido");
    if (emails.getIfAvailable() == null
        || Objects.toString(ambiente.getProperty("spring.mail.host"), "").isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recuperação por e-mail não configurada");
    var usuarios =
        banco.listar(
            "SELECT u.* FROM firma_identidade.\"Usuario\" u JOIN firma.\"Cliente\" c ON c.id=u.\"UsuarioClienteId\" WHERE lower(u.\"UsuarioEmail\")=lower(:e) AND NOT COALESCE(u.\"UsuarioBloqueado\",false) AND COALESCE(c.\"ClienteStatus\",true)",
            Map.of("e", email));
    if (usuarios.size() != 1) return;
    var usuarioAtual = usuarios.getFirst();
    banco.um(
        "SELECT id FROM firma_identidade.\"Usuario\" WHERE id=:id FOR UPDATE",
        Map.of("id", usuarioAtual.get("id")));
    var recente =
        banco.um(
            "SELECT usuario_id FROM firma_relacionamento_recuperacao WHERE usuario_id=:id AND solicitada_em>now()-interval '1 minute'",
            Map.of("id", usuarioAtual.get("id")));
    if (recente != null) return;
    String codigo = String.format("%06d", new SecureRandom().nextInt(1000000));
    persistencia.gravar(
        "Usuario",
        ((Number) usuarioAtual.get("id")).longValue(),
        Map.of(
            "UsuarioRecSenhaToken",
            codigo,
            "UsuarioRecSenhaTokenTempoExpirar",
            OffsetDateTime.now().plusMinutes(10)));
    banco.executar(
        "INSERT INTO firma_relacionamento_recuperacao(usuario_id) VALUES(:id) ON CONFLICT(usuario_id) DO UPDATE SET tentativas=0,solicitada_em=now()",
        Map.of("id", usuarioAtual.get("id")));
    var emailRecuperacao = new SimpleMailMessage();
    emailRecuperacao.setTo(email);
    emailRecuperacao.setFrom(
        ambiente.getProperty(
            "APP_EMAIL_FROM", ambiente.getProperty("spring.mail.username", "FIRMA@localhost")));
    emailRecuperacao.setSubject("FIRMA · Recuperar acesso");
    emailRecuperacao.setText(
        "Seu código para recuperar o acesso à FIRMA é "
            + codigo
            + ". Ele expira em 10 minutos. Se não solicitou, ignore esta mensagem.");
    emails.getObject().send(emailRecuperacao);
  }

  // Tentativas inválidas precisam persistir mesmo quando o pedido é recusado.
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public void redefinir(String email, String codigo, String senha) {
    RegrasFirma.exigir(
        senha != null && senha.length() >= 8 && senha.length() <= 256,
        "A senha deve ter entre 8 e 256 caracteres");
    var usuarios =
        banco.listar(
            "SELECT u.* FROM firma_identidade.\"Usuario\" u WHERE lower(u.\"UsuarioEmail\")=lower(:e) AND NOT COALESCE(u.\"UsuarioBloqueado\",false) FOR UPDATE",
            Map.of("e", Objects.toString(email, "")));
    if (usuarios.size() != 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado");
    var usuarioAtual = usuarios.getFirst();
    var permitido =
        banco.um(
            "UPDATE firma_relacionamento_recuperacao SET tentativas=tentativas+1 WHERE usuario_id=:id AND tentativas<5 RETURNING usuario_id",
            Map.of("id", usuarioAtual.get("id")));
    boolean valido =
        permitido != null
            && codigo != null
            && codigo.matches("[0-9]{6}")
            && java.security.MessageDigest.isEqual(
                codigo.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Objects.toString(usuarioAtual.get("UsuarioRecSenhaToken"), "")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var prazo =
        banco.um(
            "SELECT id FROM firma_identidade.\"Usuario\" WHERE id=:id AND \"UsuarioRecSenhaTokenTempoExpirar\">now()",
            Map.of("id", usuarioAtual.get("id")));
    if (!valido || prazo == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado");
    trocar(((Number) usuarioAtual.get("id")).longValue(), senha);
  }

  private void trocar(long id, String senha) {
    var credencial = SenhaFirma.criar(senha);
    var dados = new HashMap<String, Object>();
    dados.put("UsuarioSenha", credencial.hash());
    dados.put("UsuarioRandomBytes", credencial.sal());
    dados.put("UsuarioRecSenhaToken", null);
    dados.put("UsuarioRecSenhaTokenTempoExpirar", null);
    persistencia.gravar("Usuario", id, dados);
    banco.executar(
        "UPDATE firma_identidade.\"Sessao\" SET \"SessaoStatus\"='F' WHERE \"UsuarioId\"=:id",
        Map.of("id", id));
    banco.executar(
        "DELETE FROM firma_relacionamento_recuperacao WHERE usuario_id=:id", Map.of("id", id));
  }

  @Transactional
  public void senha(ServicoFirma.Contexto contexto, String atual, String nova) {
    RegrasFirma.exigir(
        contexto.usuario() > 0, "Use o perfil da empresa para gerenciar a conta proprietária");
    RegrasFirma.exigir(
        nova != null && nova.length() >= 8 && nova.length() <= 256,
        "A senha deve ter entre 8 e 256 caracteres");
    var usuarioAtual =
        banco.um(
            "SELECT * FROM firma_identidade.\"Usuario\" WHERE id=:id AND \"UsuarioClienteId\"=:c FOR UPDATE",
            Map.of("id", contexto.usuario(), "c", contexto.cliente()));
    if (usuarioAtual == null
        || !SenhaFirma.verificar(
            Objects.toString(atual, ""),
            Objects.toString(usuarioAtual.get("UsuarioSenha"), ""),
            Objects.toString(usuarioAtual.get("UsuarioRandomBytes"), "")))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual inválida");
    trocar(contexto.usuario(), nova);
  }
}
