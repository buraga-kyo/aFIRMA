package com.afirma.assina;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "envelope_assinatura")
public class EnvelopeAssinatura {

  @Column(name = "configuracao_registro_assinatura", columnDefinition = "text")
  private String configuracaoRegistroAssinatura;
  @Column(name = "empresa_assinou_em")
  private Instant empresaAssinouEm;
  @Column(name = "empresa_titular_certificado", columnDefinition = "text")
  private String empresaTitularCertificado;
  @Column(name = "empresa_serie_certificado", length = 255)
  private String empresaSerieCertificado;
  public String getConfiguracaoRegistroAssinatura() { return configuracaoRegistroAssinatura; }
  public void configurarRegistroAssinatura(String valor) { configuracaoRegistroAssinatura = valor; }
  public Instant getEmpresaAssinouEm() { return empresaAssinouEm; }
  public String getEmpresaTitularCertificado() { return empresaTitularCertificado; }
  public void registrarAssinaturaEmpresa(Instant momento, String titular, String serie) {
    empresaAssinouEm = momento; empresaTitularCertificado = titular; empresaSerieCertificado = serie;
  }

  @Id
  private UUID id;

  @Column(name = "nome_empresa", nullable = false, length = 120)
  private String nomeEmpresa;

  @Column(name = "nome_documento", nullable = false, length = 255)
  private String nomeDocumento;

  @Column(nullable = false, length = 255)
  private String criador;

  @Column(name = "pdf_original", columnDefinition = "bytea")
  @JdbcTypeCode(SqlTypes.VARBINARY)
  private byte[] pdfOriginal;

  @Column(name = "pdf_atual", columnDefinition = "bytea")
  @JdbcTypeCode(SqlTypes.VARBINARY)
  private byte[] pdfAtual;

  @Column(name = "pdf_final", columnDefinition = "bytea")
  @JdbcTypeCode(SqlTypes.VARBINARY)
  private byte[] pdfFinal;

  @Column(name = "pdf_original_chave_minio", length = 255)
  private String pdfOriginalChaveMinio;

  @Column(name = "pdf_atual_chave_minio", length = 255)
  private String pdfAtualChaveMinio;

  @Column(name = "pdf_final_chave_minio", length = 255)
  private String pdfFinalChaveMinio;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "expira_em", nullable = false)
  private Instant expiraEm;

  @Column(name = "finalizado_em")
  private Instant finalizadoEm;

  @Column(name = "excluido_em")
  private Instant excluidoEm;

  // Legacy fields mapped from Assinatura.js
  @Column(name = "nome", length = 255)
  private String nome;

  @Column(name = "responsavel", length = 255)
  private String responsavel;

  @Column(name = "status", length = 255)
  private String status;

  @Column(name = "quantidade_documento_assinado", length = 255)
  private String quantidadeDocumentoAssinado;
  @Column(name = "tipo_documento", length = 40)
  private String tipoDocumento;

  @Column(name = "declaracao_formalidades", columnDefinition = "text")
  private String declaracaoFormalidades;

  @Column(name = "reter_evidencias_ate")
  private Instant reterEvidenciasAte;

  @Column(name = "cancelado_em")
  private Instant canceladoEm;

  @Column(name = "motivo_cancelamento", length = 500)
  private String motivoCancelamento;

  public String getTipoDocumento() {
    return tipoDocumento;
  }

  public String getDeclaracaoFormalidades() {
    return declaracaoFormalidades;
  }

  public Instant getReterEvidenciasAte() {
    return reterEvidenciasAte;
  }

  public Instant getCanceladoEm() {
    return canceladoEm;
  }

  public String getMotivoCancelamento() {
    return motivoCancelamento;
  }

  public void definirFormalidades(
    String tipo,
    String declaracao,
    Instant preservarAte
  ) {
    tipoDocumento = tipo;
    declaracaoFormalidades = declaracao;
    reterEvidenciasAte = preservarAte;
  }

  public void cancelar(String motivo, Instant momento) {
    motivoCancelamento = motivo;
    canceladoEm = momento;
  }

  public void estenderPreservacao(Instant limite) {
    if (
      reterEvidenciasAte == null || limite.isAfter(reterEvidenciasAte)
    ) reterEvidenciasAte = limite;
  }

  protected EnvelopeAssinatura() {}

  public EnvelopeAssinatura(
    UUID id,
    String nomeEmpresa,
    String nomeDocumento,
    String criador,
    byte[] pdfOriginal,
    Instant criadoEm,
    Instant expiraEm
  ) {
    this.id = id;
    this.nomeEmpresa = nomeEmpresa;
    this.nomeDocumento = nomeDocumento;
    this.criador = criador;
    this.nome = nomeDocumento;
    this.responsavel = criador;
    this.status = "Pendente";
    this.quantidadeDocumentoAssinado = "0";
    this.pdfOriginal = pdfOriginal;
    this.criadoEm = criadoEm;
    this.expiraEm = expiraEm;
  }

  public UUID getId() {
    return id;
  }

  public String getNomeEmpresa() {
    return nomeEmpresa;
  }

  public String getNomeDocumento() {
    return nomeDocumento;
  }

  public void renomearDocumento(String titulo) {
    this.nomeDocumento = titulo;
    this.nome = titulo;
  }

  public String getCriador() {
    return criador;
  }

  public byte[] getPdfOriginal() {
    return pdfOriginal;
  }

  public String getPdfOriginalChaveMinio() {
    return pdfOriginalChaveMinio;
  }

  public String getPdfAtualChaveMinio() {
    return pdfAtualChaveMinio;
  }

  public String getPdfFinalChaveMinio() {
    return pdfFinalChaveMinio;
  }

  public void guardarOriginalNoMinio(String chave) {
    pdfOriginalChaveMinio = chave;
    pdfOriginal = null;
  }

  public void guardarAtualNoMinio(String chave) {
    pdfAtualChaveMinio = chave;
    pdfAtual = null;
  }

  public void guardarFinalNoMinio(String chave) {
    pdfFinalChaveMinio = chave;
    pdfFinal = null;
  }

  public byte[] getPdfAtual() {
    return pdfAtual == null ? pdfOriginal : pdfAtual;
  }

  public boolean temPdfAtual() {
    return pdfAtual != null || pdfAtualChaveMinio != null;
  }

  public void atualizarPdf(byte[] pdf) {
    this.pdfAtual = pdf;
  }

  public byte[] getPdfFinal() {
    return pdfFinal;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getExpiraEm() {
    return expiraEm;
  }

  public Instant getFinalizadoEm() {
    return finalizadoEm;
  }

  public Instant getExcluidoEm() {
    return excluidoEm;
  }

  public void excluir(Instant momento) {
    this.excluidoEm = momento;
  }

  public void finalizar(byte[] pdf, Instant momento) {
    this.pdfFinal = pdf;
    this.finalizadoEm = momento;
    this.status = "Assinado";
  }

  // Getters & Setters for legacy fields
  public String getNome() {
    return nome;
  }

  public void setNome(String nome) {
    this.nome = nome;
  }

  public String getResponsavel() {
    return responsavel;
  }

  public void setResponsavel(String responsavel) {
    this.responsavel = responsavel;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getQuantidadeDocumentoAssinado() {
    return quantidadeDocumentoAssinado;
  }

  public void setQuantidadeDocumentoAssinado(
    String quantidadeDocumentoAssinado
  ) {
    this.quantidadeDocumentoAssinado = quantidadeDocumentoAssinado;
  }
}
