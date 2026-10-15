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

	@NotBlank
	@Size(max = 256)
	private String senha;

	private boolean permitirCertificadoVencido;

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

	public boolean isPermitirCertificadoVencido() {
		return permitirCertificadoVencido;
	}

	public void setPermitirCertificadoVencido(boolean permitirCertificadoVencido) {
		this.permitirCertificadoVencido = permitirCertificadoVencido;
	}
}
