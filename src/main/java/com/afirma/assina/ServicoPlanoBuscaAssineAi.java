package com.afirma.assina;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoPlanoBuscaAssineAi {
	public record Etapa(String op, String column, String type, String value, String from, String to, String dir) { }
	public record Plano(List<Etapa> steps, String summary) { }
	
	private final ObjectMapper json;
	private final ChatClient clienteConversa;

	public ServicoPlanoBuscaAssineAi(ObjectMapper json, ChatClient.Builder preparoClienteConversa) {
		this.json = json;
		this.clienteConversa = preparoClienteConversa.build();
	}

	public Plano interpretar(String consulta) { return interpretar(consulta, false); }

	public Plano interpretarConversas(String consulta) { return interpretar(consulta, true); }

	private Plano interpretar(String consulta, boolean conversas) {
		if (consulta == null || consulta.isBlank() || consulta.length() > 500)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta inválida");

		String instrucao = "Você é um assistente de conversão de filtros. Converta a busca do usuário em operações para uma tabela. "
				+ "Responda EXCLUSIVAMENTE em formato JSON puro, sem blocos markdown (```json). "
				+ "Formato exigido: {\"steps\":[{\"op\":\"column-filter\",\"column\":\"status\",\"type\":\"equals\",\"value\":\"Assinado\"}],\"summary\":\"...\"}. "
				+ (conversas ? "Colunas aceitas: contato, telefone, email, ultima, status, canal, naoLidas, updatedAt. " : "Colunas aceitas: id, titulo, data, responsavel, status, quantidadeAssinaturas, signatarios. ")
				+ "Operações suportadas: 'column-filter' com type 'equals' ou 'contains' e 'value'; ou type 'range' com 'from' e 'to'. "
				+ "Ordenação: op 'sort' com 'column' e 'dir' ('asc' ou 'desc'). "
				+ (conversas ? "Mapeamento obrigatório: pendentes -> status equals Pendente; em andamento -> status equals Em andamento; bots -> status equals Bots; finalizados ou concluídos -> status equals Finalizados. Última mensagem é a coluna ultima. Não lidas é a coluna numérica naoLidas. Atualização é a data updatedAt. " : "Mapeamento obrigatório: 'assinados' ou 'concluídos' -> status equals 'Assinado'. 'pendentes' -> status equals 'Pendente'. 'andamento' ou 'progresso' -> status equals 'Em Processo'. ")
				+ "Retorne no máximo 5 etapas. Não invente campos. Se não entender, retorne steps vazio.";
		
		try {
			String jsonResponse = this.clienteConversa.prompt()
					.system(instrucao)
					.user(consulta)
					.call()
					.content();
					
			if (jsonResponse == null) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "IA não retornou resposta");
			}

			jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
			JsonNode resultado = json.readTree(jsonResponse);

			if (!resultado.path("steps").isArray() || resultado.path("steps").size() > 5)
				throw new IllegalArgumentException("Plano inválido");
				
			List<Etapa> etapas = new ArrayList<>();
			List<String> campos = conversas ? List.of("contato", "telefone", "email", "ultima", "status", "canal", "naoLidas", "updatedAt") : List.of("id", "titulo", "data", "responsavel", "status", "quantidadeAssinaturas", "signatarios");
					
			for (JsonNode item : resultado.path("steps")) {
				Etapa etapa = new Etapa(item.path("op").asText(""), item.path("column").asText(""),
						item.path("type").asText(""), item.path("value").asText(""),
						item.path("from").asText(""), item.path("to").asText(""),
						item.path("dir").asText(""));
				if (!campos.contains(etapa.column()) || !validar(etapa, conversas))
					throw new IllegalArgumentException("Operação fora da interface");
				etapas.add(etapa);
			}
			
			return new Plano(etapas, resultado.path("summary").asText("").substring(0,
						Math.min(200, resultado.path("summary").asText("").length())));
		} catch (ResponseStatusException erro) { throw erro; }
		catch (Exception erro) {
			if (erro instanceof InterruptedException) Thread.currentThread().interrupt();
			System.err.println("Erro ao chamar a IA: " + erro.getMessage());
			erro.printStackTrace();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível gerar o plano", erro);
		}
	}

	private boolean validar(Etapa e, boolean conversas) {
		if (e.op().equals("sort")) return List.of("asc", "desc").contains(e.dir());
		if (!e.op().equals("column-filter")) return false;
		if (List.of("equals", "contains").contains(e.type())) return !e.value().isBlank() && e.value().length() <= 120;
		return e.type().equals("range") && (conversas ? List.of("updatedAt", "naoLidas").contains(e.column()) : List.of("data", "quantidadeAssinaturas").contains(e.column()))
				&& (!e.from().isBlank() || !e.to().isBlank());
	}
}
