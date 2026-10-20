package com.afirma.assina;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServicoAssinaturaBlockchain {
	private final ValidadorPedidoAssinatura validador;
	private final ServicoPdfBlockchain pdfs;
	private final ProtocoloBlockchain protocolo;
	private final ConsultaSepolia sepolia;
	private final RepositorioAssinaturaBlockchain repositorio;
	private final PaginaComprovante paginaComprovante;

	public ServicoAssinaturaBlockchain(ValidadorPedidoAssinatura validador, ServicoPdfBlockchain pdfs,
			ProtocoloBlockchain protocolo, ConsultaSepolia sepolia,
			RepositorioAssinaturaBlockchain repositorio, PaginaComprovante paginaComprovante) {
		this.validador = validador;
		this.pdfs = pdfs;
		this.protocolo = protocolo;
		this.sepolia = sepolia;
		this.repositorio = repositorio;
		this.paginaComprovante = paginaComprovante;
	}

	@Transactional
	public Preparacao preparar(MultipartFile arquivo, String carteira, boolean paginaPersonalizada,
			String nomeEmpresa, MultipartFile imagemEmpresa) throws Exception {
		protocolo.bytesHex(carteira, 20);
		String dominio = nomeEmpresa == null || nomeEmpresa.isBlank()
				? "Assinatura de documento" : nomeEmpresa;
		if (paginaPersonalizada && (nomeEmpresa == null || nomeEmpresa.isBlank())) {
			throw new IllegalArgumentException("Informe o nome da empresa (até 120 caracteres)");
		}
		if (dominio == null || dominio.isBlank() || dominio.length() > 120) {
			throw new IllegalArgumentException("Informe o nome da empresa (até 120 caracteres)");
		}
		dominio = dominio.strip();
		byte[] original = validador.validarPdf(arquivo);
		if (paginaPersonalizada) {
			byte[] imagem = imagemEmpresa == null ? null : imagemEmpresa.getBytes();
			original = paginaComprovante.acrescentar(original, dominio, imagem,
					"Assinatura eletrônica", carteira);
		}
		ServicoPdfBlockchain.PdfPreparado preparado = pdfs.preparar(original);
		UUID identificador = UUID.randomUUID();
		Instant prazo = Instant.now().plusSeconds(3600);
		repositorio.save(new AssinaturaBlockchainPendente(identificador, carteira.toLowerCase(), dominio,
				preparado.resumo(), preparado.bytes(), prazo));
		return new Preparacao(identificador, preparado.resumo(),
				protocolo.identificadorAssinado(identificador), prazo.getEpochSecond(), dominio);
	}

	@Transactional
	public byte[] concluir(UUID identificador, String assinatura, String transacao) {
		protocolo.bytesHex(assinatura, 65);
		protocolo.bytesHex(transacao, 32);
		AssinaturaBlockchainPendente pendente = repositorio.buscarParaConcluir(identificador)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preparo não encontrado"));
		if (Instant.now().isAfter(pendente.getExpiraEm())) {
			throw new ResponseStatusException(HttpStatus.GONE, "Preparo expirado. Envie o PDF novamente");
		}
		String recuperada = protocolo.recuperarCarteira(pendente.getResumoPdf(), pendente.getCarteira(),
				identificador, pendente.getExpiraEm().getEpochSecond(), assinatura, pendente.getNomeEmpresa());
		if (!recuperada.equalsIgnoreCase(pendente.getCarteira())) {
			throw new IllegalArgumentException("Assinatura não pertence à carteira conectada");
		}
		String dados = protocolo.dadosTransacao(pendente.getResumoPdf(), assinatura);
		ConsultaSepolia.Registro registro = sepolia.conferir(transacao, pendente.getCarteira(), dados);
		if (registro == null) return null;
		if (registro.dataHora() > pendente.getExpiraEm().getEpochSecond()) {
			throw new IllegalArgumentException("Transação ocorreu após o prazo da assinatura");
		}
		ProvaBlockchain prova = new ProvaBlockchain(2, pendente.getCarteira(), pendente.getResumoPdf(),
				identificador.toString(), pendente.getExpiraEm().getEpochSecond(), assinatura,
				transacao, registro.bloco(), registro.dataHora(), pendente.getNomeEmpresa());
		byte[] concluido = pdfs.concluir(pendente.getPdfPreparado(), prova);
		repositorio.delete(pendente);
		return concluido;
	}

	public ProvaBlockchain verificar(MultipartFile arquivo) throws Exception {
		ServicoPdfBlockchain.ProvaLida leitura = pdfs.ler(validador.validarPdfAssinado(arquivo));
		ProvaBlockchain prova = leitura.prova();
		if ((prova.versao() != 1 && prova.versao() != 2)
				|| !leitura.resumoCalculado().equalsIgnoreCase(prova.resumoPdf())) {
			throw new IllegalArgumentException("PDF alterado após a assinatura");
		}
		UUID identificador = UUID.fromString(prova.identificador());
		String dominio = prova.versao() == 1 ? "aFIRMA" : prova.nomeEmpresa();
		String recuperada = protocolo.recuperarCarteira(prova.resumoPdf(), prova.carteira(),
				identificador, prova.prazo(), prova.assinatura(), dominio);
		if (!recuperada.equalsIgnoreCase(prova.carteira())) {
			throw new IllegalArgumentException("Assinatura da carteira inválida");
		}
		ConsultaSepolia.Registro registro = sepolia.conferir(prova.transacao(), prova.carteira(),
				protocolo.dadosTransacao(prova.resumoPdf(), prova.assinatura()));
		if (registro == null || !registro.bloco().equalsIgnoreCase(prova.bloco())
				|| registro.dataHora() != prova.dataHora() || registro.dataHora() > prova.prazo()) {
			throw new IllegalArgumentException("Prova blockchain não confirmada");
		}
		return prova;
	}

	@Scheduled(fixedDelay = 300000)
	@Transactional
	public void limparExpirados() {
		repositorio.deleteByExpiraEmBefore(Instant.now());
	}

	public record Preparacao(UUID identificador, String resumoPdf, String identificadorAssinado,
			long prazo, String nomeEmpresa) { }
}
