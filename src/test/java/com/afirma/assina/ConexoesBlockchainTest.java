package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ConexoesBlockchainTest {
  @Test
  void cofreRecusaTrocaDeEmpresaChaveEConteudo() {
    var json = JsonMapper.builder().build();
    var ambiente =
        new MockEnvironment()
            .withProperty(
                "ASSINEAI_SEGREDO_EVIDENCIAS",
                "segredo-local-de-testes-blockchain-com-32-caracteres");
    var cofre =
        new ConfiguracaoBlockchainEmpresa(
            mock(JdbcTemplate.class),
            json,
            new SegredosAssinatura(ambiente),
            new CriptografiaNostr(json),
            ambiente);
    byte[] texto = "bunker://credencial-privada".getBytes(StandardCharsets.UTF_8);
    byte[] protegido = cofre.cifrar(true, 1, "principal", texto);
    assertArrayEquals(texto, cofre.cifrar(false, 1, "principal", protegido));
    assertFalse(Arrays.equals(protegido, cofre.cifrar(true, 1, "principal", texto)));
    assertThrows(IllegalStateException.class, () -> cofre.cifrar(false, 2, "principal", protegido));
    protegido[14] ^= 1;
    assertThrows(IllegalStateException.class, () -> cofre.cifrar(false, 1, "principal", protegido));
    assertThrows(
        ResponseStatusException.class, () -> cofre.cifrar(false, 1, "chave-ausente", protegido));
  }

  @Test
  void empresasNaoPodemUsarFormularioParaAcessarRedeInterna() {
    for (String endereco :
        List.of(
            "https://127.0.0.1",
            "https://10.1.2.3",
            "https://169.254.169.254",
            "https://[::1]",
            "https://[fd00::1]",
            "https://100.64.0.1")) {
      assertThrows(
          ResponseStatusException.class,
          () -> ConfiguracaoBlockchainEmpresa.conferirPublico(URI.create(endereco)));
    }
  }

  @Test
  void todasAsOperacoesExigemAdministradorAntesDeLerCredenciaisOuConsultarRede() {
    var identidade = mock(IdentidadeAssineai.class);
    var config = mock(ConfiguracaoBlockchainEmpresa.class);
    var carimbo = mock(CarimboNostr.class);
    var publicacoes = mock(ServicoPublicacaoNostr.class);
    var r = new MockHttpServletRequest();
    when(identidade.autenticar(r)).thenReturn("ator");
    when(identidade.empresaParaAdministrar("ator"))
        .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    var c = new ControleConexoesBlockchain(identidade, config, carimbo, publicacoes);
    assertThrows(ResponseStatusException.class, () -> c.estado(r));
    assertThrows(
        ResponseStatusException.class,
        () -> c.nostr(new ControleConexoesBlockchain.Nostr("ref", "pub", "segredo", List.of()), r));
    assertThrows(
        ResponseStatusException.class,
        () ->
            c.bitcoin(new ControleConexoesBlockchain.Bitcoin("rpc", "usuario", "senha", false), r));
    assertThrows(ResponseStatusException.class, () -> c.testarBitcoin(r));
    assertThrows(
        ResponseStatusException.class,
        () -> c.testarNostr(new ControleConexoesBlockchain.Teste("ref"), r));
    verifyNoInteractions(config, carimbo, publicacoes);
  }

  @Test
  void acessoUsaEmpresaDaSessaoERespostaNaoPermiteCache() {
    var identidade = mock(IdentidadeAssineai.class);
    var config = mock(ConfiguracaoBlockchainEmpresa.class);
    var r = new MockHttpServletRequest();
    when(identidade.autenticar(r)).thenReturn("admin");
    when(identidade.empresaParaAdministrar("admin")).thenReturn(Map.of("EmpresaId", 9L));
    when(config.estado(9)).thenReturn(Map.of("cofreDisponivel", true));
    var resposta =
        new ControleConexoesBlockchain(
                identidade, config, mock(CarimboNostr.class), mock(ServicoPublicacaoNostr.class))
            .estado(r);
    assertEquals("no-store", resposta.getHeaders().getCacheControl());
    verify(config).estado(9);
    assertFalse(
        new ControleConexoesBlockchain.Nostr("ref", "pub", "segredo-nao-logar", List.of())
            .toString()
            .contains("segredo-nao-logar"));
    assertFalse(
        new ControleConexoesBlockchain.Bitcoin("rpc", "usuario", "senha-nao-logar", false)
            .toString()
            .contains("senha-nao-logar"));
  }
  @Test void bitcoinUsaCredenciaisDaEmpresaSemMisturarComPadraoDaInstalacao() throws Exception {
    var json=JsonMapper.builder().build();var rede=mock(ConexoesNostr.class);
    var configuracao=mock(ConfiguracaoBlockchainEmpresa.class);
    when(configuracao.bitcoin(7)).thenReturn(new ConfiguracaoBlockchainEmpresa.Bitcoin("http://127.0.0.1:18443","empresa7","senha-empresa7"));
    when(rede.buscar(any(),any(),anyMap(),eq(65536))).thenReturn(json.writeValueAsBytes(Map.of("result",Map.of("chain","main","initialblockdownload",false,"blocks",100))));
    var ambiente=new MockEnvironment().withProperty("APP_BITCOIN_RPC_URL","http://127.0.0.1:18444").withProperty("APP_BITCOIN_RPC_USER","padrao").withProperty("APP_BITCOIN_RPC_PASSWORD","senha-padrao");
    var carimbo=new CarimboNostr(rede,json,new CriptografiaNostr(json),ambiente);carimbo.usarConexoes(configuracao);
    assertEquals("main",carimbo.testarBitcoin(7).get("rede"));
    String autorizacao="Basic "+Base64.getEncoder().encodeToString("empresa7:senha-empresa7".getBytes(StandardCharsets.UTF_8));
    verify(rede).buscar(eq(URI.create("http://127.0.0.1:18443")),any(),eq(Map.of("Content-Type","application/json","Authorization",autorizacao)),eq(65536));
    verify(configuracao).bitcoin(7);verifyNoMoreInteractions(configuracao);
  }

}
