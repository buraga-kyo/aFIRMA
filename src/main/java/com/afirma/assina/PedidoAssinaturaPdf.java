package com.afirma.assina;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class PedidoAssinaturaPdf {

	@NotNull
	private MultipartFile pdf;

	@NotNull
	private MultipartFile certificado;

	@Size(max = 256)
	private String senha;

	private boolean paginaPersonalizada;

    @Size(max = 800000)
    private String templatePagina;
    public String getTemplatePagina() { return templatePagina; }
    public void setTemplatePagina(String valor) { templatePagina=valor; }

	@Size(max = 120)
	private String nomeEmpresa;

	private MultipartFile imagemEmpresa;

	@NotNull
	private java.util.UUID idRequisicao;
	public java.util.UUID getIdRequisicao() { return idRequisicao; }
	public void setIdRequisicao(java.util.UUID valor) { idRequisicao = valor; }

	@NotBlank
	private String tipoDocumento;
	@NotBlank
	@Size(max = 1000)
	private String declaracaoFormalidades;
	@NotBlank
	@Size(max = 1000)
	private String declaracaoPoderes;

	public String getTipoDocumento() { return tipoDocumento; }
	public void setTipoDocumento(String valor) { tipoDocumento = valor; }
	public String getDeclaracaoFormalidades() { return declaracaoFormalidades; }
	public void setDeclaracaoFormalidades(String valor) { declaracaoFormalidades = valor; }
	public String getDeclaracaoPoderes() { return declaracaoPoderes; }
	public void setDeclaracaoPoderes(String valor) { declaracaoPoderes = valor; }

	public MultipartFile getPdf() {
		return pdf;
	}

	public void setPdf(MultipartFile pdf) {
		this.pdf = pdf;
	}

	public MultipartFile getCertificado() {
		return certificado;
	}

	public void setCertificado(MultipartFile certificado) {
		this.certificado = certificado;
	}

	public String getSenha() {
		return senha;
	}

	public void setSenha(String senha) {
		this.senha = senha;
	}

	public boolean isPaginaPersonalizada() { return paginaPersonalizada; }
	public void setPaginaPersonalizada(boolean paginaPersonalizada) { this.paginaPersonalizada = paginaPersonalizada; }
	public String getNomeEmpresa() { return nomeEmpresa; }
	public void setNomeEmpresa(String nomeEmpresa) { this.nomeEmpresa = nomeEmpresa; }
	public MultipartFile getImagemEmpresa() { return imagemEmpresa; }
	public void setImagemEmpresa(MultipartFile imagemEmpresa) { this.imagemEmpresa = imagemEmpresa; }

}
