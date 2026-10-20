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
@Table(name = "assinatura_blockchain_pendente")
public class AssinaturaBlockchainPendente {
	@Id
	private UUID identificador;

	@Column(nullable = false, length = 42)
	private String carteira;

	@Column(name = "nome_empresa", nullable = false, length = 120)
	private String nomeEmpresa;

	@Column(name = "resumo_pdf", nullable = false, length = 66)
	private String resumoPdf;

	@Column(name = "pdf_preparado", nullable = false, columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] pdfPreparado;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	protected AssinaturaBlockchainPendente() {
	}

	public AssinaturaBlockchainPendente(UUID identificador, String carteira, String nomeEmpresa, String resumoPdf,
			byte[] pdfPreparado, Instant expiraEm) {
		this.identificador = identificador;
		this.carteira = carteira;
		this.nomeEmpresa = nomeEmpresa;
		this.resumoPdf = resumoPdf;
		this.pdfPreparado = pdfPreparado;
		this.expiraEm = expiraEm;
	}

	public UUID getIdentificador() { return identificador; }
	public String getCarteira() { return carteira; }
	public String getNomeEmpresa() { return nomeEmpresa; }
	public String getResumoPdf() { return resumoPdf; }
	public byte[] getPdfPreparado() { return pdfPreparado; }
	public Instant getExpiraEm() { return expiraEm; }
}
