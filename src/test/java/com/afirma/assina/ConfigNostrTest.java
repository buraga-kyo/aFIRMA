package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class ConfigNostrTest {
  @TempDir Path pasta;

  @Test
  void preparaCredencialPrivadaSemTrocarChaveExistente() throws Exception {
    var arquivo = pasta.resolve("nostr.json");
    Files.writeString(
        arquivo,
        """
        {"identidades":[{"empresaId":1,"referencia":"firma","chavePublica":"%s","chaveCliente":"","bunker":"bunker://%s?relay=wss%%3A%%2F%%2Fassinador.example","relays":["wss://a.example","wss://b.example"]}]}
        """
            .formatted("11".repeat(32), "22".repeat(32)));
    PrepararConfigNostr.preparar(arquivo);
    String primeira = Files.readString(arquivo);
    assertEquals(
        PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(arquivo));
    PrepararConfigNostr.preparar(arquivo);
    assertEquals(primeira, Files.readString(arquivo));
    var json = JsonMapper.builder().build();
    var perfis =
        new PerfisNostrConfigurados(
            json,
            new MockEnvironment().withProperty("APP_NOSTR_CONFIG", arquivo.toString()),
            new CriptografiaNostr(json));
    assertEquals(1, perfis.listar(1).size());
    assertTrue(perfis.listar(2).isEmpty());
    assertFalse(perfis.buscar(1, "firma").toString().contains("chaveCliente"));
    Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rw-r--r--"));
    assertThrows(
        IllegalStateException.class,
        () ->
            new PerfisNostrConfigurados(
                json,
                new MockEnvironment().withProperty("APP_NOSTR_CONFIG", arquivo.toString()),
                new CriptografiaNostr(json)));
  }

  @Test
  void relaysRecusamCredenciaisEConexaoSemTls() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PerfisNostrConfigurados.relaySeguro("ws://relay.example"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PerfisNostrConfigurados.relaySeguro("wss://segredo@relay.example"));
    assertEquals(
        "wss://relay.example/?grupo=1",
        PerfisNostrConfigurados.relaySeguro("wss://relay.example?grupo=1").toString());
  }
}
