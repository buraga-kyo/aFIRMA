package com.afirma.assina;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** O filtro e a API consultam a mesma chave para conferir a empresa e a revogação. */
@Service
public class ChavesIntegracaoFirma {
  private final BancoAssineai banco;

  public ChavesIntegracaoFirma(BancoAssineai banco) {
    this.banco = banco;
  }

  public ServicoFirma.Contexto autenticar(HttpServletRequest requisicao) {
    String chave =
        Objects.toString(requisicao.getHeader("Authorization"), "").replaceFirst("^Bearer +", "");
    if (!chave.matches(
        "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chave de API inválida");
    var vinculo =
        banco.um(
            "SELECT e.* FROM firma.\"ChaveAPI\" k JOIN firma.\"Cliente\" c ON c.id=k.\"ClienteId\" JOIN firma_relacionamento_empresa e ON e.cliente_id=c.id WHERE k.\"ChaveAPIValor\"=CAST(:k AS uuid) AND k.\"ChaveAPIStatus\" AND COALESCE(c.\"ClienteStatus\",true)",
            Map.of("k", chave));
    if (vinculo == null)
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Chave revogada ou empresa desativada");
    return new ServicoFirma.Contexto(
        ((Number) vinculo.get("empresa_id")).longValue(),
        ((Number) vinculo.get("cliente_id")).longValue(),
        ((Number) vinculo.get("repositorio_id")).longValue(),
        0);
  }
}
