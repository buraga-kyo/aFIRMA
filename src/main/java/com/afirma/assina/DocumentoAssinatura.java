package com.afirma.assina;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documento_assinatura")
public class DocumentoAssinatura {
	@Id
	private UUID id;

	@Column(name = "envelope_id", nullable = false)
	private UUID envelopeId;

	@Column(name = "titulo", length = 255)
	private String titulo;

	@Column(name = "buffer", columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] buffer;

	@Column(name = "assinado_buffer", columnDefinition = "bytea")
	@JdbcTypeCode(SqlTypes.VARBINARY)
	private byte[] assinadoBuffer;

	@Column(name = "colecao_de_div_arrastavel", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String colecaoDeDivArrastavel;

	@Column(name = "token", columnDefinition = "text")
	private String token;

	@Column(name = "criptografia_chave_publica", columnDefinition = "text")
	private String criptografiaChavePublica;

	@Column(name = "criptografia_assinatura", columnDefinition = "text")
	private String criptografiaAssinatura;

	@Column(name = "hash_do_pdf_original", columnDefinition = "text")
	private String hashDoPdfOriginal;

	@Column(name = "link_autenticacao", columnDefinition = "text")
	private String linkAutenticacao;

	protected DocumentoAssinatura() { }

	public DocumentoAssinatura(UUID id, UUID envelopeId, String titulo) {
		this.id = id;
		this.envelopeId = envelopeId;
		this.titulo = titulo;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getEnvelopeId() { return envelopeId; }
	public void setEnvelopeId(UUID envelopeId) { this.envelopeId = envelopeId; }
	public String getTitulo() { return titulo; }
	public void setTitulo(String titulo) { this.titulo = titulo; }
	public byte[] getBuffer() { return buffer; }
	public void setBuffer(byte[] buffer) { this.buffer = buffer; }
	public byte[] getAssinadoBuffer() { return assinadoBuffer; }
	public void setAssinadoBuffer(byte[] assinadoBuffer) { this.assinadoBuffer = assinadoBuffer; }
	public String getColecaoDeDivArrastavel() { return colecaoDeDivArrastavel; }
	public void setColecaoDeDivArrastavel(String colecaoDeDivArrastavel) { this.colecaoDeDivArrastavel = colecaoDeDivArrastavel; }
	public String getToken() { return token; }
	public void setToken(String token) { this.token = token; }
	public String getCriptografiaChavePublica() { return criptografiaChavePublica; }
	public void setCriptografiaChavePublica(String criptografiaChavePublica) { this.criptografiaChavePublica = criptografiaChavePublica; }
	public String getCriptografiaAssinatura() { return criptografiaAssinatura; }
	public void setCriptografiaAssinatura(String criptografiaAssinatura) { this.criptografiaAssinatura = criptografiaAssinatura; }
	public String getHashDoPdfOriginal() { return hashDoPdfOriginal; }
	public void setHashDoPdfOriginal(String hashDoPdfOriginal) { this.hashDoPdfOriginal = hashDoPdfOriginal; }
	public String getLinkAutenticacao() { return linkAutenticacao; }
	public void setLinkAutenticacao(String linkAutenticacao) { this.linkAutenticacao = linkAutenticacao; }
}
