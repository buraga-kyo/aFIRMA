package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class NostrEmpresaTest {
  @Test
  void semArquivoDeIdentidadesAPublicacaoFicaDesativada() {
    var json = JsonMapper.builder().build();
    var perfis =
        new PerfisNostrConfigurados(json, new MockEnvironment(), new CriptografiaNostr(json));
    assertFalse(perfis.configurado());
    assertThrows(IllegalArgumentException.class, () -> perfis.buscar(1, "ref"));
  }

  @Test
  void rpcRemotoExigeHttpsERecusaCredencialNaUrl() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConexoesNostr.rpcSeguro("http://host-remoto.example"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ConexoesNostr.rpcSeguro("https://usuario:senha@host.example"));
  }

  @Test
  void naoAceitaChavePrivadaNoCampoDeReferencia() {
    var banco = mock(JdbcTemplate.class);
    var publicacoes = mock(ServicoPublicacaoNostr.class);
    var s = new ServicoNostrEmpresa(banco, publicacoes, JsonMapper.builder().build());
    assertThrows(
        ResponseStatusException.class,
        () ->
            s.salvar(
                1, "ator", "bunker://segredo", "Firma", "", true, ServicoNostrEmpresa.AUTORIZACAO));
    assertThrows(
        ResponseStatusException.class,
        () -> s.salvar(1, "ator", "ref", "Firma", "", true, "qualquer texto"));
    verifyNoInteractions(banco, publicacoes);
  }

  @Test
  void cadastroEPausaExigemAdministradorDaEmpresa() {
    var identidade = mock(IdentidadeAssineai.class);
    var s = mock(ServicoNostrEmpresa.class);
    var r = new MockHttpServletRequest();
    when(identidade.autenticar(r)).thenReturn("ator");
    when(identidade.empresaParaAdministrar("ator"))
        .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    var c = new ControleNostrEmpresa(identidade, s);
    assertThrows(
        ResponseStatusException.class,
        () ->
            c.salvar(
                new ControleNostrEmpresa.Configuracao(
                    "ref", "Firma", "", true, ServicoNostrEmpresa.AUTORIZACAO),
                r));
    assertThrows(ResponseStatusException.class, () -> c.pausar(r));
    verifyNoInteractions(s);
  }

  @Test
  void downloadsUsamEmpresaDaSessaoNuncaParametroDoCliente() {
    var identidade = mock(IdentidadeAssineai.class);
    var s = mock(ServicoNostrEmpresa.class);
    var r = new MockHttpServletRequest();
    when(identidade.autenticar(r)).thenReturn("ator");
    when(identidade.empresa("ator")).thenReturn(Map.of("EmpresaId", 7L));
    UUID id = UUID.randomUUID();
    when(s.evento(7, id)).thenReturn("evento".getBytes());
    new ControleNostrEmpresa(identidade, s).evento(id, r);
    verify(s).evento(7, id);
  }
}
