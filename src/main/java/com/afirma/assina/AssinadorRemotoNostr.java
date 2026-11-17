package com.afirma.assina;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AssinadorRemotoNostr {
  private final ObjectMapper json;
  private final CriptografiaNostr criptografia;
  private final CifraConversaNostr cifra;
  private final ConexoesNostr conexoes;

  public AssinadorRemotoNostr(
      ObjectMapper json,
      CriptografiaNostr criptografia,
      CifraConversaNostr cifra,
      ConexoesNostr conexoes) {
    this.json = json;
    this.criptografia = criptografia;
    this.cifra = cifra;
    this.conexoes = conexoes;
  }

  Map<String, Object> conferirIdentidade(PerfisNostrConfigurados.Perfil perfil) throws Exception {
    conversar(perfil, null);
    return Map.of("chavePublica", perfil.chavePublica);
  }

  Map<String, Object> assinar(PerfisNostrConfigurados.Perfil perfil, Map<String, ?> pedido)
      throws Exception {
    var esperado = criptografia.montarEvento(pedido);
    var evento = conversar(perfil, esperado);
    return Map.of(
        "eventoCanonico",
        criptografia.serializarEvento(evento),
        "eventoId",
        evento.get("id"),
        "chavePublica",
        evento.get("pubkey"));
  }

  private Map<String, Object> conversar(
      PerfisNostrConfigurados.Perfil perfil, Map<String, ?> rascunho) throws Exception {
    byte[] chave = perfil.chaveCliente();
    try {
      byte[] conversa = cifra.chaveDaConversa(chave, perfil.publicaAssinador);
      // Tenta outro canal só para abrir a conexão. Não repete um pedido de assinatura após resposta
      // ambígua.
      CanalRelayNostr canal = null;
      for (var endereco : perfil.canais) {
        try {
          canal = new CanalRelayNostr(conexoes.cliente(), endereco);
          break;
        } catch (Exception falha) {
          if (falha instanceof InterruptedException) throw falha;
        }
      }
      if (canal == null) throw new IllegalStateException("Nenhum canal do assinador respondeu");
      try (var aberto = canal) {
        String inscricao = UUID.randomUUID().toString(),
            publicaCliente = criptografia.chavePublica(chave);
        aberto.enviar(
            json.writeValueAsString(
                List.of(
                    "REQ",
                    inscricao,
                    Map.of(
                        "kinds",
                        List.of(24133),
                        "authors",
                        List.of(perfil.publicaAssinador),
                        "#p",
                        List.of(publicaCliente)))));
        String publica;
        try {
          publica = pedir(aberto, inscricao, perfil, chave, conversa, "get_public_key", List.of());
        } catch (RespostaRemotaRecusada | CanalRelayNostr.SemResposta falha) {
          // O segredo bunker pode ser de uso único. Um cliente já autorizado não precisa enviá-lo
          // de novo.
          pedir(
              aberto,
              inscricao,
              perfil,
              chave,
              conversa,
              "connect",
              List.of(
                  perfil.publicaAssinador, perfil.segredoPareamento, "sign_event:0,sign_event:1"));
          publica = pedir(aberto, inscricao, perfil, chave, conversa, "get_public_key", List.of());
        }
        if (!perfil.chavePublica.equals(publica))
          throw new IllegalArgumentException(
              "A chave retornada pelo assinador não corresponde ao perfil cadastrado");
        if (rascunho == null) return Map.of();
        String resposta =
            pedir(
                aberto,
                inscricao,
                perfil,
                chave,
                conversa,
                "sign_event",
                List.of(json.writeValueAsString(rascunho)));
        return criptografia.conferirResposta(
            json.readValue(resposta, Map.class), rascunho, perfil.chavePublica);
      } finally {
        Arrays.fill(conversa, (byte) 0);
      }
    } finally {
      Arrays.fill(chave, (byte) 0);
    }
  }

  @SuppressWarnings("unchecked")
  private String pedir(
      CanalRelayNostr canal,
      String inscricao,
      PerfisNostrConfigurados.Perfil perfil,
      byte[] chave,
      byte[] conversa,
      String metodo,
      List<String> parametros)
      throws Exception {
    String identificador = UUID.randomUUID().toString();
    String conteudo =
        json.writeValueAsString(
            Map.of("id", identificador, "method", metodo, "params", parametros));
    var evento =
        criptografia.assinarConversa(
            Map.of(
                "kind",
                24133,
                "created_at",
                Instant.now().getEpochSecond(),
                "tags",
                List.of(List.of("p", perfil.publicaAssinador)),
                "content",
                cifra.cifrar(conteudo, conversa)),
            chave);
    canal.enviar(json.writeValueAsString(List.of("EVENT", evento)));
    long ate = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    for (int recebidas = 0; recebidas < 128; recebidas++) {
      List<?> mensagem = json.readValue(canal.esperar(ate), List.class);
      if (mensagem.size() != 3
          || !"EVENT".equals(mensagem.get(0))
          || !inscricao.equals(mensagem.get(1))
          || !(mensagem.get(2) instanceof Map<?, ?>)) continue;
      Map<String, Object> retorno;
      try {
        retorno = criptografia.conferirEvento((Map<String, ?>) mensagem.get(2));
      } catch (RuntimeException falha) {
        continue;
      }
      if (!perfil.publicaAssinador.equals(retorno.get("pubkey"))
          || CriptografiaNostr.inteiro(retorno.get("kind")) != 24133
          || !(retorno.get("tags") instanceof List<?> tags)
          || !tags.stream()
              .anyMatch(t ->
                  t instanceof List<?> tag
                      && tag.size() >= 2
                      && "p".equals(tag.get(0))
                      && criptografia.chavePublica(chave).equals(tag.get(1)))) continue;
      Map<?, ?> resposta;
      try {
        resposta =
            json.readValue(cifra.abrir(retorno.get("content").toString(), conversa), Map.class);
      } catch (RuntimeException falha) {
        continue;
      }
      if (!identificador.equals(resposta.get("id"))) continue;
      if ("auth_url".equals(resposta.get("result")))
        throw new IllegalStateException(
            "Autorize o cliente de automação no assinador Nostr antes de continuar");
      if (resposta.get("error") != null) throw new RespostaRemotaRecusada();
      if (!(resposta.get("result") instanceof String resultado))
        throw new IllegalStateException("Resposta do assinador inválida");
      return resultado;
    }
    throw new IllegalStateException("O assinador enviou respostas demais sem concluir o pedido");
  }

  static final class RespostaRemotaRecusada extends Exception {}
}
