package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class CodigosAssineaiLegadoTest {

  private final BancoAssineai banco = mock(BancoAssineai.class);
  private final ServicoEntregaAssinatura entrega = mock(
    ServicoEntregaAssinatura.class
  );
  private final Map<String, Object> pessoa = new LinkedHashMap<>(
    Map.of(
      "AssinaturaId",
      17,
      "SignatarioId",
      91,
      "SignatarioNome",
      "Pessoa",
      "SignatarioFormaDeAutenticacao",
      List.of("tela"),
      "SignatarioEmail",
      "legada@example.com",
      "SignatarioTokenEmail",
      "123456"
    )
  );

  private ServicoAssineai servico() {
    when(banco.um(anyString(), anyMap())).thenAnswer(a -> {
      String sql = a.getArgument(0);
      if (sql.contains("FROM \"AssinaturaSignatarios\"")) return pessoa;
      if (sql.contains("FROM \"Assinaturas\"")) return Map.of(
        "AssinaturaId",
        17
      );
      return null;
    });
    return new ServicoAssineai(
      banco,
      mock(IdentidadeAssineai.class),
      new ObjectMapper(),
      mock(ValidadorPedidoAssinatura.class),
      new ServicoCamposPdf(),
      new PdfAssineai(new ServicoCamposPdf()),
      mock(ServicoAssinaturaPdf.class),
      entrega,
      "",
      ""
    );
  }

  @Test
  void envioAntigoSemCanalUsaContatoMesmoComOpcaoTela() {
    var s = servico();
    s.enviarCodigoLegado("token-original-preservado", "127.0.0.1", "Navegador");
    var codigo = ArgumentCaptor.forClass(String.class);
    verify(entrega).enviar(
      eq("email"),
      eq("legada@example.com"),
      eq("codigo"),
      codigo.capture()
    );
    assertTrue(codigo.getValue().matches("[0-9]{6}"));
  }

  @Test
  void apiNovaContinuaRespeitandoCanaisConfigurados() {
    var s = servico();
    assertThrows(ResponseStatusException.class, () ->
      s.enviarCodigo("token-original-preservado", "email", null, null)
    );
    verifyNoInteractions(entrega);
  }

  @Test
  void codigoEmitidoAntesDaFirmaContinuaValido() {
    var s = servico();
    assertTrue(
      s.verificarCodigo("token-original-preservado", "123456", null, null, null)
    );
    verify(banco).executar(
      contains("INSERT INTO firma_assineai_codigo"),
      argThat(p -> Boolean.TRUE.equals(p.get("v")))
    );
    assertEquals("123456", pessoa.get("SignatarioTokenEmail"));
  }

  @Test
  void verificacaoConfirmadaPodeSerReutilizadaPelaRotaAntiga()
    throws Exception {
    var s = servico();
    String hash = HexFormat.of().formatHex(
      java.security.MessageDigest.getInstance("SHA-256").digest(
        "91:email:123456".getBytes(java.nio.charset.StandardCharsets.UTF_8)
      )
    );
    when(banco.um(contains("SELECT *,expira_em"), anyMap())).thenReturn(
      Map.of("ativo", true, "verificado", true, "tentativas", 5, "resumo", hash)
    );
    assertTrue(
      s.verificarCodigo("token-original-preservado", "123456", null, null, null)
    );
    verify(banco, never()).executar(
      contains("UPDATE firma_assineai_codigo"),
      anyMap()
    );
  }
}
