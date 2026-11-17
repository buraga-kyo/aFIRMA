package com.afirma.assina;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConfiguracaoBlockchainEmpresa {
  private final JdbcTemplate banco;
  private final ObjectMapper json;
  private final SegredosAssinatura segredos;
  private final CriptografiaNostr criptografia;
  private final String rpcLiberado;

  public ConfiguracaoBlockchainEmpresa(
      JdbcTemplate banco,
      ObjectMapper json,
      SegredosAssinatura segredos,
      CriptografiaNostr criptografia,
      Environment ambiente) {
    this.banco = banco;
    this.json = json;
    this.segredos = segredos;
    this.criptografia = criptografia;
    rpcLiberado = ambiente.getProperty("APP_BITCOIN_RPC_URL", "");
  }

  public Map<String, Object> estado(long empresa) {
    var dados = carregar(empresa);
    var nostr = objeto(dados, "nostr");
    var bitcoin = objeto(dados, "bitcoin");
    var publico = new LinkedHashMap<String, Object>();
    publico.put("cofreDisponivel", segredos.configurado());
    publico.put(
        "nostr",
        nostr.isEmpty()
            ? Map.of()
            : Map.of(
                "referencia",
                nostr.get("referencia"),
                "chavePublica",
                nostr.get("chavePublica"),
                "chaveClientePublica",
                criptografia.chavePublica(
                    CriptografiaNostr.bytes(nostr.get("chaveCliente").toString())),
                "relays",
                nostr.get("relays"),
                "bunkerSalvo",
                true));
    publico.put(
        "bitcoin",
        bitcoin.isEmpty()
            ? Map.of()
            : Map.of(
                "endereco",
                bitcoin.get("endereco"),
                "usuario",
                bitcoin.get("usuario"),
                "senhaSalva",
                !bitcoin.get("senha").toString().isEmpty()));
    return publico;
  }

  @Transactional
  public Map<String, Object> salvarNostr(
      long empresa,
      String ator,
      String referencia,
      String publica,
      String bunker,
      List<String> relays) {
    segredos.exigirConfiguracao();
    travar(empresa);
    var dados = carregar(empresa);
    var anterior = objeto(dados, "nostr");
    if (referencia == null
        || publica == null
        || relays == null
        || relays.size() > 5
        || bunker != null && bunker.length() > 4096)
      throw invalido("Confira a referência, a chave pública e os relays");
    var perfil = new LinkedHashMap<String, Object>();
    perfil.put("empresaId", empresa);
    perfil.put("referencia", referencia.trim());
    perfil.put("chavePublica", publica.trim().toLowerCase(Locale.ROOT));
    // Editar endereço ou relay não deve trocar a credencial já autorizada no assinador.
    perfil.put(
        "chaveCliente",
        anterior.containsKey("chaveCliente")
            ? anterior.get("chaveCliente")
            : CriptografiaNostr.hex(criptografia.criarChaveCliente()));
    perfil.put(
        "bunker",
        bunker == null || bunker.isBlank() ? anterior.getOrDefault("bunker", "") : bunker.trim());
    perfil.put("relays", relays);
    try {
      PerfisNostrConfigurados.ler(Map.of("identidades", List.of(perfil)), criptografia);
      conferirEnderecos(perfil);
    } catch (Exception falha) {
      throw invalido(
          "Informe chave pública hexadecimal, bunker válido e de dois a cinco relays públicos wss");
    }
    dados.put("nostr", perfil);
    gravar(empresa, ator, "NOSTR_SALVO", dados);
    // Alterar a conexão pausa a publicação. O administrador precisa conferir e autorizar de novo.
    banco.update(
        "UPDATE firma_nostr_configuracao SET ativa=false,atualizado_em=now() WHERE empresa_id=?",
        empresa);
    return estado(empresa);
  }

  @Transactional
  public Map<String, Object> salvarBitcoin(
      long empresa, String ator, String endereco, String usuario, String senha, boolean remover) {
    segredos.exigirConfiguracao();
    travar(empresa);
    var dados = carregar(empresa);
    if (remover) dados.remove("bitcoin");
    else {
      if (endereco == null
          || endereco.isBlank()
          || endereco.length() > 2048
          || usuario == null
          || usuario.length() > 256
          || usuario.contains(":")
          || usuario.contains("\n")
          || senha != null && senha.length() > 1024)
        throw invalido("Confira o endereço e as credenciais do Bitcoin Core");
      try {
        conferirRpc(endereco.trim());
      } catch (Exception falha) {
        throw invalido(
            "Use RPC HTTPS público ou o endereço local previamente liberado no servidor");
      }
      var anterior = objeto(dados, "bitcoin");
      // Não encaminha uma senha antiga a outro destino quando o endereço é alterado.
      if (!anterior.isEmpty()
          && !endereco.trim().equals(anterior.get("endereco"))
          && !Objects.toString(anterior.get("senha"), "").isEmpty()
          && (senha == null || senha.isBlank()))
        throw invalido("Ao trocar o endereço RPC, informe novamente a senha");
      String guardada =
          senha == null || senha.isBlank() ? Objects.toString(anterior.get("senha"), "") : senha;
      dados.put(
          "bitcoin",
          Map.of("endereco", endereco.trim(), "usuario", usuario.trim(), "senha", guardada));
    }
    gravar(empresa, ator, remover ? "BITCOIN_REMOVIDO" : "BITCOIN_SALVO", dados);
    return estado(empresa);
  }

  PerfisNostrConfigurados.Perfil perfil(long empresa) {
    var perfil = objeto(carregar(empresa), "nostr");
    if (perfil.isEmpty()) return null;
    conferirEnderecos(perfil);
    return PerfisNostrConfigurados.ler(Map.of("identidades", List.of(perfil)), criptografia)
        .getFirst();
  }

  Bitcoin bitcoin(long empresa) {
    var dados = objeto(carregar(empresa), "bitcoin");
    if (dados.isEmpty()) return null;
    conferirRpc(dados.get("endereco").toString());
    return new Bitcoin(
        dados.get("endereco").toString(),
        dados.get("usuario").toString(),
        dados.get("senha").toString());
  }

  private void conferirEnderecos(Map<String, Object> perfil) {
    for (Object relay : (List<?>) perfil.get("relays"))
      conferirPublico(URI.create(relay.toString()));
    URI bunker = URI.create(perfil.get("bunker").toString());
    for (String par : Objects.toString(bunker.getRawQuery(), "").split("&")) {
      String[] campo = par.split("=", 2);
      if (campo.length == 2 && campo[0].equals("relay"))
        conferirPublico(URI.create(URLDecoder.decode(campo[1], StandardCharsets.UTF_8)));
    }
  }

  private void conferirRpc(String endereco) {
    URI uri = ConexoesNostr.rpcSeguro(endereco);
    // Um administrador de empresa não pode transformar o formulário num acesso à rede interna.
    if (!rpcLiberado.isBlank() && uri.equals(ConexoesNostr.rpcSeguro(rpcLiberado))) return;
    conferirPublico(uri);
  }

  static void conferirPublico(URI uri) {
    try {
      if (uri.getHost() == null) throw new IllegalArgumentException();
      for (var ip : InetAddress.getAllByName(uri.getHost())) {
        byte[] bytes = ip.getAddress();
        if (ip.isAnyLocalAddress()
            || ip.isLoopbackAddress()
            || ip.isSiteLocalAddress()
            || ip.isLinkLocalAddress()
            || ip.isMulticastAddress()
            || bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc
            || bytes.length == 4
                && (bytes[0] & 255) == 100
                && ((bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127))
          throw new IllegalArgumentException();
      }
    } catch (Exception falha) {
      throw invalido("O endereço precisa resolver para um serviço público");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> carregar(long empresa) {
    var linhas =
        banco.queryForList(
            "SELECT chave_id,conteudo FROM firma_blockchain_conexao WHERE empresa_id=?", empresa);
    if (linhas.isEmpty()) return new LinkedHashMap<>();
    try {
      var linha = linhas.getFirst();
      byte[] aberto =
          cifrar(false, empresa, linha.get("chave_id").toString(), (byte[]) linha.get("conteudo"));
      try {
        return new LinkedHashMap<>(json.readValue(aberto, Map.class));
      } finally {
        Arrays.fill(aberto, (byte) 0);
      }
    } catch (Exception falha) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Não foi possível abrir as conexões protegidas desta empresa; confira a chave de"
              + " evidências");
    }
  }

  private void gravar(long empresa, String ator, String acao, Map<String, Object> dados) {
    String chave = segredos.identificadorChave();
    byte[] aberto = json.writeValueAsBytes(dados);
    try {
      byte[] protegido = cifrar(true, empresa, chave, aberto);
      banco.update(
          "INSERT INTO firma_blockchain_conexao(empresa_id,chave_id,conteudo) VALUES(?,?,?) ON"
              + " CONFLICT(empresa_id) DO UPDATE SET"
              + " chave_id=excluded.chave_id,conteudo=excluded.conteudo,atualizado_em=now()",
          empresa,
          chave,
          protegido);
      banco.update(
          "INSERT INTO firma_blockchain_config_auditoria(empresa_id,responsavel,acao)"
              + " VALUES(?,?,?)",
          empresa,
          ator,
          acao);
    } finally {
      Arrays.fill(aberto, (byte) 0);
    }
  }

  byte[] cifrar(boolean gravar, long empresa, String chave, byte[] dados) {
    byte[] segredo =
        CriptografiaNostr.bytes(
            segredos.selarComChave(chave, "cofre-blockchain-v1", "empresa:" + empresa));
    try {
      byte[] nonce = gravar ? criptografia.sortear(12) : Arrays.copyOf(dados, 12);
      var cifra = Cipher.getInstance("AES/GCM/NoPadding");
      cifra.init(
          gravar ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
          new SecretKeySpec(segredo, "AES"),
          new GCMParameterSpec(128, nonce));
      cifra.updateAAD(
          ("afirma.blockchain.conexoes.v1:" + empresa).getBytes(StandardCharsets.UTF_8));
      byte[] resultado =
          cifra.doFinal(gravar ? dados : Arrays.copyOfRange(dados, 12, dados.length));
      return gravar
          ? ByteBuffer.allocate(12 + resultado.length).put(nonce).put(resultado).array()
          : resultado;
    } catch (Exception falha) {
      throw new IllegalStateException("Não foi possível proteger as conexões blockchain");
    } finally {
      Arrays.fill(segredo, (byte) 0);
    }
  }

  private void travar(long empresa) {
    banco.queryForList(
        "SELECT pg_advisory_xact_lock(hashtext('firma.nostr'),hashtext(?))",
        Long.toString(empresa));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objeto(Map<String, Object> dados, String nome) {
    return dados.get(nome) instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : Map.of();
  }

  private static ResponseStatusException invalido(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  static final class Bitcoin {
    final String endereco, usuario, senha;

    Bitcoin(String endereco, String usuario, String senha) {
      this.endereco = endereco;
      this.usuario = usuario;
      this.senha = senha;
    }

    @Override
    public String toString() {
      return "Conexão protegida Bitcoin Core";
    }
  }
}
