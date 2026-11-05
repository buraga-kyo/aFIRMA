package com.afirma.assina;

import java.util.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Liga os arquivos enviados pela tela ao cadastro da empresa e ao armazenamento do chat. */
@Service
public class ArquivosFirma {
  private final ServicoFirma dados;
  private final BancoAssineai banco;
  private final TransactionTemplate transacoes;
  private final EspelhoFirma espelho;
  private final Path pasta;

  public ArquivosFirma(
      ServicoFirma dados,
      BancoAssineai banco,
      TransactionTemplate transacoes,
      EspelhoFirma espelho,
      @Value("${APP_CHAT_ARQUIVOS_DIR:./chat-arquivos}") String pasta) {
    this.dados = dados;
    this.banco = banco;
    this.transacoes = transacoes;
    this.espelho = espelho;
    this.pasta = Path.of(pasta).toAbsolutePath().normalize();
  }

  public Map<String, Object> guardar(MultipartFile arquivo, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    RegrasFirma.exigir(
        arquivo != null && !arquivo.isEmpty() && arquivo.getSize() <= 15 * 1024 * 1024,
        "Arquivo inválido ou maior que 15 MB");
    String nome =
        Objects.toString(arquivo.getOriginalFilename(), "arquivo").replaceAll("[\\r\\n]", "");
    String tipoArquivo = Objects.toString(arquivo.getContentType(), "application/octet-stream");
    RegrasFirma.exigir(
        nome.length() <= 255 && tipoArquivo.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"),
        "Nome ou tipo de arquivo inválido");
    UUID id = UUID.randomUUID();
    Path destino = pasta.resolve(id.toString());
    try {
      Files.createDirectories(pasta);
      Files.setPosixFilePermissions(pasta, PosixFilePermissions.fromString("rwx------"));
      Files.write(destino, arquivo.getBytes(), StandardOpenOption.CREATE_NEW);
      Files.setPosixFilePermissions(destino, PosixFilePermissions.fromString("rw-------"));
      return transacoes.execute(transacao -> {
        Object empresa = espelho.id("Cliente", contexto.cliente());
        if (empresa == null) {
          espelho.salvar(
              "Cliente", dados.registro("Cliente", contexto.cliente(), contexto), contexto);
          empresa = espelho.id("Cliente", contexto.cliente());
        }
        // Ao gravar chat_arquivo, o banco cria o vínculo usado para conferir a empresa no download.
        banco.um("SELECT set_config('firma.relacionamento_direcao','',true)", Map.of());
        banco.executar(
            "INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento) VALUES(:id,:e,:n,:t,:s,:ch)",
            Map.of(
                "id",
                id,
                "e",
                Long.parseLong(empresa.toString()),
                "n",
                nome,
                "t",
                tipoArquivo,
                "s",
                arquivo.getSize(),
                "ch",
                id.toString()));
        var valor =
            banco.um(
                "SELECT destino_id FROM firma_relacionamento_vinculo WHERE origem='chat_arquivo' AND origem_id=:id",
                Map.of("id", id.toString()));
        return dados.registro("Arquivo", ((Number) valor.get("destino_id")).longValue(), contexto);
      });
    } catch (Exception erro) {
      try {
        Files.deleteIfExists(destino);
      } catch (Exception ignorado) {
      }
      if (erro instanceof RuntimeException registroAtual) throw registroAtual;
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível guardar o arquivo", erro);
    }
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> url(
      String endereco, String nome, String tipoArquivo, ServicoFirma.Contexto contexto) {
    dados.autorizar(contexto, "atendimentos", "read");
    ServicoWebhooks.verificarDestino(endereco);
    RegrasFirma.exigir(
        nome != null
            && !nome.isBlank()
            && nome.length() <= 255
            && tipoArquivo != null
            && tipoArquivo.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"),
        "Arquivo inválido");
    Object empresa = espelho.id("Cliente", contexto.cliente());
    if (empresa == null) {
      espelho.salvar("Cliente", dados.registro("Cliente", contexto.cliente(), contexto), contexto);
      empresa = espelho.id("Cliente", contexto.cliente());
    }
    UUID id = UUID.randomUUID();
    banco.um("SELECT set_config('firma.relacionamento_direcao','',true)", Map.of());
    banco.executar(
        "INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento,url) VALUES(:id,:e,:nome,:mime,0,:ch,:url)",
        Map.of(
            "id",
            id,
            "e",
            Long.parseLong(empresa.toString()),
            "nome",
            nome,
            "mime",
            tipoArquivo,
            "ch",
            id.toString(),
            "url",
            endereco));
    var valor =
        banco.um(
            "SELECT destino_id FROM firma_relacionamento_vinculo WHERE origem='chat_arquivo' AND origem_id=:id",
            Map.of("id", id.toString()));
    return dados.registro("Arquivo", ((Number) valor.get("destino_id")).longValue(), contexto);
  }

  public Map<String, Object> base64(
      String base64, String nome, String tipo, ServicoFirma.Contexto contexto) {
    RegrasFirma.exigir(
        base64 != null && base64.length() <= 21 * 1024 * 1024, "Arquivo maior que 15 MB");
    if (base64.startsWith("data:")) base64 = base64.substring(base64.indexOf(',') + 1);
    byte[] bytes = Base64.getDecoder().decode(base64);
    return guardar(new ConteudoArquivo(nome, tipo, bytes), contexto);
  }

  private record ConteudoArquivo(String nome, String tipo, byte[] bytes) implements MultipartFile {
    public String getName() {
      return "arquivo";
    }

    public String getOriginalFilename() {
      return nome;
    }

    public String getContentType() {
      return tipo;
    }

    public boolean isEmpty() {
      return bytes.length == 0;
    }

    public long getSize() {
      return bytes.length;
    }

    public byte[] getBytes() {
      return bytes;
    }

    public java.io.InputStream getInputStream() {
      return new java.io.ByteArrayInputStream(bytes);
    }

    public void transferTo(java.io.File destino) throws java.io.IOException {
      Files.write(destino.toPath(), bytes);
    }
  }

  public ServicoArquivosChat.Arquivo baixar(long id, ServicoFirma.Contexto contexto) {
    var arquivoAtual = dados.registro("Arquivo", id, contexto);
    String chave = Objects.toString(arquivoAtual.get("ArquivoChave"), "");
    if (!chave.matches("[a-fA-F0-9-]{36}") || !Files.isRegularFile(pasta.resolve(chave)))
      return remoto(arquivoAtual);
    try {
      return new ServicoArquivosChat.Arquivo(
          Objects.toString(arquivoAtual.get("ArquivoNome"), "arquivo"),
          Objects.toString(arquivoAtual.get("ArquivoMimeType"), "application/octet-stream"),
          Files.readAllBytes(pasta.resolve(chave)));
    } catch (Exception erro) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo indisponível");
    }
  }

  private ServicoArquivosChat.Arquivo remoto(Map<String, Object> registroAtual) {
    String endereco = Objects.toString(registroAtual.get("ArquivoURL"), "");
    ServicoWebhooks.verificarDestino(endereco);
    try {
      var clienteHttp =
          java.net.http.HttpClient.newBuilder()
              .connectTimeout(java.time.Duration.ofSeconds(5))
              .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
              .build();
      var resposta =
          clienteHttp.send(
              java.net.http.HttpRequest.newBuilder(java.net.URI.create(endereco))
                  .timeout(java.time.Duration.ofSeconds(30))
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofInputStream());
      try (var in = resposta.body()) {
        if (resposta.statusCode() != 200) throw new java.io.IOException();
        byte[] bytes = in.readNBytes(15 * 1024 * 1024 + 1);
        RegrasFirma.exigir(bytes.length <= 15 * 1024 * 1024, "Arquivo maior que 15 MB");
        return new ServicoArquivosChat.Arquivo(
            Objects.toString(registroAtual.get("ArquivoNome"), "arquivo"),
            Objects.toString(registroAtual.get("ArquivoMimeType"), "application/octet-stream"),
            bytes);
      }
    } catch (Exception erro) {
      if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Arquivo remoto indisponível");
    }
  }
}
