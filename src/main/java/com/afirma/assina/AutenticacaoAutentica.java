package com.afirma.assina;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/autentica")
public class AutenticacaoAutentica {
 private final BancoAssineai banco;private final IdentidadeAssineai identidade;private final String segredo;private final ContaAutentica conta;private final ServicoAutentica servico;
 public AutenticacaoAutentica(BancoAssineai banco,IdentidadeAssineai identidade,@Value("${APP_ASSINEAI_JWT_SECRET:${JWT_SECRET:}}")String segredo,Environment env,ContaAutentica conta,ServicoAutentica servico){this.conta=conta;this.servico=servico;this.banco=banco;this.identidade=identidade;this.segredo=segredo.isBlank()&&env.matchesProfiles("local-test")?"afirma-local-test-compatibilidade-jwt-2026":segredo;}
 @PostMapping("/login") public Object entrar(@RequestBody Map<String,Object> dados,HttpServletRequest r) throws Exception {
   String email=Objects.toString(dados.get("email"),"").strip(),senha=Objects.toString(dados.get("password"),"");
   if(email.length()>254||senha.length()>256)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Credenciais inválidas");
   try{if(senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72)return identidade.login(dados);}catch(ResponseStatusException e){if(e.getStatusCode().value()!=401)throw e;}
   if(segredo.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Acesso não configurado");
   var usuarios=banco.listar("SELECT u.* FROM autentica_identidade.\"Usuario\" u JOIN autentica.\"Cliente\" c ON c.id=u.\"UsuarioClienteId\" WHERE lower(u.\"UsuarioEmail\")=lower(:email) AND NOT COALESCE(u.\"UsuarioBloqueado\",false) AND COALESCE(c.\"ClienteStatus\",true)",Map.of("email",email));
   var validos=usuarios.stream().filter(u->{return SenhaAutentica.verificar(senha,Objects.toString(u.get("UsuarioSenha"),""),Objects.toString(u.get("UsuarioRandomBytes"),""));}).toList();
   if(validos.size()!=1)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"E-mail ou senha inválidos");
   var u=validos.getFirst();String token=TokensEmpresaAssineai.emitir("usuario:"+u.get("id"),segredo);
   banco.executar("INSERT INTO autentica_identidade.\"Sessao\"(\"UsuarioId\",\"RepositorioId\",\"SessaoJWT\",\"SessaoStatus\",\"SessaoTipoAutenticacao\",\"SessaoCriacao\",\"SessaoUltimoAcesso\",\"SessaoIP\",\"createdAt\",\"updatedAt\") VALUES(:u,:r,:token,'A','local',now(),now(),:ip,now(),now())",Map.of("u",u.get("id"),"r",u.get("RepositorioId"),"token",token,"ip",r.getRemoteAddr()));return Map.of("token",token);
 }
 @PostMapping("/logout") public void sair(HttpServletRequest r){identidade.autenticar(r);String token=r.getHeader("Authorization").substring(7);banco.executar("INSERT INTO firma_autentica_token_revogado(resumo) VALUES(:resumo) ON CONFLICT DO NOTHING",Map.of("resumo",resumo(token)));banco.executar("UPDATE autentica_identidade.\"Sessao\" SET \"SessaoStatus\"='F',\"updatedAt\"=now() WHERE \"SessaoJWT\"=:t",Map.of("t",token));}

 static String resumo(String token){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @PostMapping("/recuperar-senha") public Object recuperar(@RequestBody Map<String,Object> dados){conta.solicitar(Objects.toString(dados.get("email"),""));return Map.of("mensagem","Se houver uma conta elegível, enviaremos o código por e-mail.");}
 @PostMapping("/redefinir-senha") public void redefinir(@RequestBody Map<String,Object> dados){conta.redefinir(Objects.toString(dados.get("email"),""),Objects.toString(dados.get("codigo"),""),Objects.toString(dados.get("senha"),""));}
 @PutMapping("/conta/senha") public void senha(@RequestBody Map<String,Object> dados,HttpServletRequest r){conta.senha(servico.contexto(identidade.autenticar(r)),Objects.toString(dados.get("atual"),""),Objects.toString(dados.get("nova"),""));}
}
