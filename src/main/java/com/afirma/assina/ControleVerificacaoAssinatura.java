package com.afirma.assina;

import java.util.Arrays;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdfs")
public class ControleVerificacaoAssinatura {
	private final ServicoAssinaturaPdf assinador;
	private final VerificadorAssinaturasPdf verificador;
	private final ValidadorPedidoAssinatura validador;
	private final boolean permitirCertificadoVencido;

	public ControleVerificacaoAssinatura(ServicoAssinaturaPdf assinador,
			VerificadorAssinaturasPdf verificador, ValidadorPedidoAssinatura validador,
			@org.springframework.beans.factory.annotation.Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}") boolean permitirCertificadoVencido) {
		this.assinador = assinador;
		this.verificador = verificador;
		this.validador = validador;
		this.permitirCertificadoVencido = permitirCertificadoVencido;
	}

	@PostMapping(value = "/certificado", consumes = "multipart/form-data")
	public ServicoAssinaturaPdf.DadosCertificado certificado(@RequestParam("certificado") MultipartFile certificado,
			@RequestParam(value = "senha", required = false) String senha,
			@RequestParam(value = "finalizacaoEnvelope", defaultValue = "false") boolean finalizacaoEnvelope) throws Exception {
		senha = validador.resolverSenhaCertificado(senha);
		byte[] dadosCertificado = validador.validarCertificado(certificado);
		char[] senhaCertificado = senha.toCharArray();
		try {
			return assinador.consultarCertificado(dadosCertificado, senhaCertificado,
					permitirCertificadoVencido);
		} finally {
			Arrays.fill(dadosCertificado, (byte) 0);
			Arrays.fill(senhaCertificado, '\0');
		}
	}

	@PostMapping(value = "/verificar", consumes = "multipart/form-data")
	public VerificadorAssinaturasPdf.Resultado verificar(@RequestParam("pdf") MultipartFile pdf) throws Exception {
		return verificador.verificar(validador.validarPdfParaVerificacao(pdf));
	}
}
