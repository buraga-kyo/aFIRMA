package com.afirma.assina;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "envelope_assinatura")
public class EnvelopeAssinatura {
	@Id
	private UUID id;
	@Column(name = "nome_empresa", nullable = false, length = 120)
	private String nomeEmpresa;
	@Column(name = "nome_documento", nullable = false, length = 255)
	private String nomeDocumento;
	@Column(nullable = false, length = 255)
	private String criador;
	@Column(name = "pdf_original", nullable = false, columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] pdfOriginal;
	@Column(name = "pdf_atual", columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] pdfAtual;
	@Column(name = "pdf_final", columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] pdfFinal;
	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;
	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;
	@Column(name = "finalizado_em")
	private Instant finalizadoEm;

	protected EnvelopeAssinatura() { }

	public EnvelopeAssinatura(UUID id, String nomeEmpresa, String nomeDocumento, String criador,
			byte[] pdfOriginal, Instant criadoEm, Instant expiraEm) {
		this.id = id;
		this.nomeEmpresa = nomeEmpresa;
		this.nomeDocumento = nomeDocumento;
		this.criador = criador;
		this.pdfOriginal = pdfOriginal;
		this.criadoEm = criadoEm;
		this.expiraEm = expiraEm;
	}

	public UUID getId() { return id; }
	public String getNomeEmpresa() { return nomeEmpresa; }
	public String getNomeDocumento() { return nomeDocumento; }
	public String getCriador() { return criador; }
	public byte[] getPdfOriginal() { return pdfOriginal; }
	public byte[] getPdfAtual() { return pdfAtual == null ? pdfOriginal : pdfAtual; }
	public boolean temPdfAtual() { return pdfAtual != null; }
	public void atualizarPdf(byte[] pdf) { this.pdfAtual = pdf; }
	public byte[] getPdfFinal() { return pdfFinal; }
	public Instant getCriadoEm() { return criadoEm; }
	public Instant getExpiraEm() { return expiraEm; }
	public Instant getFinalizadoEm() { return finalizadoEm; }
	public void finalizar(byte[] pdf, Instant momento) { this.pdfFinal = pdf; this.finalizadoEm = momento; }
}
