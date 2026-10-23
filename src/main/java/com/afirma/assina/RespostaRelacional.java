package com.afirma.assina;

import java.sql.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.postgresql.util.PGobject;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice(assignableTypes = {
		RelacionamentoControle.class,
		ControleConfiguracoesRelacionamento.class,
		ControleWebhooks.class,
		ControleCanalOficial.class,
		ControleIntegracoesApi.class
})
public class RespostaRelacional implements ResponseBodyAdvice<Object> {
	private final ObjectMapper json;

	public RespostaRelacional(ObjectMapper json) { this.json = json; }

	@Override
	public boolean supports(MethodParameter retorno, Class<? extends HttpMessageConverter<?>> conversor) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object corpo, MethodParameter retorno, MediaType tipo,
			Class<? extends HttpMessageConverter<?>> conversor, ServerHttpRequest requisicao,
			ServerHttpResponse resposta) {
		return normalizar(corpo);
	}

	private Object normalizar(Object valor) {
		if (valor instanceof PGobject objeto) {
			if ("json".equals(objeto.getType()) || "jsonb".equals(objeto.getType())) {
				try { return json.readTree(objeto.getValue()); }
				catch (Exception erro) { throw new IllegalStateException("JSON do banco inválido", erro); }
			}
			return objeto.getValue();
		}
		if (valor instanceof Array vetor) {
			try { return normalizar(vetor.getArray()); }
			catch (Exception erro) { throw new IllegalStateException("Vetor do banco inválido", erro); }
		}
		if (valor instanceof Object[] vetor) {
			List<Object> resultado = new ArrayList<>(vetor.length);
			for (Object elemento : vetor) resultado.add(normalizar(elemento));
			return resultado;
		}
		if (valor instanceof Map<?, ?> mapa) {
			Map<String, Object> resultado = new LinkedHashMap<>();
			mapa.forEach((chave, elemento) -> resultado.put(String.valueOf(chave), normalizar(elemento)));
			return resultado;
		}
		if (valor instanceof List<?> lista) {
			List<Object> resultado = new ArrayList<>(lista.size());
			for (Object elemento : lista) resultado.add(normalizar(elemento));
			return resultado;
		}
		return valor;
	}
}
