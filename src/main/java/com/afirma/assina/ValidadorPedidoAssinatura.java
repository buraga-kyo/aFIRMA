package com.afirma.assina;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ValidadorPedidoAssinatura {

	private static final long TETO_PDF_BYTES = 20L * 1024 * 1024;
	private static final long TETO_CERTIFICADO_BYTES = 2L * 1024 * 1024;
	private final RepositorioConfiguracaoAssinatura configuracoes;
	@org.springframework.beans.factory.annotation.Value("${ASSINEAI_CERTIFICADO_SENHA:}")
	private String senhaCertificadoConfigurada = "";

	public String resolverSenhaCertificado(String senhaInformada) {
		String senha = senhaInformada == null || senhaInformada.isEmpty()
				? senhaCertificadoConfigurada : senhaInformada;
		if (senha == null || senha.isBlank() || senha.length() > 256) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha do certificado (até 256 caracteres)");
		}
		return senha;
	}

	public ValidadorPedidoAssinatura(RepositorioConfiguracaoAssinatura configuracoes) {
		this.configuracoes = configuracoes;
	}

	public ArquivosValidados validar(PedidoAssinaturaPdf pedido) {
		pedido.setSenha(resolverSenhaCertificado(pedido.getSenha()));
		byte[] dadosPdf = validarPdf(pedido.getPdf());
		return new ArquivosValidados(dadosPdf, validarCertificado(pedido.getCertificado()));
	}

	public byte[] validarCertificado(MultipartFile certificado) {
		if (certificado == null || certificado.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificado é obrigatório");
		}
		return lerComLimite(certificado, TETO_CERTIFICADO_BYTES);
	}

	public byte[] validarPdf(MultipartFile pdf) {
		return validarPdf(pdf, 0);
	}

	public byte[] validarPdfAssinado(MultipartFile pdf) {
		return validarPdf(pdf, 16 * 1024);
	}

	public byte[] validarPdfParaVerificacao(MultipartFile pdf) {
		return validarPdf(pdf, 2 * 1024 * 1024);
	}

	private byte[] validarPdf(MultipartFile pdf, int margem) {
		long limite;
		try {
			limite = configuracoes.findById(1L)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
					.getTamanhoMaximoPdfBytes();
		} catch (DataAccessException erro) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configuração indisponível");
		}
		if (limite < 1 || limite > TETO_PDF_BYTES) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Limite de PDF inválido");
		}

		if (pdf == null || pdf.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF é obrigatório");
		}
		verificarMimePdf(pdf.getContentType());
		byte[] dadosPdf = lerComLimite(pdf, limite + margem);
		if (!temCabecalhoPdf(dadosPdf)) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Conteúdo não é PDF");
		}
		return dadosPdf;
	}

	private void verificarMimePdf(String tipoArquivo) {
		try {
			MediaType tipo = MediaType.parseMediaType(tipoArquivo);
			if ("application".equalsIgnoreCase(tipo.getType())
					&& "pdf".equalsIgnoreCase(tipo.getSubtype())) {
				return;
			}
		} catch (RuntimeException erro) {
			// Tipo ausente ou inválido recebe mesma resposta.
		}
		throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MIME do PDF inválido");
	}

	private byte[] lerComLimite(MultipartFile arquivo, long limite) {
		if (arquivo.getSize() > limite) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
		}
		try (InputStream entrada = arquivo.getInputStream()) {
			byte[] dados = entrada.readNBytes((int) limite + 1);
			if (dados.length > limite) {
				throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
			}
			return dados;
		} catch (IOException erro) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler arquivo");
		}
	}

	private boolean temCabecalhoPdf(byte[] dados) {
		return dados.length >= 8
				&& "%PDF-".equals(new String(dados, 0, 5, StandardCharsets.US_ASCII))
				&& Character.isDigit(dados[5]) && dados[6] == '.' && Character.isDigit(dados[7]);
	}

	public record ArquivosValidados(byte[] pdf, byte[] certificado) {
	}
}
