package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class PedidoCamposAssinaturaJsonTest {
  @ParameterizedTest
  @ValueSource(strings = {"", ", \"rotacao\":null", ", \"rotacao\":0"})
  void aceitaCampoSemRotacaoDefinida(String rotacao) {
    var campo = ler(rotacao);
    assertEquals(0, campo.rotacao().intValue());
    var entidade = new CampoAssinatura(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
        campo.tipo(), campo.pagina(), campo.posicaoX(), campo.posicaoY(), campo.largura(), campo.altura());
    entidade.setRotacao(campo.rotacao());
    new ServicoCamposPdf().validar(entidade, 1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 90, 180, 270})
  void preservaRotacaoEnviada(int rotacao) {
    assertEquals(rotacao, ler(", \"rotacao\":" + rotacao).rotacao().intValue());
  }

  private ServicoEnvelopes.CampoNovo ler(String rotacao) {
    return JsonMapper.builder().build().readValue("""
      {"tipo":"ASSINATURA","pagina":1,"posicaoX":0.1,"posicaoY":0.2,
       "largura":0.3,"altura":0.1,"documento":0%s}
      """.formatted(rotacao), ServicoEnvelopes.CampoNovo.class);
  }
}
