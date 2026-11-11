package com.afirma.assina;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAssinaturaDireta {
	private final RepositorioEnvelopeAssinatura envelopes;
	private final ServicoEvidenciasAssinatura evidencias;
	private final PoliticaDocumentosAssinatura politica;
	private final ServicoAssinaturaPdf assinador;
	private final ArmazenamentoMinio armazenamento;
	private final ServicoPedidosAssinatura pedidos;
	@org.springframework.beans.factory.annotation.Autowired
	private PaginaAssinaturaEmpresa paginas;

	public ServicoAssinaturaDireta(RepositorioEnvelopeAssinatura envelopes, ServicoEvidenciasAssinatura evidencias,
			PoliticaDocumentosAssinatura politica, ServicoAssinaturaPdf assinador, ArmazenamentoMinio armazenamento, ServicoPedidosAssinatura pedidos) {
		this.envelopes = envelopes;
		this.evidencias = evidencias;
		this.politica = politica;
		this.assinador = assinador;
		this.armazenamento = armazenamento;
		this.pedidos = pedidos;
	}

	@Transactional(rollbackFor = Exception.class)
	public Assinado assinar(PedidoAssinaturaPdf pedido, ValidadorPedidoAssinatura.ArquivosValidados arquivos,
			char[] senha, boolean permitirVencido, String criador) throws Exception {
		politica.validarCriacao(pedido.getTipoDocumento(), pedido.getDeclaracaoFormalidades());
		if (!PoliticaDocumentosAssinatura.DECLARACAO_PODERES.equals(pedido.getDeclaracaoPoderes())) {
			throw new ErroAssinatura("Confirme os poderes para usar o certificado");
		}
		if (pedido.getImagemEmpresa() != null && pedido.getImagemEmpresa().getSize() > 1024 * 1024) throw new ErroAssinatura("A imagem da empresa deve ter até 1 MB");
		byte[] imagem = pedido.isPaginaPersonalizada() && pedido.getImagemEmpresa() != null ? pedido.getImagemEmpresa().getBytes() : null;
		var dadosPedido = Map.of("tipoDocumento", pedido.getTipoDocumento(), "formalidades", pedido.getDeclaracaoFormalidades(),
				"poderes", pedido.getDeclaracaoPoderes(), "paginaPersonalizada", pedido.isPaginaPersonalizada(),
				"nomeEmpresa", java.util.Objects.toString(pedido.getNomeEmpresa(), ""),
				"nomeDocumento", java.util.Objects.toString(pedido.getPdf().getOriginalFilename(), ""));
		String resumoPedido = pedidos.resumir(dadosPedido, arquivos.pdf(), arquivos.certificado(), imagem);
		var anterior = pedidos.iniciar(pedido.getIdRequisicao(), criador, "DIRETA", resumoPedido);
		if (anterior != null) {
			var existente = envelopes.findById(anterior.envelope()).filter(item -> item.getCriador().equals(criador)
					&& item.getExcluidoEm() == null && item.getCanceladoEm() == null)
					.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
			byte[] finalizado = existente.getPdfFinalChaveMinio() == null ? existente.getPdfFinal() : armazenamento.ler(existente.getPdfFinalChaveMinio());
			evidencias.conferir(existente.getId());
			evidencias.conferirConteudoRegistrado(existente.getId(), finalizado, "PDF_FINAL");
			return new Assinado(existente.getId(), finalizado);
		}
		UUID identificador = UUID.randomUUID();
		Instant agora = Instant.now();
		String nomeDocumento = pedido.getPdf().getOriginalFilename();
		if (nomeDocumento == null || nomeDocumento.isBlank()) nomeDocumento = "documento.pdf";
		if (nomeDocumento.length() > 255) nomeDocumento = nomeDocumento.substring(0, 255);
		EnvelopeAssinatura envelope = new EnvelopeAssinatura(identificador,
				pedido.getNomeEmpresa() == null || pedido.getNomeEmpresa().isBlank() ? "Titular do certificado" : pedido.getNomeEmpresa(),
				nomeDocumento, criador, arquivos.pdf(), agora, agora.plusSeconds(7 * 86400));
		envelope.definirFormalidades(pedido.getTipoDocumento(), pedido.getDeclaracaoFormalidades(), politica.prazoPreservacao(agora));
		if (armazenamento.ativo()) envelope.guardarOriginalNoMinio(armazenamento.guardar(identificador, "original", arquivos.pdf()));
		envelope = envelopes.saveAndFlush(envelope);
		evidencias.registrar(identificador, null, "ASSINATURA_DIRETA_SOLICITADA", Map.of("criador", criador,
				"tipoDocumento", pedido.getTipoDocumento(), "declaracaoFormalidades", pedido.getDeclaracaoFormalidades(),
				"declaracaoPoderes", pedido.getDeclaracaoPoderes(), "certificadoVencidoPermitido", permitirVencido));
		evidencias.guardarArquivo(identificador, null, "PDF_ORIGINAL", nomeDocumento, "application/pdf", arquivos.pdf(), envelope.getPdfOriginalChaveMinio());
		evidencias.guardarArquivo(identificador, null, "AVISO_PRIVACIDADE", "aviso-privacidade.txt", "text/plain",
				politica.avisoPrivacidade().getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
		byte[] manifesto = evidencias.manifestoParaAssinar(identificador);
		byte[] selo = assinador.assinarManifesto(manifesto, arquivos.certificado(), senha, permitirVencido);
		evidencias.guardarArquivo(identificador, null, "MANIFESTO_ASSINADO", "manifesto-assinado.json", "application/json", manifesto, null);
		evidencias.guardarArquivo(identificador, null, "SELO_MANIFESTO", "manifesto-assinado.p7s", "application/pkcs7-signature", selo, null);
		byte[] preparado = new ComprovanteAceites().acrescentarVinculoEvidencias(arquivos.pdf(), envelope,
				SegredosAssinatura.resumo(manifesto), SegredosAssinatura.resumo(selo));
		var pagina = paginas.doUsuarioAtual();
		var modelo = pagina != null ? pagina.modelo()
				: pedido.getTemplatePagina() == null || pedido.getTemplatePagina().isBlank()
						? TemplatesEmpresa.padrao() : TemplatesEmpresa.lerModelo(pedido.getTemplatePagina());
		byte[] pdf = assinador.assinar(preparado, arquivos.certificado(), senha, permitirVencido,
				pedido.isPaginaPersonalizada(), pedido.getNomeEmpresa(), imagem, modelo);
		envelope.finalizar(pdf, Instant.now());
		if (armazenamento.ativo()) envelope.guardarFinalNoMinio(armazenamento.guardar(identificador, "final", pdf));
		evidencias.guardarArquivo(identificador, null, "PDF_FINAL", "documento-final.pdf", "application/pdf", pdf, envelope.getPdfFinalChaveMinio());
		evidencias.registrar(identificador, null, "ENVELOPE_FINALIZADO", Map.of("resumoPdfFinal", SegredosAssinatura.resumo(pdf),
				"resumoManifesto", SegredosAssinatura.resumo(manifesto), "resumoSeloManifesto", SegredosAssinatura.resumo(selo)));
		pedidos.concluir(pedido.getIdRequisicao(), criador, "DIRETA", resumoPedido, identificador, null);
		return new Assinado(identificador, pdf);
	}
	public record Assinado(UUID identificador, byte[] pdf) { }
}
