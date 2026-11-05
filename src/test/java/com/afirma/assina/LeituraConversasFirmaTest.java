package com.afirma.assina;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LeituraConversasFirmaTest {
  private final BancoAssineai banco=mock(BancoAssineai.class);
  private final ServicoFirma dados=mock(ServicoFirma.class);
  private final EspelhoFirma espelho=mock(EspelhoFirma.class);
  private final ChatTransporte transporte=mock(ChatTransporte.class);
  private final ServicoFirma.Contexto contexto=new ServicoFirma.Contexto(1,2,3,4);
  private final UUID canal=UUID.randomUUID();
  private final Map<String,Object> atendimento=Map.of("id",10L,"CanalId",20L,"ContatoId",30L,"AtendimentoStatus","pendente");

  private OperacaoFirma operacao() {
    when(dados.registro("Atendimento",10,contexto)).thenReturn(atendimento);
    when(banco.um(contains("FOR UPDATE"),anyMap())).thenReturn(atendimento);
    when(dados.registro("Canal",20,contexto)).thenReturn(Map.of("CanalGUID",canal));
    when(dados.registro("Contato",30,contexto)).thenReturn(Map.of("ContatoNumero","5511999990000"));
    when(banco.listar(contains("MensagemIdWhatsApp"),anyMap())).thenReturn(List.of(
        Map.of("id",100L,"MensagemIdWhatsApp","recebida-1"),
        Map.of("id",101L,"MensagemIdWhatsApp","recebida-2")));
    return new OperacaoFirma(dados,banco,espelho,transporte,mock(ArquivosFirma.class),mock(ServicoCanalOficial.class),mock(FirmaPersistencia.class));
  }

  @Test void confirmaTodasAsMensagensAntesDeZerarNaoLidas() {
    operacao().acao(10,Map.of("acao","lidas"),contexto,"operador");
    var ordem=inOrder(transporte,banco);
    ordem.verify(transporte).mensagem(canal,"5511999990000","recebida-1",false,"lida");
    ordem.verify(banco).executar(contains("SET \"MensagemACK\"=3"),anyMap());
    ordem.verify(transporte).mensagem(canal,"5511999990000","recebida-2",false,"lida");
    ordem.verify(banco).executar(contains("SET \"MensagemACK\"=3"),anyMap());
    ordem.verify(banco).executar(contains("SET \"AtendimentoQtdMensagensNaoLidas\"=0"),anyMap());
  }

  @Test void falhaNoProvedorPreservaIndicadorDeNaoLidas() {
    var operacao=operacao();
    doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY)).when(transporte)
        .mensagem(canal,"5511999990000","recebida-1",false,"lida");
    assertThrows(ResponseStatusException.class,()->operacao.acao(10,Map.of("acao","lidas"),contexto,"operador"));
    verify(banco,never()).executar(contains("SET \"AtendimentoQtdMensagensNaoLidas\"=0"),anyMap());
    verify(espelho,never()).salvar(anyString(),anyMap(),any());
  }

  @Test void acessoRecusadoNaoConfirmaMensagens() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(dados).autorizar(contexto,"atendimentos","read");
    assertThrows(ResponseStatusException.class,()->new OperacaoFirma(dados,banco,espelho,transporte,mock(ArquivosFirma.class),mock(ServicoCanalOficial.class),mock(FirmaPersistencia.class))
        .acao(10,Map.of("acao","lidas"),contexto,"operador"));
    verifyNoInteractions(transporte);
  }
}
