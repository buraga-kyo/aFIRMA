package com.afirma.assina;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracao_assinatura")
public class ConfiguracaoAssinatura {

	@Id
	private Long id;

	@Column(name = "tamanho_maximo_pdf_bytes", nullable = false)
	private long tamanhoMaximoPdfBytes;

	protected ConfiguracaoAssinatura() {
	}

	public long getTamanhoMaximoPdfBytes() {
		return tamanhoMaximoPdfBytes;
	}
}
