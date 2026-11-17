package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "AFIRMA_NOSTR_TEST_URL", matches = ".+")
class NostrBancoTest {
  @Test
  void filaMantemProvasIsolaEmpresasEPreservaAnterioridadeAposPausa() {
    var ds =
        new DriverManagerDataSource(
            System.getenv("AFIRMA_NOSTR_TEST_URL"),
            System.getenv("AFIRMA_NOSTR_TEST_USER"),
            System.getenv("AFIRMA_NOSTR_TEST_PASSWORD"));
    var banco = new JdbcTemplate(ds);
    var json = JsonMapper.builder().build();
    banco.execute("CREATE TABLE \"Empresas\"(\"EmpresaId\" bigint primary key)");
    banco.update("INSERT INTO \"Empresas\" VALUES(1),(2)");
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V59__identidade_e_publicacoes_nostr.sql"),
            new ClassPathResource("db/migration/V60__limite_assinador_nostr.sql"),
            new ClassPathResource("db/migration/V61__configuracao_blockchain_empresa.sql"))
        .execute(ds);
    var publicacoes = mock(ServicoPublicacaoNostr.class);
    when(publicacoes.configurado(anyLong())).thenReturn(true);
    String chave = "11".repeat(32), evento = "aa".repeat(32);
    when(publicacoes.conferirIdentidade(eq(1L), eq("ref")))
        .thenReturn(Map.of("chavePublica", chave));
    when(publicacoes.conferirAceite(eq(1L), eq("ref"), anyMap())).thenReturn(evento);
    when(publicacoes.assinar(anyMap()))
        .thenAnswer(i -> {
          Map<?, ?> p = i.getArgument(0);
          String id = p.get("tipo").equals("PERFIL") ? "bb".repeat(32) : "cc".repeat(32);
          return Map.of(
              "eventoCanonico", json.writeValueAsString(Map.of("pubkey", chave, "id", id)));
        });
    when(publicacoes.publicar(anyMap()))
        .thenReturn(
            Map.of(
                "relays",
                List.of(
                    Map.of("url", "wss://relay-a.example", "aceito", true),
                    Map.of("url", "wss://relay-b.example", "aceito", true))));
    when(publicacoes.carimbar(anyMap()))
        .thenReturn(
            Map.of(
                "estado",
                "PENDENTE",
                "ots",
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
    var s = new ServicoNostrEmpresa(banco, publicacoes, json);
    var processador = new ProcessadorPublicacoesNostr(banco, publicacoes, json);
    s.salvar(1, "admin", "ref", "Firma", "Perfil público", true, ServicoNostrEmpresa.AUTORIZACAO);
    UUID id = s.enfileirar(1, "operador", Map.of("id", evento));
    assertEquals(id, s.enfileirar(1, "operador", Map.of("id", evento)));
    assertThrows(ResponseStatusException.class, () -> s.obter(2, id));
    processador.processar();
    processador.processar();
    var p = s.obter(1, id);
    assertEquals("PUBLICADO", p.get("publicacao_estado"));
    assertEquals("PENDENTE", p.get("anterioridade_estado"));
    assertArrayEquals(new byte[] {1, 2, 3}, s.ots(1, id));
    var pacote = s.pacote(1, id);
    assertTrue(pacote.containsKey("pedidoPrivado"));
    assertEquals(2, ((List<?>) s.listar(1).getFirst().get("relays")).size());
    s.pausar(1, "admin");
    assertEquals(false, s.estado(1).get("ativa"));
    assertThrows(
        ResponseStatusException.class,
        () -> s.enfileirar(1, "operador", Map.of("id", "dd".repeat(32))));
    clearInvocations(publicacoes);
    when(publicacoes.carimbar(anyMap()))
        .thenReturn(
            Map.of(
                "estado",
                "CONFIRMADA",
                "ots",
                Base64.getEncoder().encodeToString(new byte[] {4, 5}),
                "bloco",
                "ee".repeat(32),
                "altura",
                100L,
                "data",
                1700000000L));
    banco.update("UPDATE firma_nostr_publicacao SET proxima_tentativa=now() WHERE id=?", id);
    processador.processar();
    verify(publicacoes, never()).publicar(anyMap());
    verify(publicacoes).carimbar(anyMap());
    assertEquals("CONFIRMADA", s.obter(1, id).get("anterioridade_estado"));
    assertEquals(
        2, banco.queryForObject("SELECT count(*) FROM firma_nostr_auditoria", Integer.class));
    var limite = new LimiteAssinadorNostr(banco);
    for (int tentativa = 0; tentativa < 100; tentativa++) limite.reservar(1, "teste-limite");
    assertThrows(
        IllegalStateException.class,
        () -> new LimiteAssinadorNostr(banco).reservar(1, "teste-limite"));
    new LimiteAssinadorNostr(banco).reservar(2, "teste-limite");
    var ambiente =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty(
                "ASSINEAI_SEGREDO_EVIDENCIAS", "segredo-de-testes-para-cofre-blockchain-2026");
    var cripto = new CriptografiaNostr(json);
    var conexoes =
        new ConfiguracaoBlockchainEmpresa(
            banco, json, new SegredosAssinatura(ambiente), cripto, ambiente);
    String bunker =
        "bunker://" + "22".repeat(32) + "?relay=wss%3A%2F%2F8.8.8.8&secret=segredo-do-pareamento";
    banco.update("UPDATE firma_nostr_configuracao SET ativa=true WHERE empresa_id=1");
    var estado =
        conexoes.salvarNostr(
            1, "admin", "firma-painel", chave, bunker, List.of("wss://8.8.8.8", "wss://1.1.1.1"));
    String publico = json.writeValueAsString(estado);
    assertFalse(publico.contains("segredo-do-pareamento"));
    assertFalse(publico.contains("chaveCliente\""));
    assertTrue(((Map<?, ?>) conexoes.estado(2).get("nostr")).isEmpty());
    byte[] protegido =
        banco.queryForObject(
            "SELECT conteudo FROM firma_blockchain_conexao WHERE empresa_id=1", byte[].class);
    assertFalse(
        new String(protegido, java.nio.charset.StandardCharsets.UTF_8)
            .contains("segredo-do-pareamento"));
    String cliente = ((Map<?, ?>) estado.get("nostr")).get("chaveClientePublica").toString();
    var alterado =
        conexoes.salvarNostr(
            1, "admin", "firma-painel", chave, "", List.of("wss://8.8.8.8", "wss://1.1.1.1"));
    assertEquals(cliente, ((Map<?, ?>) alterado.get("nostr")).get("chaveClientePublica"));
    assertEquals("segredo-do-pareamento", conexoes.perfil(1).segredoPareamento);
    conexoes.salvarBitcoin(1, "admin", "https://8.8.8.8", "rpc-teste", "senha-rpc-privada", false);
    conexoes.salvarBitcoin(1, "admin", "https://8.8.8.8", "rpc-teste", "", false);
    assertEquals("senha-rpc-privada", conexoes.bitcoin(1).senha);
    assertThrows(
        ResponseStatusException.class,
        () -> conexoes.salvarBitcoin(1, "admin", "https://1.1.1.1", "rpc-teste", "", false));
    assertFalse(json.writeValueAsString(conexoes.estado(1)).contains("senha-rpc-privada"));
    assertNull(conexoes.bitcoin(2));
    var perfis = new PerfisNostrConfigurados(json, ambiente, cripto);
    perfis.usarConexoes(conexoes);
    assertTrue(perfis.configurado(1));
    assertFalse(perfis.configurado(2));
    assertEquals(cliente, cripto.chavePublica(perfis.buscar(1, "firma-painel").chaveCliente()));
    assertFalse(
        (Boolean)
            banco.queryForObject(
                "SELECT ativa FROM firma_nostr_configuracao WHERE empresa_id=1", Boolean.class));
    conexoes.salvarBitcoin(1, "admin", null, null, null, true);
    assertNull(conexoes.bitcoin(1));
  }
}
