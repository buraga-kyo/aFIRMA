package com.afirma.assina;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoPublicacaoNostr {
  private final PerfisNostrConfigurados perfis;
  private final AssinadorRemotoNostr assinador;
  private final CriptografiaNostr criptografia;
  private final CarimboNostr carimbo;
  private final ConexoesNostr conexoes;
  private final LimiteAssinadorNostr limite;
  private final ObjectMapper json;

  public ServicoPublicacaoNostr(
      PerfisNostrConfigurados perfis,
      AssinadorRemotoNostr assinador,
      CriptografiaNostr criptografia,
      CarimboNostr carimbo,
      ConexoesNostr conexoes,
      LimiteAssinadorNostr limite,
      ObjectMapper json) {
    this.perfis = perfis;
    this.assinador = assinador;
    this.criptografia = criptografia;
    this.carimbo = carimbo;
    this.conexoes = conexoes;
    this.limite = limite;
    this.json = json;
  }

  public boolean configurado(long empresa) {
    return perfis.configurado(empresa);
  }

  public boolean configurado() {
    return perfis.configurado();
  }

  public Map<String, Object> identidades(long empresa) {
    return Map.of("identidades", perfis.listar(empresa));
  }

  public Map<String, Object> conferirIdentidade(long empresa, String referencia) {
    try {
      return assinador.conferirIdentidade(perfis.buscar(empresa, referencia));
    } catch (Exception falha) {
      throw indisponivel(falha);
    }
  }

  public String conferirAceite(long empresa, String referencia, Map<String, ?> evento) {
    perfis.buscar(empresa, referencia);
    return criptografia.conferirAceite(evento).get("id").toString();
  }

  public Map<String, Object> assinar(Map<String, ?> pedido) {
    try {
      var perfil = perfilDoPedido(pedido);
      // A reserva fica no PostgreSQL, inclusive quando o assinador recusa ou o processo reinicia.
      limite.reservar(perfil.empresa, perfil.referencia);
      return assinador.assinar(perfil, pedido);
    } catch (Exception falha) {
      throw indisponivel(falha);
    }
  }

  public Map<String, Object> publicar(Map<String, ?> pedido) {
    try {
      var perfil = perfilDoPedido(pedido);
      var evento =
          criptografia.conferirResposta(
              json.readValue(pedido.get("eventoCanonico").toString(), Map.class),
              criptografia.montarEvento(pedido),
              perfil.chavePublica);
      var recibos = new ArrayList<Map<String, Object>>();
      try (var tarefas = Executors.newVirtualThreadPerTaskExecutor()) {
        var chamadas = new ArrayList<Callable<Map<String, Object>>>();
        for (var endereco : perfil.relays) chamadas.add(() -> enviarAoRelay(endereco, evento));
        var respostas = tarefas.invokeAll(chamadas, 12, TimeUnit.SECONDS);
        for (int i = 0; i < respostas.size(); i++) {
          try {
            recibos.add(respostas.get(i).get());
          } catch (ExecutionException | CancellationException falha) {
            recibos.add(
                Map.of(
                    "url",
                    perfil.relays.get(i).toString(),
                    "aceito",
                    false,
                    "observadoEm",
                    Instant.now().toString()));
          }
        }
      }
      return Map.of("relays", recibos);
    } catch (Exception falha) {
      throw indisponivel(falha);
    }
  }

  public Map<String, Object> carimbar(Map<String, ?> pedido) {
    try {
      if (!"COMPROVANTE".equals(pedido.get("tipo"))) throw CriptografiaNostr.invalido();
      // Uma prova antiga continua sendo carimbada mesmo depois da troca ou remoção do assinador.
      return carimbo.carimbar(pedido);
    } catch (Exception falha) {
      throw indisponivel(falha);
    }
  }

  Map<String, Object> enviarAoRelay(java.net.URI endereco, Map<String, ?> evento) throws Exception {
    boolean aceito = false;
    try (var canal = new CanalRelayNostr(conexoes.cliente(), endereco)) {
      canal.enviar(json.writeValueAsString(List.of("EVENT", evento)));
      long ate = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      for (int recebidas = 0; recebidas < 64; recebidas++) {
        List<?> mensagem = json.readValue(canal.esperar(ate), List.class);
        if (mensagem.size() >= 3
            && "OK".equals(mensagem.get(0))
            && evento.get("id").equals(mensagem.get(1))) {
          aceito = Boolean.TRUE.equals(mensagem.get(2));
          break;
        }
      }
    } catch (InterruptedException falha) {
      throw falha;
    } catch (Exception falha) {
      /* A falha deste relay não apaga os recibos dos demais. */
    }
    return Map.of(
        "url", endereco.toString(), "aceito", aceito, "observadoEm", Instant.now().toString());
  }

  private PerfisNostrConfigurados.Perfil perfilDoPedido(Map<String, ?> pedido) {
    return perfis.buscar(
        CriptografiaNostr.inteiro(pedido.get("empresaId")),
        Objects.toString(pedido.get("referencia"), ""));
  }

  private ResponseStatusException indisponivel(Exception falha) {
    if (falha instanceof InterruptedException) Thread.currentThread().interrupt();
    // Respostas do bunker podem conter links de autorização. Não repassa a mensagem ou causa para
    // logs e API.
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "A operação Nostr não foi concluída; confira a configuração e a autorização do assinador");
  }
}
