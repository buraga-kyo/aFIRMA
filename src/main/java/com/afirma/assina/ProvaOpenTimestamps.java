package com.afirma.assina;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

/** Leitura do formato OTS; as operações criptográficas ficam no JDK e no Bouncy Castle. */
final class ProvaOpenTimestamps {
  static final byte[] CABECALHO =
      CriptografiaNostr.bytes("004f70656e54696d657374616d7073000050726f6f6600bf89e2e884e89294");
  static final byte[] PENDENTE = CriptografiaNostr.bytes("83dfe30d2ef90c8e");
  static final byte[] BITCOIN = CriptografiaNostr.bytes("0588960d73d71901");
  static final int LIMITE = 65536;
  final No raiz;

  ProvaOpenTimestamps(byte[] resumo) {
    if (resumo.length != 32) throw invalida();
    raiz = new No(resumo);
  }

  private ProvaOpenTimestamps(No raiz) {
    this.raiz = raiz;
  }

  static ProvaOpenTimestamps ler(byte[] dados, byte[] esperado) {
    var leitura = new Leitura(dados);
    if (!Arrays.equals(leitura.bytes(CABECALHO.length), CABECALHO)
        || leitura.numero() != 1
        || leitura.um() != 8) throw invalida();
    byte[] resumo = leitura.bytes(32);
    if (!MessageDigest.isEqual(resumo, esperado))
      throw new IllegalArgumentException("A prova OTS pertence a outro evento");
    var prova = new ProvaOpenTimestamps(leitura.no(resumo, 0));
    leitura.fim();
    return prova;
  }

  static No lerResposta(byte[] dados, byte[] resumo) {
    var leitura = new Leitura(dados);
    var no = leitura.no(resumo, 0);
    leitura.fim();
    return no;
  }

  byte[] gravar() {
    var escrita = new Escrita();
    escrita.bytes(CABECALHO);
    escrita.numero(1);
    escrita.um(8);
    escrita.bytes(raiz.mensagem);
    escrita.no(raiz, 0);
    return escrita.saida.toByteArray();
  }

  List<No> pendencias() {
    var encontrados = new ArrayList<No>();
    visitar(
        raiz,
        no -> {
          if (no.atestados.stream().anyMatch(a -> Arrays.equals(a.tipo, PENDENTE)))
            encontrados.add(no);
        });
    return encontrados.stream().limit(8).toList();
  }

  List<Confirmacao> confirmacoes() {
    var encontrados = new ArrayList<Confirmacao>();
    visitar(
        raiz,
        no -> {
          for (var a : no.atestados)
            if (Arrays.equals(a.tipo, BITCOIN)) {
              var leitura = new Leitura(a.conteudo);
              long altura = leitura.numero();
              leitura.fim();
              encontrados.add(new Confirmacao(no.mensagem, altura));
            }
        });
    return encontrados.stream()
        .sorted(Comparator.comparingLong(Confirmacao::altura))
        .limit(8)
        .toList();
  }

  private void visitar(No no, java.util.function.Consumer<No> acao) {
    acao.accept(no);
    for (var caminho : no.caminhos) visitar(caminho.destino, acao);
  }

  static final class No {
    final byte[] mensagem;
    final List<Atestado> atestados = new ArrayList<>();
    final List<Caminho> caminhos = new ArrayList<>();

    No(byte[] mensagem) {
      if (mensagem.length > 4096) throw invalida();
      this.mensagem = mensagem.clone();
    }

    No adicionar(int tipo, byte[] argumento) {
      for (var caminho : caminhos)
        if (caminho.tipo == tipo && Arrays.equals(caminho.argumento, argumento))
          return caminho.destino;
      var filho = new No(aplicar(tipo, argumento, mensagem));
      caminhos.add(new Caminho(tipo, argumento.clone(), filho));
      return filho;
    }

    void juntar(No outro) {
      juntar(outro, 0);
    }

    private void juntar(No outro, int profundidade) {
      if (profundidade > 128 || !MessageDigest.isEqual(mensagem, outro.mensagem)) throw invalida();
      for (var a : outro.atestados)
        if (atestados.stream()
            .noneMatch(b -> Arrays.equals(a.tipo, b.tipo) && Arrays.equals(a.conteudo, b.conteudo)))
          atestados.add(a);
      for (var caminho : outro.caminhos)
        adicionar(caminho.tipo, caminho.argumento).juntar(caminho.destino, profundidade + 1);
    }
  }

  record Atestado(byte[] tipo, byte[] conteudo) {}

  record Caminho(int tipo, byte[] argumento, No destino) {}

  record Confirmacao(byte[] mensagem, long altura) {}

  static byte[] aplicar(int tipo, byte[] argumento, byte[] mensagem) {
    if (mensagem.length > 4096 || argumento.length > 4096) throw invalida();
    byte[] resultado;
    try {
      resultado =
          switch (tipo) {
            case 0x02 -> MessageDigest.getInstance("SHA-1").digest(mensagem);
            case 0x08 -> CriptografiaNostr.sha256(mensagem);
            case 0x03 -> {
              var hash = new RIPEMD160Digest();
              hash.update(mensagem, 0, mensagem.length);
              byte[] fim = new byte[20];
              hash.doFinal(fim, 0);
              yield fim;
            }
            case 0x67 -> {
              var hash = new KeccakDigest(256);
              hash.update(mensagem, 0, mensagem.length);
              byte[] fim = new byte[32];
              hash.doFinal(fim, 0);
              yield fim;
            }
            case 0xf0 -> CriptografiaNostr.juntar(mensagem, argumento);
            case 0xf1 -> CriptografiaNostr.juntar(argumento, mensagem);
            case 0xf2 -> {
              byte[] fim = mensagem.clone();
              for (int i = 0; i < fim.length / 2; i++) {
                byte aux = fim[i];
                fim[i] = fim[fim.length - i - 1];
                fim[fim.length - i - 1] = aux;
              }
              yield fim;
            }
            case 0xf3 -> CriptografiaNostr.hex(mensagem).getBytes(StandardCharsets.US_ASCII);
            default -> throw invalida();
          };
    } catch (java.security.NoSuchAlgorithmException falha) {
      throw new IllegalStateException("Hash OTS indisponível");
    }
    if (resultado.length > 4096) throw invalida();
    return resultado;
  }

  // O limite vale também para respostas dos calendários. Um calendário não ganha acesso ilimitado à
  // pilha ou memória.
  private static final class Leitura {
    final byte[] dados;
    int posicao, nos;

    Leitura(byte[] dados) {
      if (dados.length == 0 || dados.length > LIMITE) throw invalida();
      this.dados = dados;
    }

    int um() {
      return bytes(1)[0] & 255;
    }

    byte[] bytes(int tamanho) {
      if (tamanho < 0 || tamanho > dados.length - posicao) throw invalida();
      byte[] parte = Arrays.copyOfRange(dados, posicao, posicao + tamanho);
      posicao += tamanho;
      return parte;
    }

    long numero() {
      long numero = 0;
      for (int volta = 0; volta < 8; volta++) {
        int b = um();
        numero |= (long) (b & 127) << (7 * volta);
        if ((b & 128) == 0) {
          if (numero > 9007199254740991L) throw invalida();
          return numero;
        }
      }
      throw invalida();
    }

    byte[] pacote(int limite) {
      long tamanho = numero();
      if (tamanho > limite) throw invalida();
      return bytes((int) tamanho);
    }

    No no(byte[] mensagem, int profundidade) {
      if (profundidade > 128 || ++nos > 4096) throw invalida();
      var no = new No(mensagem);
      int marca = um();
      while (marca == 255) {
        parte(no, um(), profundidade);
        marca = um();
      }
      parte(no, marca, profundidade);
      return no;
    }

    void parte(No no, int marca, int profundidade) {
      if (marca == 0) {
        byte[] tipo = bytes(8), conteudo = pacote(8192);
        if (Arrays.equals(tipo, BITCOIN)) {
          var altura = new Leitura(conteudo);
          altura.numero();
          altura.fim();
        }
        if (Arrays.equals(tipo, PENDENTE)) {
          var uri = new Leitura(conteudo);
          byte[] texto = uri.pacote(1000);
          uri.fim();
          for (byte b : texto) if (b < 33 || b > 126) throw invalida();
        }
        no.atestados.add(new Atestado(tipo, conteudo));
        if (no.atestados.size() > 1024) throw invalida();
      } else {
        byte[] argumento = (marca == 240 || marca == 241) ? pacote(4096) : new byte[0];
        if ((marca == 240 || marca == 241) && argumento.length == 0) throw invalida();
        var filho = no(aplicar(marca, argumento, no.mensagem), profundidade + 1);
        // Ramos repetidos não podem sobrescrever um caminho já conferido.
        no.adicionar(marca, argumento).juntar(filho);
      }
    }

    void fim() {
      if (posicao != dados.length) throw invalida();
    }
  }

  private static final class Escrita {
    final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    int nos;

    void um(int b) {
      bytes(new byte[] {(byte) b});
    }

    void bytes(byte[] bytes) {
      if (bytes.length > LIMITE - saida.size()) throw invalida();
      saida.writeBytes(bytes);
    }

    void numero(long numero) {
      if (numero < 0) throw invalida();
      do {
        int parte = (int) (numero & 127);
        numero >>>= 7;
        um(numero == 0 ? parte : parte | 128);
      } while (numero != 0);
    }

    void pacote(byte[] bytes) {
      numero(bytes.length);
      bytes(bytes);
    }

    void no(No no, int profundidade) {
      if (profundidade > 128 || ++nos > 4096) throw invalida();
      var atestados = new ArrayList<>(no.atestados);
      atestados.sort((a, b) -> {
        int tipo = Arrays.compareUnsigned(a.tipo, b.tipo);
        return tipo == 0 ? Arrays.compareUnsigned(a.conteudo, b.conteudo) : tipo;
      });
      var caminhos = new ArrayList<>(no.caminhos);
      caminhos.sort((a, b) -> {
        int tipo = Integer.compare(a.tipo, b.tipo);
        return tipo == 0 ? Arrays.compareUnsigned(a.argumento, b.argumento) : tipo;
      });
      int faltam = atestados.size() + caminhos.size();
      if (faltam == 0) throw invalida();
      for (var a : atestados) {
        if (--faltam > 0) um(255);
        um(0);
        bytes(a.tipo);
        pacote(a.conteudo);
      }
      for (var c : caminhos) {
        if (--faltam > 0) um(255);
        um(c.tipo);
        if (c.tipo == 240 || c.tipo == 241) pacote(c.argumento);
        no(c.destino, profundidade + 1);
      }
    }
  }

  static IllegalArgumentException invalida() {
    return new IllegalArgumentException("Formato da prova OpenTimestamps inválido ou excessivo");
  }
}
