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
@Table(name = "signatario_envelope")
public class SignatarioEnvelope {
	@Id
	private UUID id;
	@Column(name = "envelope_id", nullable = false)
	private UUID envelopeId;
	@Column(nullable = false, length = 120)
	private String nome;
	@Column(nullable = false, length = 254)
	private String email;
	@Column(name = "resumo_convite", nullable = false, length = 64)
	private String resumoConvite;
	@Column(columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] assinatura;
	@Column(columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] rubrica;
	@Column(columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] selfie;
	@Column(name = "assinado_em")
	private Instant assinadoEm;
	@Column(name = "endereco_ip", length = 64)
	private String enderecoIp;

	protected SignatarioEnvelope() { }

	public SignatarioEnvelope(UUID id, UUID envelopeId, String nome, String email, String resumoConvite) {
		this.id = id;
		this.envelopeId = envelopeId;
		this.nome = nome;
		this.email = email;
		this.resumoConvite = resumoConvite;
	}

	public UUID getId() { return id; }
	public UUID getEnvelopeId() { return envelopeId; }
	public String getNome() { return nome; }
	public String getEmail() { return email; }
	public String getResumoConvite() { return resumoConvite; }
	public byte[] getAssinatura() { return assinatura; }
	public byte[] getRubrica() { return rubrica; }
	public byte[] getSelfie() { return selfie; }
	public Instant getAssinadoEm() { return assinadoEm; }
	public String getEnderecoIp() { return enderecoIp; }
	public void assinar(byte[] assinatura, byte[] rubrica, byte[] selfie, String enderecoIp, Instant momento) {
		this.assinatura = assinatura;
		this.rubrica = rubrica;
		this.selfie = selfie;
		this.enderecoIp = enderecoIp;
		this.assinadoEm = momento;
	}
	public void limparImagens() {
		this.assinatura = null;
		this.rubrica = null;
		this.selfie = null;
	}
}
