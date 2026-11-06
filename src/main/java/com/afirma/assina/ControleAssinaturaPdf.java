package com.afirma.assina;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController

public class ControleAssinaturaPdf {
	private static final Logger registroLog = LoggerFactory.getLogger(ControleAssinaturaPdf.class);
	private static final Set<String> MOTIVOS_SEGUROS = Set.of(
			"PDF e certificado são obrigatórios", "Certificado sem chave privada",
			"Certificado sem chave privada ou cadeia de certificados", "Certificado X.509 inválido",
			"Arquivo do certificado inválido", "PDF inválido ou protegido por senha",
			"Informe o nome da empresa (até 120 caracteres)",
			"A imagem da empresa deve ter até 1 MB", "Use imagem PNG ou JPEG da empresa",
			"Imagem da empresa inválida", "Nome da empresa muito longo para a página");

	private final ServicoAssinaturaPdf servico;
	private final ValidadorPedidoAssinatura validador;
    private final PaginaAssinaturaEmpresa paginas;
	private final boolean permitirCertificadoVencido;

	public ControleAssinaturaPdf(ServicoAssinaturaPdf servico, ValidadorPedidoAssinatura validador,
			@Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}") boolean permitirCertificadoVencido, PaginaAssinaturaEmpresa paginas) {
		this.servico = servico;
        this.paginas = paginas;
		this.validador = validador;
		this.permitirCertificadoVencido = permitirCertificadoVencido;
	}

	@PostMapping(value = {"/api/pdfs/assinar", "/api/assineai/pdf/assinar"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> assinar(@Valid @ModelAttribute PedidoAssinaturaPdf pedido, jakarta.servlet.http.HttpServletRequest requisicao) throws Exception {
        var pagina = paginas.daRequisicao(requisicao);
        if (pagina != null) {
            pedido.setNomeEmpresa(pagina.nomeEmpresa());
            pedido.setPaginaPersonalizada(true);
        }
		ValidadorPedidoAssinatura.ArquivosValidados arquivos = validador.validar(pedido);
		char[] senhaCertificado = pedido.getSenha().toCharArray();
		try {
			byte[] imagem = pagina == null && pedido.isPaginaPersonalizada() && pedido.getImagemEmpresa() != null
					? pedido.getImagemEmpresa().getBytes() : null;
			byte[] assinado;
            if (pagina != null) {
                assinado = servico.assinar(arquivos.pdf(), arquivos.certificado(), senhaCertificado,
                    permitirCertificadoVencido, true, pagina.nomeEmpresa(), null, pagina.modelo());
            } else if (pedido.isPaginaPersonalizada() && pedido.getTemplatePagina()!=null && !pedido.getTemplatePagina().isBlank()) {
                var modelo=TemplatesEmpresa.lerModelo(pedido.getTemplatePagina());
                assinado=servico.assinar(arquivos.pdf(),arquivos.certificado(),senhaCertificado,permitirCertificadoVencido,true,pedido.getNomeEmpresa(),imagem,modelo);
            } else assinado = servico.assinar(arquivos.pdf(), arquivos.certificado(), senhaCertificado,
					permitirCertificadoVencido, pedido.isPaginaPersonalizada(), pedido.getNomeEmpresa(), imagem);
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.attachment().filename("documento-assinado.pdf").build().toString())
					.header(HttpHeaders.CACHE_CONTROL, "no-store")
					.header("X-Content-Type-Options", "nosniff")
					.contentType(MediaType.APPLICATION_PDF)
					.body(assinado);
		} catch (CertificateExpiredException erro) {
			registroLog.warn("Assinatura recusada: certificado vencido");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificado vencido", erro);
		} catch (CertificateNotYetValidException erro) {
			registroLog.warn("Assinatura recusada: certificado ainda não válido");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Certificado ainda não é válido", erro);
		} catch (UnrecoverableKeyException erro) {
			registroLog.warn("Assinatura recusada: senha do certificado incorreta");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha do certificado incorreta", erro);
		} catch (IllegalArgumentException erro) {
			String motivo = erro.getMessage() != null && MOTIVOS_SEGUROS.contains(erro.getMessage())
					? erro.getMessage() : "PDF ou certificado inválido";
			registroLog.warn("Assinatura recusada: {}", motivo, erro);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, motivo, erro);
		} catch (GeneralSecurityException | IOException erro) {
			registroLog.warn("Assinatura recusada: falha ao processar PDF ou certificado", erro);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível processar o PDF ou certificado", erro);
        } catch (ResponseStatusException erro) {
            throw erro;
		} catch (Exception erro) {
			String referencia = UUID.randomUUID().toString();
			registroLog.error("Falha interna ao assinar PDF. Referência {}", referencia, erro);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Falha interna ao assinar. Referência: " + referencia, erro);
		} finally {
			Arrays.fill(senhaCertificado, '\0');
		}
	}
}
