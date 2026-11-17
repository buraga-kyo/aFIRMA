package com.afirma.assina;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

final class PrepararConfigNostr {
  @SuppressWarnings("unchecked")
  static void preparar(Path arquivo) throws Exception {
    if (!arquivo.isAbsolute() || Files.size(arquivo) > 65536) throw new IllegalArgumentException();
    var json = JsonMapper.builder().build();
    var criptografia = new CriptografiaNostr(json);
    Map<String, Object> config = json.readValue(Files.readString(arquivo), Map.class);
    if (!(config.get("identidades") instanceof List<?> identidades))
      throw new IllegalArgumentException();
    for (var item : identidades) {
      var identidade = (Map<String, Object>) item;
      if (identidade.get("chaveCliente") == null
          || identidade.get("chaveCliente").toString().isBlank()) {
        byte[] chave = criptografia.criarChaveCliente();
        try {
          identidade.put("chaveCliente", CriptografiaNostr.hex(chave));
        } finally {
          Arrays.fill(chave, (byte) 0);
        }
      }
    }
    var perfis = PerfisNostrConfigurados.ler(config, criptografia);
    Path temporario =
        Files.createTempFile(
            arquivo.getParent(),
            ".nostr-",
            ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try {
      Files.writeString(
          temporario, json.writerWithDefaultPrettyPrinter().writeValueAsString(config) + "\n");
      Files.move(
          temporario, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporario);
    }
    for (var perfil : perfis) {
      byte[] chave = perfil.chaveCliente();
      try {
        System.out.println(
            perfil.referencia
                + ": chave pública do cliente de automação "
                + criptografia.chavePublica(chave));
      } finally {
        Arrays.fill(chave, (byte) 0);
      }
    }
  }
}
