package com.afirma.assina;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RespostaMotorChatFirmaTest {
  @Test void respostaPassaPelaFilaDoAtendimentoComIdentificacaoEstavel()throws Exception {
    var banco=mock(BancoAssineai.class);var envios=mock(DisparosFirma.class);UUID canal=UUID.randomUUID();
    when(banco.um(anyString(),anyMap())).thenReturn(Map.of("empresa_id",1,"cliente_id",2,"repositorio_id",3,"canal_id",4,"atendimento_id",5));
    when(envios.avulso(anyMap(),any(),anyString())).thenReturn(Map.of("estado","ENVIADO"));
    new RespostaMotorChatFirma(banco,envios).receber(canal,10,new ObjectMapper().readTree("{\"id\":123}"),"5511999990000","Olá");
    verify(envios).avulso(Map.of("CanalId",4,"AtendimentoId",5,"Destinatario","5511999990000","texto","Olá","origem","MOTOR_CHAT"),new ServicoFirma.Contexto(1,2,3,0),"motor-chat:10:123");
  }
  @Test void motorNaoCriaAtendimentoNemEnviaQuandoNaoHaAtendimentoAberto()throws Exception {
    var banco=mock(BancoAssineai.class);var envios=mock(DisparosFirma.class);
    when(banco.um(anyString(),anyMap())).thenReturn(null);
    var evento=new ObjectMapper().readTree("{\"id\":123}");
    assertThrows(ResponseStatusException.class,()->new RespostaMotorChatFirma(banco,envios).receber(UUID.randomUUID(),10,evento,"5511999990000","Olá"));
    verifyNoInteractions(envios);
  }
  @Test void entregaIncertaNaoEApresentadaComoConfirmacao()throws Exception {
    var banco=mock(BancoAssineai.class);var envios=mock(DisparosFirma.class);
    when(banco.um(anyString(),anyMap())).thenReturn(Map.of("empresa_id",1,"cliente_id",2,"repositorio_id",3,"canal_id",4,"atendimento_id",5));
    when(envios.avulso(anyMap(),any(),anyString())).thenReturn(Map.of("estado","INCERTO"));
    var evento=new ObjectMapper().readTree("{\"id\":123}");
    assertThrows(ResponseStatusException.class,()->new RespostaMotorChatFirma(banco,envios).receber(UUID.randomUUID(),10,evento,"5511999990000","Olá"));
  }
}
