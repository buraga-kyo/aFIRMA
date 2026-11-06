package com.afirma.assina;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "campo_assinatura")
public class CampoAssinatura {

  public enum Tipo {
    ASSINATURA,
    RUBRICA,
    SELFIE,
    TEXTO,
    DATA,
    SELECAO,
    NUMERO,
    ANEXO,
  }

  @Id
  private UUID id;

  @Column(name = "signatario_id", nullable = false)
  private UUID signatarioId;

  @Column(name = "envelope_id", nullable = false)
  private UUID envelopeId;

  @Column(name = "documento_id")
  private UUID documentoId;

  @Column(nullable = false, length = 20)
  private String tipo;

  @Column(nullable = false)
  private int pagina;

  @Column(name = "posicao_x", nullable = false)
  private double posicaoX;

  @Column(name = "posicao_y", nullable = false)
  private double posicaoY;

  @Column(nullable = false)
  private double largura;

  @Column(nullable = false)
  private double altura;

  @Column(nullable = false, length = 80)
  private String nome = "";

  @Column(nullable = false)
  private boolean obrigatorio = true;

  @Column(length = 2000)
  private String valor;

  @Column(columnDefinition = "bytea")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
  private byte[] anexo;

  @Column(name = "anexo_nome", length = 120)
  private String anexoNome;

  @Column(name = "anexo_tipo", length = 80)
  private String anexoTipo;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "anexo_sha256", columnDefinition = "char(64)", length = 64)
  private String anexoSha256;

  public byte[] getAnexo() {
    return anexo;
  }

  public String getAnexoNome() {
    return anexoNome;
  }

  public String getAnexoTipo() {
    return anexoTipo;
  }

  public void anexar(byte[] bytes, String nome, String tipo, String hash) {
    anexo = bytes;
    anexoNome = nome;
    anexoTipo = tipo;
    anexoSha256 = hash;
    valor = nome + " · SHA-256 " + hash;
  }

  public String getNome() {
    return nome;
  }

  public boolean isObrigatorio() {
    return obrigatorio;
  }

  public String getValor() {
    return valor;
  }

  public void configurar(String nome, boolean obrigatorio) {
    this.nome = nome == null ? "" : nome;
    this.obrigatorio = obrigatorio;
  }

  public void preencher(String valor) {
    this.valor = valor;
  }

  public boolean preenchivel() {
    return (
      getTipo() == Tipo.TEXTO ||
      getTipo() == Tipo.DATA ||
      getTipo() == Tipo.SELECAO ||
      getTipo() == Tipo.NUMERO ||
      getTipo() == Tipo.ANEXO
    );
  }

  protected CampoAssinatura() {}

  public CampoAssinatura(
    UUID id,
    UUID signatarioId,
    Tipo tipo,
    int pagina,
    double posicaoX,
    double posicaoY,
    double largura,
    double altura
  ) {
    this.id = id;
    this.signatarioId = signatarioId;
    this.tipo = tipo.name();
    this.pagina = pagina;
    this.posicaoX = posicaoX;
    this.posicaoY = posicaoY;
    this.largura = largura;
    this.altura = altura;
  }

  public void vincularDocumento(DocumentoAssinatura documento) {
    if (
      pagina < documento.getPaginaInicio() || pagina > documento.getPaginaFim()
    ) throw new IllegalArgumentException("Campo fora das páginas do documento");
    envelopeId = documento.getEnvelopeId();
    documentoId = documento.getId();
  }

  public UUID getEnvelopeId() {
    return envelopeId;
  }

  public UUID getDocumentoId() {
    return documentoId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSignatarioId() {
    return signatarioId;
  }

  public Tipo getTipo() {
    return Tipo.valueOf(tipo);
  }

  public int getPagina() {
    return pagina;
  }

  public double getPosicaoX() {
    return posicaoX;
  }

  public double getPosicaoY() {
    return posicaoY;
  }

  public double getLargura() {
    return largura;
  }

  public double getAltura() {
    return altura;
  }
}
