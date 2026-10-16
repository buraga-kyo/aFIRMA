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

	public ServicoAssinaturaBlockchain(ValidadorPedidoAssinatura validador, ServicoPdfBlockchain pdfs,
			ProtocoloBlockchain protocolo, ConsultaSepolia sepolia,
			RepositorioAssinaturaBlockchain repositorio) {
		this.validador = validador;
		this.pdfs = pdfs;
		this.protocolo = protocolo;
		this.sepolia = sepolia;
		this.repositorio = repositorio;
	}

	@Transactional
	public Preparacao preparar(MultipartFile arquivo, String carteira) throws Exception {
		protocolo.bytesHex(carteira, 20);
		byte[] original = validador.validarPdf(arquivo);
		ServicoPdfBlockchain.PdfPreparado preparado = pdfs.preparar(original);
		UUID identificador = UUID.randomUUID();
		Instant prazo = Instant.now().plusSeconds(3600);
		repositorio.save(new AssinaturaBlockchainPendente(identificador, carteira.toLowerCase(),
				preparado.resumo(), preparado.bytes(), prazo));
		return new Preparacao(identificador, preparado.resumo(),
				protocolo.identificadorAssinado(identificador), prazo.getEpochSecond());
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
				identificador, pendente.getExpiraEm().getEpochSecond(), assinatura);
		if (!recuperada.equalsIgnoreCase(pendente.getCarteira())) {
			throw new IllegalArgumentException("Assinatura não pertence à carteira conectada");
		}
		String dados = protocolo.dadosTransacao(pendente.getResumoPdf(), assinatura);
		ConsultaSepolia.Registro registro = sepolia.conferir(transacao, pendente.getCarteira(), dados);
		if (registro == null) return null;
		if (registro.dataHora() > pendente.getExpiraEm().getEpochSecond()) {
			throw new IllegalArgumentException("Transação ocorreu após o prazo da assinatura");
		}
		ProvaBlockchain prova = new ProvaBlockchain(1, pendente.getCarteira(), pendente.getResumoPdf(),
				identificador.toString(), pendente.getExpiraEm().getEpochSecond(), assinatura,
				transacao, registro.bloco(), registro.dataHora());
		byte[] concluido = pdfs.concluir(pendente.getPdfPreparado(), prova);
		repositorio.delete(pendente);
		return concluido;
	}

	public ProvaBlockchain verificar(MultipartFile arquivo) throws Exception {
		ServicoPdfBlockchain.ProvaLida leitura = pdfs.ler(validador.validarPdfAssinado(arquivo));
		ProvaBlockchain prova = leitura.prova();
		if (prova.versao() != 1 || !leitura.resumoCalculado().equalsIgnoreCase(prova.resumoPdf())) {
			throw new IllegalArgumentException("PDF alterado após a assinatura");
		}
		UUID identificador = UUID.fromString(prova.identificador());
		String recuperada = protocolo.recuperarCarteira(prova.resumoPdf(), prova.carteira(),
				identificador, prova.prazo(), prova.assinatura());
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

	public record Preparacao(UUID identificador, String resumoPdf, String identificadorAssinado, long prazo) { }
}
