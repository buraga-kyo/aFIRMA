package com.afirma.assina;

public record ProvaBlockchain(int versao, String carteira, String resumoPdf, String identificador,
		long prazo, String assinatura, String transacao, String bloco, long dataHora,
		String nomeEmpresa) {
	public ProvaBlockchain(int versao, String carteira, String resumoPdf, String identificador,
			long prazo, String assinatura, String transacao, String bloco, long dataHora) {
		this(versao, carteira, resumoPdf, identificador, prazo, assinatura, transacao, bloco,
				dataHora, null);
	}
}
