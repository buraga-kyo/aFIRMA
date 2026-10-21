package com.afirma.assina;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "campo_assinatura")
public class CampoAssinatura {
	public enum Tipo { ASSINATURA, RUBRICA, SELFIE }

	@Id
	private UUID id;
	@Column(name = "signatario_id", nullable = false)
	private UUID signatarioId;
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

	protected CampoAssinatura() { }

	public CampoAssinatura(UUID id, UUID signatarioId, Tipo tipo, int pagina,
			double posicaoX, double posicaoY, double largura, double altura) {
		this.id = id;
		this.signatarioId = signatarioId;
		this.tipo = tipo.name();
		this.pagina = pagina;
		this.posicaoX = posicaoX;
		this.posicaoY = posicaoY;
		this.largura = largura;
		this.altura = altura;
	}

	public UUID getId() { return id; }
	public UUID getSignatarioId() { return signatarioId; }
	public Tipo getTipo() { return Tipo.valueOf(tipo); }
	public int getPagina() { return pagina; }
	public double getPosicaoX() { return posicaoX; }
	public double getPosicaoY() { return posicaoY; }
	public double getLargura() { return largura; }
	public double getAltura() { return altura; }
}
