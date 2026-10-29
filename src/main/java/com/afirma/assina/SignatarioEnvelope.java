package com.afirma.assina;

import java.time.Instant;
import java.util.List;
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

	// Legacy fields mapped from Signatarios.js
	@Column(name = "forma_de_autenticacao", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> formaDeAutenticacao;

	@Column(name = "forma_de_notificacao", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> formaDeNotificacao;

	@Column(name = "assinou")
	private Boolean assinou;

	@Column(name = "rg", length = 255)
	private String rg;

	@Column(name = "cpf", length = 255)
	private String cpf;

	@Column(name = "whatsapp", length = 255)
	private String whatsapp;

	@Column(name = "token_email", length = 255)
	private String tokenEmail;

	@Column(name = "token_whatsapp", length = 255)
	private String tokenWhatsapp;

	@Column(name = "token_link_assinatura", columnDefinition = "text")
	private String tokenLinkAssinatura;

	@Column(name = "link_assinatura", columnDefinition = "text")
	private String linkAssinatura;

	@Column(name = "qualificacao", length = 255)
	private String qualificacao;

	@Column(name = "situacao_assinatura", length = 255)
	private String situacaoAssinatura;

	@Column(name = "data_assinatura", length = 255)
	private String dataAssinatura;

	@Column(name = "quantidade_de_acessos_no_link_de_assinatura", length = 255)
	private String quantidadeDeAcessosNoLinkDeAssinatura;

	@Column(name = "mensagem_sobre_visualizacao_do_link_de_assinatura", length = 255)
	private String mensagemSobreVisualizacaoDoLinkDeAssinatura;

	@Column(name = "dispositivo", columnDefinition = "text")
	private String dispositivo;

	@Column(name = "geolocalizacao", columnDefinition = "text")
	private String geolocalizacao;

	@Column(name = "autenticado_via", length = 255)
	private String autenticadoVia;

	@Column(name = "selfie_base64", columnDefinition = "text")
	private String selfieBase64;

	@Column(name = "assinatura_escrita_base64", columnDefinition = "text")
	private String assinaturaEscritaBase64;

	protected SignatarioEnvelope() { }

	public SignatarioEnvelope(UUID id, UUID envelopeId, String nome, String email, String resumoConvite) {
		this.id = id;
		this.envelopeId = envelopeId;
		this.nome = nome;
		this.email = email;
		this.resumoConvite = resumoConvite;
        this.assinou = false; this.situacaoAssinatura = "Não abriu o link";
        this.quantidadeDeAcessosNoLinkDeAssinatura = "0";
        this.mensagemSobreVisualizacaoDoLinkDeAssinatura = "Não abriu o link de assinatura";
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
        this.assinou = true; this.situacaoAssinatura = "Assinado"; this.dataAssinatura = momento.toString();
	}
    public void registrarAcesso(String dispositivo) {
        java.math.BigInteger acessos;
        try { acessos = new java.math.BigInteger(quantidadeDeAcessosNoLinkDeAssinatura == null ? "0" : quantidadeDeAcessosNoLinkDeAssinatura); }
        catch (NumberFormatException invalido) { acessos = java.math.BigInteger.ZERO; }
        acessos = acessos.max(java.math.BigInteger.ZERO).add(java.math.BigInteger.ONE);
        quantidadeDeAcessosNoLinkDeAssinatura = acessos.toString();
        mensagemSobreVisualizacaoDoLinkDeAssinatura = "Acessou " + acessos + (acessos.equals(java.math.BigInteger.ONE) ? " vez" : " vezes") + " o link de assinatura";
        if (assinadoEm == null) {
            situacaoAssinatura = "Visualizou o documento";
            this.dispositivo = dispositivo == null ? null : dispositivo.substring(0, Math.min(1024, dispositivo.length()));
        }
    }
	public void limparImagens() {
		this.assinatura = null;
		this.rubrica = null;
		this.selfie = null;
	}

	// Legacy Getters & Setters
	public List<String> getFormaDeAutenticacao() { return formaDeAutenticacao; }
	public void setFormaDeAutenticacao(List<String> formaDeAutenticacao) { this.formaDeAutenticacao = formaDeAutenticacao; }
	public List<String> getFormaDeNotificacao() { return formaDeNotificacao; }
	public void setFormaDeNotificacao(List<String> formaDeNotificacao) { this.formaDeNotificacao = formaDeNotificacao; }
	public Boolean getAssinou() { return assinou; }
	public void setAssinou(Boolean assinou) { this.assinou = assinou; }
	public String getRg() { return rg; }
	public void setRg(String rg) { this.rg = rg; }
	public String getCpf() { return cpf; }
	public void setCpf(String cpf) { this.cpf = cpf; }
	public String getWhatsapp() { return whatsapp; }
	public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
	public String getTokenEmail() { return tokenEmail; }
	public void setTokenEmail(String tokenEmail) { this.tokenEmail = tokenEmail; }
	public String getTokenWhatsapp() { return tokenWhatsapp; }
	public void setTokenWhatsapp(String tokenWhatsapp) { this.tokenWhatsapp = tokenWhatsapp; }
	public String getTokenLinkAssinatura() { return tokenLinkAssinatura; }
	public void setTokenLinkAssinatura(String tokenLinkAssinatura) { this.tokenLinkAssinatura = tokenLinkAssinatura; }
	public String getLinkAssinatura() { return linkAssinatura; }
	public void setLinkAssinatura(String linkAssinatura) { this.linkAssinatura = linkAssinatura; }
	public String getQualificacao() { return qualificacao; }
	public void setQualificacao(String qualificacao) { this.qualificacao = qualificacao; }
	public String getSituacaoAssinatura() { return situacaoAssinatura; }
	public void setSituacaoAssinatura(String situacaoAssinatura) { this.situacaoAssinatura = situacaoAssinatura; }
	public String getDataAssinatura() { return dataAssinatura; }
	public void setDataAssinatura(String dataAssinatura) { this.dataAssinatura = dataAssinatura; }
	public String getQuantidadeDeAcessosNoLinkDeAssinatura() { return quantidadeDeAcessosNoLinkDeAssinatura; }
	public void setQuantidadeDeAcessosNoLinkDeAssinatura(String quantidadeDeAcessosNoLinkDeAssinatura) { this.quantidadeDeAcessosNoLinkDeAssinatura = quantidadeDeAcessosNoLinkDeAssinatura; }
	public String getMensagemSobreVisualizacaoDoLinkDeAssinatura() { return mensagemSobreVisualizacaoDoLinkDeAssinatura; }
	public void setMensagemSobreVisualizacaoDoLinkDeAssinatura(String mensagemSobreVisualizacaoDoLinkDeAssinatura) { this.mensagemSobreVisualizacaoDoLinkDeAssinatura = mensagemSobreVisualizacaoDoLinkDeAssinatura; }
	public String getDispositivo() { return dispositivo; }
	public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }
	public String getGeolocalizacao() { return geolocalizacao; }
	public void setGeolocalizacao(String geolocalizacao) { this.geolocalizacao = geolocalizacao; }
	public String getAutenticadoVia() { return autenticadoVia; }
	public void setAutenticadoVia(String autenticadoVia) { this.autenticadoVia = autenticadoVia; }
	public String getSelfieBase64() { return selfieBase64; }
	public void setSelfieBase64(String selfieBase64) { this.selfieBase64 = selfieBase64; }
	public String getAssinaturaEscritaBase64() { return assinaturaEscritaBase64; }
	public void setAssinaturaEscritaBase64(String assinaturaEscritaBase64) { this.assinaturaEscritaBase64 = assinaturaEscritaBase64; }
}
