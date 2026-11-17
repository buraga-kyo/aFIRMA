package com.afirma.assina;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CarimboNostr {
  private final ConexoesNostr conexoes;
  private final ObjectMapper json;
  private final CriptografiaNostr criptografia;
  private final String enderecoBitcoin, usuarioBitcoin, senhaBitcoin;
  private ConfiguracaoBlockchainEmpresa conexoesEmpresa;

  @org.springframework.beans.factory.annotation.Autowired
  void usarConexoes(ConfiguracaoBlockchainEmpresa conexoes) {
    this.conexoesEmpresa = conexoes;
  }

  private ConfiguracaoBlockchainEmpresa.Bitcoin bitcoinPadrao() {
    return new ConfiguracaoBlockchainEmpresa.Bitcoin(enderecoBitcoin, usuarioBitcoin, senhaBitcoin);
  }

  private ConfiguracaoBlockchainEmpresa.Bitcoin bitcoinDaEmpresa(long empresa) {
    var escolhido = conexoesEmpresa == null ? null : conexoesEmpresa.bitcoin(empresa);
    return escolhido == null ? bitcoinPadrao() : escolhido;
  }

  public Map<String, Object> testarBitcoin(long empresa) throws Exception {
    var info = (Map<?, ?>) rpc("getblockchaininfo", List.of(), bitcoinDaEmpresa(empresa));
    return Map.of(
        "rede",
        Objects.toString(info.get("chain"), ""),
        "sincronizado",
        Boolean.FALSE.equals(info.get("initialblockdownload")),
        "blocos",
        CriptografiaNostr.inteiro(info.get("blocks")));
  }

  private final List<URI> calendarios =
      List.of(
          URI.create("https://alice.btc.calendar.opentimestamps.org"),
          URI.create("https://bob.btc.calendar.opentimestamps.org"));

  public CarimboNostr(
      ConexoesNostr conexoes,
      ObjectMapper json,
      CriptografiaNostr criptografia,
      Environment ambiente) {
    this.conexoes = conexoes;
    this.json = json;
    this.criptografia = criptografia;
    enderecoBitcoin = ambiente.getProperty("APP_BITCOIN_RPC_URL", "");
    usuarioBitcoin = ambiente.getProperty("APP_BITCOIN_RPC_USER", "");
    senhaBitcoin = ambiente.getProperty("APP_BITCOIN_RPC_PASSWORD", "");
    if (!enderecoBitcoin.isBlank()) ConexoesNostr.rpcSeguro(enderecoBitcoin);
  }

  public Map<String, Object> carimbar(Map<String, ?> pedido) throws Exception {
    String evento = Objects.toString(pedido.get("eventoCanonico"), "");
    byte[] arquivo = evento.getBytes(StandardCharsets.UTF_8);
    if (arquivo.length == 0 || arquivo.length > 8192) throw CriptografiaNostr.invalido();
    var esperado = criptografia.montarEvento(pedido);
    criptografia.conferirResposta(
        json.readValue(evento, Map.class),
        esperado,
        Objects.toString(pedido.get("chavePublica"), ""));
    byte[] resumo = CriptografiaNostr.sha256(arquivo);
    ProvaOpenTimestamps prova;
    if (pedido.get("ots") instanceof String anterior) {
      if (anterior.length() > 87400) throw ProvaOpenTimestamps.invalida();
      prova = ProvaOpenTimestamps.ler(Base64.getDecoder().decode(anterior), resumo);
      for (var no : prova.pendencias())
        for (var calendario : calendarios) {
          try {
            // Só consultamos os calendários fixos, nunca o endereço que veio dentro de uma prova
            // recebida.
            byte[] resposta =
                conexoes.buscar(
                    calendario.resolve("/timestamp/" + CriptografiaNostr.hex(no.mensagem)),
                    null,
                    Map.of(),
                    65536);
            no.juntar(ProvaOpenTimestamps.lerResposta(resposta, no.mensagem));
          } catch (InterruptedException falha) {
            throw falha;
          } catch (Exception falha) {
            /* Mantém a prova anterior se um calendário estiver fora do ar. */
          }
        }
    } else {
      prova = new ProvaOpenTimestamps(resumo);
      var ponta = prova.raiz.adicionar(240, criptografia.sortear(32)).adicionar(8, new byte[0]);
      int aceitos = 0;
      for (var calendario : calendarios) {
        try {
          byte[] resposta =
              conexoes.buscar(
                  calendario.resolve("/digest"),
                  ponta.mensagem,
                  Map.of(
                      "Content-Type",
                      "application/x-www-form-urlencoded",
                      "Accept",
                      "application/vnd.opentimestamps.v1",
                      "User-Agent",
                      "aFIRMA"),
                  65536);
          ponta.juntar(ProvaOpenTimestamps.lerResposta(resposta, ponta.mensagem));
          aceitos++;
        } catch (InterruptedException falha) {
          throw falha;
        } catch (Exception falha) {
          /* O outro calendário ainda pode atender ao pedido. */
        }
      }
      if (aceitos == 0)
        throw new IllegalStateException("Os calendários OpenTimestamps estão indisponíveis");
    }
    var resultado = new LinkedHashMap<String, Object>();
    resultado.put("ots", Base64.getEncoder().encodeToString(prova.gravar()));
    resultado.put("estado", "PENDENTE");
    var confirmacoes = prova.confirmacoes();
    if (!confirmacoes.isEmpty()) resultado.put("estado", "AGUARDANDO_NO_BITCOIN");
    var bitcoin =
        conexoesEmpresa == null
            ? bitcoinPadrao()
            : bitcoinDaEmpresa(CriptografiaNostr.inteiro(pedido.get("empresaId")));
    if (bitcoin.endereco.isBlank()) return resultado;
    for (var confirmacao : confirmacoes) {
      try {
        var bloco = conferirBloco(confirmacao, bitcoin);
        if (!bloco.isEmpty()) {
          resultado.putAll(bloco);
          resultado.put("estado", "CONFIRMADA");
          break;
        }
      } catch (InterruptedException falha) {
        throw falha;
      } catch (Exception falha) {
        /* Ter uma atestação não basta: sem o nó conferir, continua pendente. */
      }
    }
    return resultado;
  }

  Map<String, Object> conferirBloco(ProvaOpenTimestamps.Confirmacao atestacao) throws Exception {
    return conferirBloco(atestacao, bitcoinPadrao());
  }

  private Map<String, Object> conferirBloco(
      ProvaOpenTimestamps.Confirmacao atestacao, ConfiguracaoBlockchainEmpresa.Bitcoin bitcoin)
      throws Exception {
    var info = (Map<?, ?>) rpc("getblockchaininfo", List.of(), bitcoin);
    if (!"main".equals(info.get("chain"))
        || !Boolean.FALSE.equals(info.get("initialblockdownload"))
        || CriptografiaNostr.inteiro(info.get("blocks")) - atestacao.altura() + 1 < 6)
      return Map.of();
    String bloco = Objects.toString(rpc("getblockhash", List.of(atestacao.altura()), bitcoin), "");
    if (!CriptografiaNostr.hexadecimal(bloco, 64)) return Map.of();
    byte[] cabecalho =
        CriptografiaNostr.bytes(rpc("getblockheader", List.of(bloco, false), bitcoin).toString());
    if (cabecalho.length != 80 || atestacao.mensagem().length != 32) return Map.of();
    byte[] hash = CriptografiaNostr.sha256(CriptografiaNostr.sha256(cabecalho));
    for (int i = 0; i < 16; i++) {
      byte b = hash[i];
      hash[i] = hash[31 - i];
      hash[31 - i] = b;
    }
    if (!MessageDigest.isEqual(hash, CriptografiaNostr.bytes(bloco))
        || !MessageDigest.isEqual(Arrays.copyOfRange(cabecalho, 36, 68), atestacao.mensagem()))
      return Map.of();
    if (!bloco.equals(rpc("getblockhash", List.of(atestacao.altura()), bitcoin))) return Map.of();
    long quando =
        Integer.toUnsignedLong(
            ByteBuffer.wrap(cabecalho, 68, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    return Map.of("bloco", bloco, "altura", atestacao.altura(), "data", quando);
  }

  Object rpc(String metodo, List<?> parametros) throws Exception {
    return rpc(metodo, parametros, bitcoinPadrao());
  }

  private Object rpc(
      String metodo, List<?> parametros, ConfiguracaoBlockchainEmpresa.Bitcoin bitcoin)
      throws Exception {
    if (bitcoin.endereco.isBlank()) throw new IllegalStateException("Bitcoin Core não configurado");
    var cabecalhos = new HashMap<String, String>();
    cabecalhos.put("Content-Type", "application/json");
    if (!bitcoin.usuario.isEmpty() || !bitcoin.senha.isEmpty())
      cabecalhos.put(
          "Authorization",
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      (bitcoin.usuario + ":" + bitcoin.senha).getBytes(StandardCharsets.UTF_8)));
    byte[] pedido =
        json.writeValueAsBytes(
            Map.of("jsonrpc", "2.0", "id", "firma-ots", "method", metodo, "params", parametros));
    Map<?, ?> resposta =
        json.readValue(
            conexoes.buscar(ConexoesNostr.rpcSeguro(bitcoin.endereco), pedido, cabecalhos, 65536),
            Map.class);
    if (resposta.get("error") != null || !resposta.containsKey("result"))
      throw new IllegalStateException("Bitcoin Core recusou a consulta");
    return resposta.get("result");
  }
}
