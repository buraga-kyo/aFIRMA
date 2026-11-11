package com.afirma.assina;

public class ErroAssinatura extends IllegalArgumentException {
	public ErroAssinatura(String mensagem) { super(mensagem); }
	public ErroAssinatura(String mensagem, Throwable causa) { super(mensagem, causa); }
}
