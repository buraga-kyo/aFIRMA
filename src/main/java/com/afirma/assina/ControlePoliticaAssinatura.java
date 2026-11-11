package com.afirma.assina;

import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assinaturas")
public class ControlePoliticaAssinatura {
	private final PoliticaDocumentosAssinatura politica;
	private final String controlador;
	private final String contato;
	private final boolean carteiraExperimental;

	public ControlePoliticaAssinatura(PoliticaDocumentosAssinatura politica, Environment ambiente) {
		this.politica = politica;
		controlador = ambiente.getProperty("ASSINEAI_CONTROLADOR_NOME", "Empresa responsável pelo documento");
		carteiraExperimental = ambiente.getProperty("APP_ASSINATURA_BLOCKCHAIN_EXPERIMENTAL", Boolean.class, false);
		contato = ambiente.getProperty("ASSINEAI_CONTROLADOR_CONTATO", "Solicite o contato à empresa que enviou o convite");
	}

	@GetMapping("/politica")
	public Politica consultar() {
		return new Politica(politica.tiposPermitidos(), PoliticaDocumentosAssinatura.DECLARACAO_FORMALIDADES,
				PoliticaDocumentosAssinatura.DECLARACAO_PODERES, controlador, contato,
				politica.avisoPrivacidade(), politica.fotografiaPermitida(), carteiraExperimental);
	}
	public record Politica(List<PoliticaDocumentosAssinatura.TipoPermitido> tiposDocumento, String declaracaoFormalidades,
			String declaracaoPoderes, String controlador, String contato, String avisoPrivacidade, boolean fotografiaPermitida, boolean carteiraExperimental) { }
}
