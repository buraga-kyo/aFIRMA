package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class ReenvioCodigoConviteTest {
  @Test void apiRecusaReenvioEDivulgaEsperaPersistida() throws Exception {
    var banco = mock(JdbcTemplate.class);
    var enviador = mock(EnviadorCodigoConvite.class);
    var servico = new ServicoAutenticacaoConvites(banco, mock(SegredosAssinatura.class), enviador,
        mock(ServicoEvidenciasAssinatura.class));
    var identificadorSignatario = UUID.randomUUID();
    var identificadorEnvelope = UUID.randomUUID();
    var ultimoEnvio = Instant.now();
    when(enviador.configurado()).thenReturn(true);
    doAnswer(chamada -> {
      String consulta = chamada.getArgument(0);
      RowMapper<?> mapeador = chamada.getArgument(1);
      var linha = mock(ResultSet.class);
      if (consulta.contains("FROM signatario_envelope")) {
        when(linha.getObject(1, UUID.class)).thenReturn(identificadorSignatario);
        when(linha.getObject(2, UUID.class)).thenReturn(identificadorEnvelope);
        when(linha.getString(3)).thenReturn("pessoa@exemplo.com");
        when(linha.getString(4)).thenReturn("Contrato");
        when(linha.getString(9)).thenReturn("Empresa do convite");
        when(linha.getTimestamp(5)).thenReturn(Timestamp.from(Instant.now().plusSeconds(3600)));
      } else {
        when(linha.getTimestamp(1)).thenReturn(Timestamp.from(ultimoEnvio));
        when(linha.getTimestamp(3)).thenReturn(Timestamp.from(ultimoEnvio));
        when(linha.getInt(4)).thenReturn(1);
      }
      return List.of(mapeador.mapRow(linha, 0));
    }).when(banco).query(anyString(), any(RowMapper.class), any(Object.class));
    when(banco.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
    when(banco.queryForList(anyString(), any(Object.class))).thenReturn(List.of(Map.of(
        "ultimo_envio_em", Timestamp.from(ultimoEnvio), "janela_envios_em", Timestamp.from(ultimoEnvio), "quantidade_envios", 1)));
    String tokenConvite = "A".repeat(43);
    var acesso = servico.consultarAcesso(tokenConvite, null);
    assertTrue(acesso.segundosParaReenvio() > 0 && acesso.segundosParaReenvio() <= 60);
    assertEquals(429, assertThrows(ResponseStatusException.class, () -> servico.solicitarCodigo(tokenConvite)).getStatusCode().value());
    verify(enviador, never()).enviar(anyString(), anyString(), anyString());
    assertTrue(servico.consultarAcesso(tokenConvite, null).segundosParaReenvio() > 0);
  }
}
