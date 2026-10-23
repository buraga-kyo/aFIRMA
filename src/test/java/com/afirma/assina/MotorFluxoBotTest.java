package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MotorFluxoBotTest {
	private final NamedParameterJdbcTemplate banco = mock(NamedParameterJdbcTemplate.class);
	private final ObjectMapper json = new ObjectMapper();
	private final MotorFluxoBot motor = new MotorFluxoBot(banco, json);
	private final MotorFluxoBot.Contexto contexto = new MotorFluxoBot.Contexto(1,
		UUID.randomUUID(), 7, UUID.randomUUID(), "5511999999999", "Ana");

	private JsonNode fluxo() throws Exception {
		return json.readTree("""
			{"nodes":[
			 {"id":"menu","type":"tarefa","data":{"pergunta":"Olá &ContatoNome. Escolha 1 ou 2",
			  "variavel":"opcao","acoes":[
			   {"valor":"setor","condicao":"&mensagem === '1'"},
			   {"valor":"fim","condicao":"&mensagem === '2'"}]}},
			 {"id":"setor","type":"script","data":{"tipo":"transferir_setor",
			  "setor":{"id":3},"destino_transferir":"gerar"}},
			 {"id":"gerar","type":"script","data":{"tipo":"gerar_atendimento"}},
			 {"id":"fim","type":"tarefa","data":{"pergunta":"Até logo, &OPCAO",
			  "acoes":[{"valor":"end","condicao":""}]}}
			],"edges":[
			 {"source":"start","target":"menu"},
			 {"source":"setor","target":"gerar"},
			 {"source":"gerar","target":"end"},
			 {"source":"fim","target":"end"}
			]}
			""");
	}

	@Test
	void perguntaFalhaEscolhaEFinalizacao() throws Exception {
		JsonNode fluxo = fluxo();
		motor.validar(fluxo);
		var inicio = motor.avancar(fluxo, null, "oi", Map.of(), contexto);
		assertFalse(inicio.concluida());
		assertEquals("menu", inicio.etapa());
		assertEquals("Olá Ana. Escolha 1 ou 2", inicio.texto());
		var invalida = motor.avancar(fluxo, inicio.etapa(), "9", inicio.variaveis(), contexto);
		assertEquals("menu", invalida.etapa());
		assertTrue(invalida.texto().contains("Não entendi"));
		var escolha = motor.avancar(fluxo, invalida.etapa(), "2", invalida.variaveis(), contexto);
		assertEquals("fim", escolha.etapa());
		assertEquals("Até logo, 2", escolha.texto());
		var fim = motor.avancar(fluxo, escolha.etapa(), "ok", escolha.variaveis(), contexto);
		assertTrue(fim.concluida());
	}

	@Test
	void transfereSetorEGeraAtendimento() throws Exception {
		when(banco.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
			.thenReturn(1);
		when(banco.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
		var inicio = motor.avancar(fluxo(), null, "oi", Map.of(), contexto);
		var fim = motor.avancar(fluxo(), inicio.etapa(), "1", inicio.variaveis(), contexto);
		assertTrue(fim.concluida());
		assertEquals(3L, fim.variaveis().get("SetorId"));
		verify(banco).update(anyString(), any(MapSqlParameterSource.class));
	}

	@Test
	void aceitaCondicoesCompostasEFormatadores() throws Exception {
		JsonNode fluxo = json.readTree("""
			{"nodes":[{"id":"inicio","type":"tarefa","data":{
			 "pergunta":"Número: somenteNumero(&NumeroRemetente)",
			 "acoes":[{"valor":"end","condicao":"&mensagem === 'sim' && &ContatoNome === 'Ana'"}]
			}}],"edges":[{"source":"start","target":"inicio"}]}
			""");
		var inicio = motor.avancar(fluxo, null, "oi", Map.of(), contexto);
		assertEquals("Número: 5511999999999", inicio.texto());
		var fim = motor.avancar(fluxo, inicio.etapa(), "sim", inicio.variaveis(), contexto);
		assertTrue(fim.concluida());
	}
}
