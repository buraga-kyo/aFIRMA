package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CanalOficialFirmaTest {
  private final NamedParameterJdbcTemplate banco=mock(NamedParameterJdbcTemplate.class);
  private final ChatServico chat=mock(ChatServico.class);
  private final EventosFirma eventos=mock(EventosFirma.class);
  private final ServicoBots bots=mock(ServicoBots.class);
  private final ServicoCanalOficial oficial=mock(ServicoCanalOficial.class);
  private final ServicoArquivosChat arquivos=mock(ServicoArquivosChat.class);
  private final UUID canal=UUID.randomUUID();
  private final String segredo="firma-meta-segredo-local-de-testes-2026";

  private ControleCanalOficial controle(){
    when(banco.queryForList(anyString(),any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of("id",canal)));
    return new ControleCanalOficial(banco,mock(EmpresaAtual.class),oficial,chat,new ObjectMapper(),arquivos,eventos,bots,segredo,segredo);
  }
  private byte[] corpo(String mensagem){return ("{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"1234567890\"},\"messages\":["+mensagem+"]}}]}]}").getBytes(StandardCharsets.UTF_8);}
  private String assinar(byte[] corpo)throws Exception{
    var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    return "sha256="+HexFormat.of().formatHex(mac.doFinal(corpo));
  }
  @Test void botRecebeTextoDaRespostaInterativaUmaVez()throws Exception{
    var controle=controle();when(chat.receber(canal,"resposta-1","5511999990000","","Financeiro")).thenReturn(true,false);
    byte[] corpo=corpo("""
        {"id":"resposta-1","from":"5511999990000","type":"interactive", "interactive":
          {"type":"button_reply","button_reply":{"id":"financeiro","title":"Financeiro"}}}
        """);
    controle.receber(assinar(corpo),corpo);controle.receber(assinar(corpo),corpo);
    verify(bots,times(1)).processarEntrada(canal,"5511999990000","Financeiro","resposta-1");
    verify(eventos,times(2)).metadados(any());
  }
  @Test void assinaturaInvalidaNaoAlteraMensagens(){
    var controle=controle();clearInvocations(banco);
    assertThrows(ResponseStatusException.class,()->controle.receber("sha256="+"0".repeat(64),corpo("{}")));
    verifyNoInteractions(chat,eventos,bots,arquivos,oficial);
    verifyNoInteractions(banco);
  }
  @Test void midiaChegaComoArquivoAntesDeExecutarBot()throws Exception{
    var controle=controle();when(oficial.baixarMidia(canal,"98765")).thenReturn(new ServicoArquivosChat.Arquivo("foto","image/jpeg",new byte[]{1,2}));
    when(arquivos.receber(eq(canal),eq("foto-1"),anyString(),anyString(),eq("Contrato"),anyString(),eq("image/jpeg"),anyString())).thenReturn(true);
    byte[] corpo=corpo("""
        {"id":"foto-1","from":"5511999990000","type":"image","image":{"id":"98765","caption":"Contrato"}}
        """);
    controle.receber(assinar(corpo),corpo);
    var ordem=inOrder(arquivos,eventos,bots);
    ordem.verify(arquivos).receber(eq(canal),eq("foto-1"),anyString(),anyString(),eq("Contrato"),anyString(),eq("image/jpeg"),anyString());
    ordem.verify(eventos).metadados(any());
    ordem.verify(bots).processarEntrada(canal,"5511999990000","Contrato","foto-1");
  }
}
