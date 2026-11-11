package com.afirma.assina;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PoliticaDocumentosAssinatura {
	public static final String DECLARACAO_FORMALIDADES = "Revisei a capacidade das partes, os poderes de representação e as formalidades "
			+ "aplicáveis ao documento. Confirmo que este fluxo de aceite por e-mail e selo final da empresa é adequado ao ato "
			+ "e que ele não exige assinatura pessoal qualificada, escritura pública ou procedimento específico.";
	public static final String DECLARACAO_PODERES = "Confirmo que tenho autorização para utilizar este certificado "
			+ "e poderes para a representação declarada. O selo final pertence ao titular do certificado e não substitui "
			+ "eventuais assinaturas pessoais exigidas para o documento.";
	private final int diasRetencao;
	private final boolean informacoesPrivacidadeConfiguradas;
	private final String avisoPrivacidade;
	private final boolean fotografiaPermitida;

	public PoliticaDocumentosAssinatura(Environment ambiente) {
		informacoesPrivacidadeConfiguradas = ambiente.matchesProfiles("local-test")
				|| !ambiente.getProperty("ASSINEAI_CONTROLADOR_NOME", "").isBlank()
				&& !ambiente.getProperty("ASSINEAI_CONTROLADOR_CONTATO", "").isBlank();
		String finalidadeFoto = ambiente.getProperty("ASSINEAI_FINALIDADE_FOTOGRAFIA", "");
		fotografiaPermitida = ambiente.getProperty("ASSINEAI_PERMITIR_FOTOGRAFIA", Boolean.class, false) && !finalidadeFoto.isBlank();
		avisoPrivacidade = "Usamos o e-mail e registros de acesso para confirmar o canal informado e documentar a manifestação. "
				+ "O PDF compartilhado com os participantes inclui nome, e-mail, CPF, RG e WhatsApp informados, sem ocultação, "
                + "além de horário, IP e localização do navegador com precisão e momento da coleta. A localização é obrigatória para confirmar o aceite. "
                + "A assinatura da empresa inclui CNPJ e os dados do responsável cadastrado. Na criação automática, registramos o acesso e a localização do operador que gera os convites, identificados nesse contexto. "
                + "Em conexões locais ou privadas, o navegador consulta api64.ipify.org para obter seu IP público; "
                + "essa consulta transmite o IP ao serviço e não envia o documento ou os dados cadastrais. "
                + "O código não verifica automaticamente a identidade civil ou os poderes do participante. "
				+ "As versões aceitas, marcas, horários e registros de autenticação são preservados em um pacote de provas acessível ao responsável autorizado. "
				+ "O selo final pertence ao titular do certificado da empresa. A exclusão da lista não apaga as provas. "
				+ "O prazo de preservação é declarado no envelope e pode ser estendido para obrigações ou defesa de direitos. "
				+ "Responsável informado: " + ambiente.getProperty("ASSINEAI_CONTROLADOR_NOME", "Empresa responsável pelo documento")
				+ ". Contato: " + ambiente.getProperty("ASSINEAI_CONTROLADOR_CONTATO", "Solicite à empresa que enviou o convite")
				+ ". Fundamento informado para o tratamento: " + ambiente.getProperty("ASSINEAI_BASE_TRATAMENTO_DADOS", "execução do instrumento e exercício regular de direitos, sujeito à avaliação do responsável")
				+ ". " + (fotografiaPermitida ? "Quando solicitada, a fotografia integra o PDF visível aos participantes e o pacote de provas. "
						+ "Finalidade informada: " + finalidadeFoto + ". Não realizamos reconhecimento biométrico." : "Fotografias não estão habilitadas neste fluxo.");
		diasRetencao = ambiente.getProperty("ASSINEAI_RETENCAO_EVIDENCIAS_DIAS", Integer.class, 1825);
		if (diasRetencao < 1 || diasRetencao > 36500) throw new IllegalArgumentException("Prazo de preservação inválido");
	}

	public List<TipoPermitido> tiposPermitidos() {
		return List.of(new TipoPermitido("CONTRATO_PARTICULAR", "Contrato particular sem forma especial"),
				new TipoPermitido("TERMO_ACEITE", "Termo de aceite sem forma especial"));
	}

	public void validarCriacao(String tipo, String declaracao) {
		if (!informacoesPrivacidadeConfiguradas) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Configure o nome e o contato do responsável pelo tratamento dos dados");
		if (tiposPermitidos().stream().noneMatch(permitido -> permitido.codigo().equals(tipo))) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Tipo de documento não atendido neste fluxo. Atos públicos, documentos de saúde, procurações e atos notariais exigem avaliação e fluxo próprios");
		}
		if (!DECLARACAO_FORMALIDADES.equals(declaracao)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Revise as formalidades do documento antes de gerar os convites");
	}

	public void validarRepresentacao(String papel, String representado, String fundamento) {
		if ("EM_NOME_PROPRIO".equals(papel)) {
			if (representado != null && !representado.isBlank() || fundamento != null && !fundamento.isBlank()) {
				throw new ErroAssinatura("Representação informada para participante que assina em nome próprio");
			}
			return;
		}
		if (!"REPRESENTANTE".equals(papel) || representado == null || representado.isBlank()
				|| representado.length() > 254 || fundamento == null || fundamento.isBlank() || fundamento.length() > 1000) {
			throw new ErroAssinatura("Informe em nome de quem o representante atua e o fundamento dos poderes");
		}
	}

	public String avisoPrivacidade() { return avisoPrivacidade; }
	public boolean fotografiaPermitida() { return fotografiaPermitida; }
	public void validarFotografia(boolean solicitada) {
		if (solicitada && !fotografiaPermitida) throw new ErroAssinatura("Fotografia não habilitada. Revise a necessidade e a finalidade antes de solicitar esse dado");
	}

	public Instant prazoPreservacao(Instant criacao) { return criacao.plus(diasRetencao, ChronoUnit.DAYS); }
	public record TipoPermitido(String codigo, String nome) { }
}
