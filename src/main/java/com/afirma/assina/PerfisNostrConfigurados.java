package com.afirma.assina;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PerfisNostrConfigurados {
  private final List<Perfil> perfis;
  private ConfiguracaoBlockchainEmpresa conexoesEmpresa;

  @org.springframework.beans.factory.annotation.Autowired
  void usarConexoes(ConfiguracaoBlockchainEmpresa conexoes) {
    this.conexoesEmpresa = conexoes;
  }

  boolean configurado(long empresa) {
    return !listar(empresa).isEmpty();
  }

  public PerfisNostrConfigurados(
      ObjectMapper json, Environment ambiente, CriptografiaNostr criptografia) {
    String arquivo = ambiente.getProperty("APP_NOSTR_CONFIG", "");
    if (arquivo.isBlank()) {
      perfis = List.of();
      return;
    }
    try {
      Path caminho = Path.of(arquivo);
      if (!caminho.isAbsolute() || Files.size(caminho) > 65536)
        throw new IllegalArgumentException();
      conferirPermissao(caminho);
      perfis = ler(json.readValue(Files.readString(caminho), Map.class), criptografia);
    } catch (Exception falha) {
      throw new IllegalStateException(
          "Confira o arquivo privado APP_NOSTR_CONFIG, suas permissões e os campos das"
              + " identidades");
    }
  }

  static void conferirPermissao(Path arquivo) throws Exception {
    var proibidas =
        EnumSet.of(
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE);
    if (!Collections.disjoint(Files.getPosixFilePermissions(arquivo), proibidas))
      throw new IllegalArgumentException("Use permissão 600 no arquivo Nostr");
  }

  @SuppressWarnings("unchecked")
  static List<Perfil> ler(Map<?, ?> config, CriptografiaNostr criptografia) {
    if (!(config.get("identidades") instanceof List<?> lista) || lista.size() > 1000)
      throw CriptografiaNostr.invalido();
    var perfis = new ArrayList<Perfil>();
    var referencias = new HashSet<String>();
    var clientes = new HashSet<String>();
    for (Object item : lista) {
      var dados = (Map<String, Object>) item;
      long empresa = CriptografiaNostr.inteiro(dados.get("empresaId"));
      String referencia = Objects.toString(dados.get("referencia"), "");
      if (empresa < 1
          || !referencia.matches("[a-zA-Z0-9_-]{1,80}")
          || referencia.startsWith("nsec")
          || referencia.matches("[0-9a-fA-F]{64}")
          || !referencias.add(referencia)
          || !CriptografiaNostr.hexadecimal(dados.get("chavePublica"), 64)
          || !CriptografiaNostr.hexadecimal(dados.get("chaveCliente"), 64))
        throw CriptografiaNostr.invalido();
      String publica = dados.get("chavePublica").toString();
      byte[] cliente = CriptografiaNostr.bytes(dados.get("chaveCliente").toString());
      String publicaCliente = criptografia.chavePublica(cliente);
      if (publicaCliente.equals(publica) || !clientes.add(publicaCliente))
        throw new IllegalArgumentException(
            "O cliente de automação precisa de uma chave própria e exclusiva");
      URI bunker = URI.create(dados.get("bunker").toString());
      if (!"bunker".equals(bunker.getScheme())
          || !CriptografiaNostr.hexadecimal(bunker.getHost(), 64)
          || bunker.getUserInfo() != null
          || bunker.getFragment() != null
          || bunker.getPort() != -1
          || bunker.getPath() != null && !bunker.getPath().isEmpty())
        throw CriptografiaNostr.invalido();
      var canais = new LinkedHashSet<URI>();
      String segredo = "";
      for (String par : Objects.toString(bunker.getRawQuery(), "").split("&")) {
        String[] campo = par.split("=", 2);
        if (campo.length != 2) continue;
        String valor = URLDecoder.decode(campo[1], StandardCharsets.UTF_8);
        if (campo[0].equals("relay")) canais.add(relaySeguro(valor));
        if (campo[0].equals("secret")) segredo = valor;
      }
      if (canais.isEmpty() || canais.size() > 3 || segredo.length() > 1024)
        throw CriptografiaNostr.invalido();
      if (!(dados.get("relays") instanceof List<?> enderecos)) throw CriptografiaNostr.invalido();
      var relays = new LinkedHashSet<URI>();
      for (Object endereco : enderecos) relays.add(relaySeguro(endereco.toString()));
      if (relays.size() < 2 || relays.size() > 5)
        throw new IllegalArgumentException("Configure de dois a cinco relays públicos");
      perfis.add(
          new Perfil(
              empresa,
              referencia,
              publica,
              cliente,
              bunker.getHost(),
              segredo,
              List.copyOf(canais),
              List.copyOf(relays)));
    }
    return List.copyOf(perfis);
  }

  static URI relaySeguro(String endereco) {
    URI uri = URI.create(endereco).normalize();
    if (!"wss".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException("O relay precisa usar wss sem credenciais na URL");
    String caminho = uri.getRawPath();
    String texto = uri.toASCIIString();
    if (caminho == null || caminho.isEmpty()) {
      int consulta = texto.indexOf('?');
      texto =
          consulta < 0
              ? texto + "/"
              : texto.substring(0, consulta) + "/" + texto.substring(consulta);
    }
    return URI.create(texto);
  }

  boolean configurado() {
    return !perfis.isEmpty();
  }

  List<Map<String, Object>> listar(long empresa) {
    var cadastrado = conexoesEmpresa == null ? null : conexoesEmpresa.perfil(empresa);
    var disponiveis = new ArrayList<Perfil>();
    if (cadastrado != null) disponiveis.add(cadastrado);
    perfis.stream()
        .filter(p ->
            p.empresa == empresa
                && (cadastrado == null || !p.referencia.equals(cadastrado.referencia)))
        .forEach(disponiveis::add);
    return disponiveis.stream()
        .map(p ->
            Map.<String, Object>of(
                "referencia",
                p.referencia,
                "chavePublica",
                p.chavePublica,
                "relays",
                p.relays.stream().map(URI::toString).toList()))
        .toList();
  }

  Perfil buscar(long empresa, String referencia) {
    var cadastrado = conexoesEmpresa == null ? null : conexoesEmpresa.perfil(empresa);
    if (cadastrado != null && cadastrado.referencia.equals(referencia)) return cadastrado;
    return perfis.stream()
        .filter(p -> p.empresa == empresa && p.referencia.equals(referencia))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Assinador não cadastrado para esta empresa"));
  }

  // Não é um record: o toString automático acabaria incluindo a credencial de automação.
  static final class Perfil {
    final long empresa;
    final String referencia, chavePublica, publicaAssinador, segredoPareamento;
    final List<URI> canais, relays;
    private final byte[] chaveCliente;

    Perfil(
        long empresa,
        String referencia,
        String publica,
        byte[] cliente,
        String assinador,
        String segredo,
        List<URI> canais,
        List<URI> relays) {
      this.empresa = empresa;
      this.referencia = referencia;
      chavePublica = publica;
      chaveCliente = cliente.clone();
      publicaAssinador = assinador;
      segredoPareamento = segredo;
      this.canais = canais;
      this.relays = relays;
    }

    byte[] chaveCliente() {
      return chaveCliente.clone();
    }

    @Override
    public String toString() {
      return "Perfil Nostr da empresa " + empresa + ", referência " + referencia;
    }
  }
}
