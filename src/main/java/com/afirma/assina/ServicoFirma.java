package com.afirma.assina;

import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

/** Os controladores passam por aqui para consultar cadastros, conferir permissões e aplicar RegrasFirma. */
@Service
public class ServicoFirma {
  public record Contexto(long empresa, long cliente, long repositorio, long usuario) {}

  private final BancoAssineai banco;
  private final NamedParameterJdbcTemplate consultasSql;
  private final IdentidadeAssineai identidades;
  private final EspelhoFirma espelho;
  private final ObjectMapper json;
  private final RegrasFirma regras;
  private final List<Map<String, Object>> modulos, tabelas, relacoes;
  private final Map<String, Object> carga;

  @SuppressWarnings("unchecked")
  public ServicoFirma(
      BancoAssineai banco,
      NamedParameterJdbcTemplate consultasSql,
      IdentidadeAssineai identidades,
      ObjectMapper json,
      EspelhoFirma espelho,
      RegrasFirma regras)
      throws Exception {
    this.regras = regras;
    this.banco = banco;
    this.consultasSql = consultasSql;
    this.identidades = identidades;
    this.json = json;
    this.espelho = espelho;
    var esquema =
        json.readValue(
            new ClassPathResource("relacionamento/esquema.json").getInputStream(), Map.class);
    tabelas = (List<Map<String, Object>>) esquema.get("tabelas");
    relacoes = (List<Map<String, Object>>) esquema.get("relacionamentos");
    carga =
        json.readValue(
            new ClassPathResource("relacionamento/carga-inicial.json").getInputStream(), Map.class);
    modulos =
        json.readValue(
            new ClassPathResource("relacionamento/modulos.json").getInputStream(), List.class);
  }

  @Transactional
  public Contexto contexto(String identidade) {
    var empresa = identidades.empresa(identidade);
    long id = BancoAssineai.id(empresa, "EmpresaId");
    // Duas abas podem abrir juntas. O bloqueio evita criar duas vezes o vínculo da empresa.
    banco.um("SELECT pg_advisory_xact_lock(:id)", Map.of("id", id));
    var vinculo =
        banco.um(
            "SELECT * FROM firma_relacionamento_empresa WHERE empresa_id=:id", Map.of("id", id));
    if (vinculo == null) {
      var parametros = new HashMap<String, Object>();
      parametros.put(
          "nome",
          Objects.toString(
              empresa.get("EmpresaNome"),
              Objects.toString(empresa.get("EmpresaUsuario"), "Empresa")));
      parametros.put("email", empresa.get("EmpresaEmail"));
      parametros.put("doc", empresa.get("EmpresaCNPJ"));
      long cliente =
          ((Number)
                  banco
                      .um(
                          "INSERT INTO firma.\"Cliente\"(\"ClienteNome\",\"ClienteEmail\",\"ClienteCpfCnpj\",\"ClienteStatus\",\"createdAt\",\"updatedAt\") VALUES(:nome,:email,:doc,true,now(),now()) RETURNING id",
                          parametros)
                      .get("id"))
              .longValue();
      long repo =
          ((Number)
                  banco
                      .um(
                          "INSERT INTO firma_identidade.\"Repositorio\"(\"RepositorioNome\",\"createdAt\",\"updatedAt\") VALUES(:nome,now(),now()) RETURNING id",
                          parametros)
                      .get("id"))
              .longValue();
      banco.executar(
          "INSERT INTO firma_relacionamento_empresa VALUES(:e,:c,:r)",
          Map.of("e", id, "c", cliente, "r", repo));
      return preparar(
          new Contexto(
              id,
              cliente,
              repo,
              identidade.startsWith("firma:") ? Long.parseLong(identidade.substring(6)) : 0));
    }
    return preparar(
        new Contexto(
            id,
            ((Number) vinculo.get("cliente_id")).longValue(),
            ((Number) vinculo.get("repositorio_id")).longValue(),
            identidade.startsWith("firma:") ? Long.parseLong(identidade.substring(6)) : 0));
  }

  @SuppressWarnings("unchecked")
  private Contexto preparar(Contexto contextoAtual) {
    if (((Number)
                banco
                    .um(
                        "SELECT count(*) n FROM firma_identidade.\"Perfil\" WHERE \"RepositorioId\"=:r",
                        Map.of("r", contextoAtual.repositorio()))
                    .get("n"))
            .longValue()
        > 0) return contextoAtual;
    var perfis = new HashMap<String, Object>();
    var permissoes = new HashMap<String, Object>();
    for (var parametros : (List<Map<String, Object>>) carga.get("perfis"))
      perfis.put(
          parametros.get("Nome").toString(),
          banco
              .um(
                  "INSERT INTO firma_identidade.\"Perfil\"(\"PerfilNome\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:n,:r,now(),now()) RETURNING id",
                  Map.of("n", parametros.get("Nome"), "r", contextoAtual.repositorio()))
              .get("id"));
    for (var parametros : (List<Map<String, Object>>) carga.get("permissoes"))
      permissoes.put(
          parametros.get("Nome") + ":" + parametros.get("Acao"),
          banco
              .um(
                  "INSERT INTO firma_identidade.\"Permissao\"(\"PermissaoNome\",\"PermissaoAcao\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:n,:a,:r,now(),now()) RETURNING id",
                  Map.of(
                      "n",
                      parametros.get("Nome"),
                      "a",
                      parametros.get("Acao"),
                      "r",
                      contextoAtual.repositorio()))
              .get("id"));
    for (var parametros : (List<Map<String, Object>>) carga.get("vinculos")) {
      Object perfil = perfis.get(parametros.get("PerfilNome")),
          permissao =
              permissoes.get(
                  parametros.get("PermissaoNome") + ":" + parametros.get("PermissaoAcao"));
      if (perfil != null && permissao != null)
        banco.executar(
            "INSERT INTO firma_identidade.\"PerfilPermissao\"(\"PerfilId\",\"PermissaoId\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:p,:a,:r,now(),now())",
            Map.of("p", perfil, "a", permissao, "r", contextoAtual.repositorio()));
    }
    return contextoAtual;
  }

  public Map<String, Object> modulo(String slug) {
    return modulos.stream()
        .filter(registroAtual -> registroAtual.get("slug").equals(slug))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private Map<String, Object> tabela(String nome) {
    return tabelas.stream()
        .filter(tabelaAtual -> tabelaAtual.get("nome").equals(nome))
        .findFirst()
        .orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> campos(String nome) {
    return (List<Map<String, Object>>) tabela(nome).get("colunas");
  }

  public String entreAspas(String identificador) {
    return "\"" + identificador + "\"";
  }

  private String nome(String tabela) {
    return entreAspas(this.tabela(tabela).get("schema").toString()) + "." + entreAspas(tabela);
  }

  private boolean segredo(String itemAtual) {
    return itemAtual.equals("IntegracaoDados")
        || itemAtual.matches(
            ".*(Senha|Token|JWT|Creds|APIKey|RandomBytes|RecSenha|MensagemRAW|KeyWhatsApp|ClienteMetaToken|ChaveAPIValor).*");
  }

  private boolean gravavelSegredo(String itemAtual) {
    return Set.of(
            "UsuarioSenha",
            "InteligenciaArtificialProvedorAPIKey",
            "ClienteMetaToken",
            "IntegracaoDados")
        .contains(itemAtual);
  }

  private boolean gerenciado(String itemAtual) {
    return Set.of(
                "ChaveAPIValor",
                "ChaveAPIResponsavelCriacao",
                "ChatbotDefinicaoJSON",
                "ChatbotDefinicaoMetadata",
                "MensagemTemplateMetaId",
                "MensagemTemplateStatusMeta",
                "MensagemTemplateMotivoRejeicaoMeta")
            .contains(itemAtual)
        || itemAtual.endsWith("GUID")
        || Set.of(
                "id",
                "ClienteId",
                "RepositorioId",
                "UsuarioClienteId",
                "createdAt",
                "updatedAt",
                "UsuarioNomeCompleto",
                "PlanoId",
                "ListaContatoQuantidadeContatos")
            .contains(itemAtual);
  }

  public String escopo(String nomeTabela, String apelidoTabela) {
    if (nomeTabela.equals("Cliente")) return apelidoTabela + ".id=:cliente";
    if (nomeTabela.equals("Plano")) return "FALSE";
    if (nomeTabela.equals("Mensagem"))
      return "EXISTS(SELECT 1 FROM firma.\"Canal\" ca WHERE ca.id="
          + apelidoTabela
          + ".\"CanalId\" AND ca.\"ClienteId\"=:cliente)";
    if (nomeTabela.equals("Repositorio")) return apelidoTabela + ".id=:repositorio";
    if (nomeTabela.equals("Usuario"))
      return apelidoTabela
          + ".\"UsuarioClienteId\"=:cliente AND "
          + apelidoTabela
          + ".\"RepositorioId\"=:repositorio";
    if (tabela(nomeTabela).get("schema").equals("firma_identidade"))
      return apelidoTabela + ".\"RepositorioId\"=:repositorio";
    if (campos(nomeTabela).stream()
        .anyMatch(itemAtual -> itemAtual.get("nome").equals("ClienteId")))
      return apelidoTabela + ".\"ClienteId\"=:cliente";
    String via =
        switch (nomeTabela) {
          case "Setor", "ChatbotDefinicao", "ChatbotMetadata" -> "Canal";
          case "Mensagem", "AtendimentoAcao", "AtendimentoTag" -> "Atendimento";
          case "AtendenteSetor", "FCMToken" -> "Atendente";
          default -> null;
        };
    String direta =
        via == null
            ? "FALSE"
            : "EXISTS(SELECT 1 FROM firma."
                + entreAspas(via)
                + " v WHERE v.id="
                + apelidoTabela
                + "."
                + entreAspas(via + "Id")
                + " AND v.\"ClienteId\"=:cliente)";
    return "("
        + direta
        + " OR EXISTS(SELECT 1 FROM firma_relacionamento_vinculo o WHERE o.destino='"
        + nomeTabela
        + "' AND o.destino_id="
        + apelidoTabela
        + ".id AND o.cliente_id=:cliente))";
  }

  private Map<String, Object> params(Contexto contextoAtual) {
    return new HashMap<>(
        Map.of(
            "cliente",
            contextoAtual.cliente(),
            "repositorio",
            contextoAtual.repositorio(),
            "usuario",
            contextoAtual.usuario()));
  }

  public String escopo(String nomeTabela, String apelidoTabela, Contexto contextoAtual) {
    String base = escopo(nomeTabela, apelidoTabela);
    if (contextoAtual.usuario() == 0 || pode(contextoAtual, "atendenteww", "read")) return base;
    String operador =
        "SELECT at.id FROM firma.\"Atendente\" at WHERE at.\"AtendenteUsuarioId\"=:usuario AND at.\"ClienteId\"=:cliente";
    String setores =
        "SELECT ast.\"SetorId\" FROM firma.\"AtendenteSetor\" ast WHERE ast.\"AtendenteId\" IN("
            + operador
            + ")";
    return base
        + switch (nomeTabela) {
          case "Atendente" ->
              " AND ("
                  + apelidoTabela
                  + ".id IN("
                  + operador
                  + ") OR "
                  + apelidoTabela
                  + ".id IN(SELECT ast.\"AtendenteId\" FROM firma.\"AtendenteSetor\" ast WHERE ast.\"SetorId\" IN("
                  + setores
                  + ")))";
          case "Setor" -> " AND " + apelidoTabela + ".id IN(" + setores + ")";
          case "Canal" ->
              " AND "
                  + apelidoTabela
                  + ".id IN(SELECT s.\"CanalId\" FROM firma.\"Setor\" s WHERE s.id IN("
                  + setores
                  + "))";
          case "Atendimento" ->
              " AND ("
                  + apelidoTabela
                  + ".\"AtendenteId\" IN("
                  + operador
                  + ") OR ("
                  + apelidoTabela
                  + ".\"AtendenteId\" IS NULL AND "
                  + apelidoTabela
                  + ".\"SetorId\" IN("
                  + setores
                  + ")))";
          case "Contato" ->
              " AND EXISTS(SELECT 1 FROM firma.\"Atendimento\" atd WHERE atd.\"ContatoId\"="
                  + apelidoTabela
                  + ".id AND (atd.\"AtendenteId\" IN("
                  + operador
                  + ") OR atd.\"SetorId\" IN("
                  + setores
                  + ")))";
          default -> "";
        };
  }

  public boolean pode(Contexto contextoAtual, String permissao, String acao) {
    if (contextoAtual.usuario() == 0) return true;
    return ((Number)
                banco
                    .um(
                        "SELECT count(*) n FROM firma_identidade.\"Permissao\" p WHERE p.\"RepositorioId\"=:r AND ((p.\"PermissaoNome\"='all' AND p.\"PermissaoAcao\"='manage') OR (p.\"PermissaoNome\"=:nome AND p.\"PermissaoAcao\"=:acao)) AND (p.id IN (SELECT pp.\"PermissaoId\" FROM firma_identidade.\"PerfilPermissao\" pp JOIN firma_identidade.\"Usuario\" u ON u.\"PerfilId\"=pp.\"PerfilId\" AND u.\"RepositorioId\"=pp.\"RepositorioId\" WHERE u.id=:u) OR p.id IN(SELECT up.\"PermissaoId\" FROM firma_identidade.\"UsuarioPermissao\" up WHERE up.\"UsuarioId\"=:u AND up.\"RepositorioId\"=:r))",
                        Map.of(
                            "r",
                            contextoAtual.repositorio(),
                            "u",
                            contextoAtual.usuario(),
                            "nome",
                            permissao,
                            "acao",
                            acao))
                    .get("n"))
            .longValue()
        > 0;
  }

  public void autorizar(Contexto contextoAtual, String slug, String acao) {
    String nomeTabela = modulo(slug).get("tabela").toString().toLowerCase(Locale.ROOT);
    String parametros =
        Set.of("webhooks", "chaves", "integracoes", "ia", "bots").contains(slug)
            ? switch (slug) {
              case "webhooks" -> "webhook";
              case "chaves" -> "chaveapi";
              case "integracoes" -> "integracao";
              case "ia" -> "chatinteligente";
              default -> "chatbot";
            }
            : nomeTabela;
    if (pode(contextoAtual, parametros + (acao.equals("read") ? "ww" : "detail"), acao)
        || pode(contextoAtual, parametros, acao)) return;
    if (acao.equals("read")
        && (slug.equals("cliente")
            || Set.of(
                        "atendimentos",
                        "contatos",
                        "canais",
                        "setores",
                        "atendentes",
                        "tags",
                        "respostas",
                        "ia",
                        "templates")
                    .contains(slug)
                && pode(contextoAtual, "chat", "read"))) return;
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu perfil não permite esta operação");
  }

  public List<Map<String, Object>> catalogo(Contexto contextoAtual) {
    return catalogo().stream()
        .filter(registroAtual -> {
          try {
            autorizar(contextoAtual, registroAtual.get("slug").toString(), "read");
            return true;
          } catch (ResponseStatusException erroAtual) {
            return false;
          }
        })
        .map(registroAtual -> {
          Map<String, Object> dados = new LinkedHashMap<>(registroAtual);
          try {
            autorizar(contextoAtual, registroAtual.get("slug").toString(), "update");
          } catch (ResponseStatusException erroAtual) {
            dados.put("editavel", false);
          }
          String slug = registroAtual.get("slug").toString();
          var acoes = new ArrayList<String>();
          try {
            autorizar(contextoAtual, slug, "insert");
            dados.put("criavel", registroAtual.get("editavel"));
          } catch (ResponseStatusException erroAtual) {
            dados.put("criavel", false);
          }
          boolean editar = Boolean.TRUE.equals(dados.get("editavel"));
          if (slug.equals("canais") && editar)
            acoes.addAll(List.of("conectar", "qr", "estado", "importar", "desconectar"));
          if (slug.equals("templates") && editar)
            acoes.addAll(List.of("publicar", "sincronizar", "excluir-meta"));
          if (slug.equals("ia")) acoes.add("execucoes-ia");
          if (slug.equals("disparos")) {
            acoes.add("detalhes");
            if (editar) acoes.addAll(List.of("cancelar", "repetir-falhas"));
            dados.put("editavel", false);
          }
          if (slug.equals("bots")) acoes.add("execucoes");
          if (slug.equals("webhooks")) acoes.add("entregas");
          if (slug.equals("integracoes")) acoes.add("consultar");
          if (slug.equals("sessoes")
              && (contextoAtual.usuario() == 0
                  || pode(contextoAtual, "all", "manage")
                  || pode(contextoAtual, "usuariodetail", "update"))) acoes.add("revogar");
          try {
            autorizar(contextoAtual, slug, "delete");
            if (!Set.of("cliente", "atendimentos", "disparos", "sessoes").contains(slug))
              acoes.add("remover");
          } catch (ResponseStatusException erroAtual) {
          }
          dados.put("acoes", acoes);
          return dados;
        })
        .toList();
  }

  public List<Map<String, Object>> catalogo() {
    var resultado = new ArrayList<Map<String, Object>>();
    for (var registroAtual : modulos) {
      var copia = new LinkedHashMap<>(registroAtual);
      String nomeTabela = registroAtual.get("tabela").toString();
      var camposAtuais = new ArrayList<Map<String, Object>>();
      for (var campo : campos(nomeTabela)) {
        String textoDoRegistro = campo.get("nome").toString();
        if (textoDoRegistro.equals("UsuarioSenha")
            || gerenciado(textoDoRegistro)
            || segredo(textoDoRegistro) && !gravavelSegredo(textoDoRegistro)
            || nomeTabela.equals("DisparoMassa")
                && !Set.of(
                        "CanalId",
                        "ListaContatoId",
                        "MensagemTemplateId",
                        "SetorId",
                        "DisparoMassaDataHoraInicio",
                        "DisparoMassaMensagensPorDisparos",
                        "DisparoMassaIntervaloEntreDisparos",
                        "DisparoMassaMaxMensagemDia",
                        "DisparoMassaUltimoHorario",
                        "DisparoMassaHoraReinicio",
                        "DisparoMassaDiasEnvio",
                        "DisparoMassaConteudo",
                        "DisparoMassaColunaDestinatario",
                        "DisparoMassaTemplateVariaveis",
                        "DisparoMassaDesejaGerarAtendimento")
                    .contains(textoDoRegistro)) continue;
        var dadosAtuais = new LinkedHashMap<>(campo);
        if (segredo(textoDoRegistro)) {
          dadosAtuais.put("sensivel", true);
          if (!textoDoRegistro.equals("IntegracaoDados")) dadosAtuais.put("tipo", "PASSWORD");
        }
        relacoes.stream()
            .filter(registroLocal ->
                registroLocal.get("schema").equals(tabela(nomeTabela).get("schema"))
                    && registroLocal.get("tabela").equals(nomeTabela)
                    && registroLocal.get("coluna").equals(textoDoRegistro))
            .findFirst()
            .ifPresent(registroLocal -> {
              dadosAtuais.put("referencia", registroLocal.get("alvo"));
              dadosAtuais.put(
                  "moduloReferencia",
                  modulos.stream()
                      .filter(moduloAtual ->
                          moduloAtual.get("tabela").equals(registroLocal.get("alvo")))
                      .map(moduloAtual -> moduloAtual.get("slug"))
                      .findFirst()
                      .orElse(""));
            });
        camposAtuais.add(dadosAtuais);
      }
      if (nomeTabela.equals("Usuario"))
        camposAtuais.add(Map.of("nome", "UsuarioSenha", "tipo", "PASSWORD", "obrigatorio", false));
      copia.put("campos", camposAtuais);
      resultado.add(copia);
    }
    return resultado;
  }

  public Map<String, Object> listar(
      String slug, Contexto contextoAtual, int pagina, int tamanho, String busca) {
    return listar(slug, contextoAtual, pagina, tamanho, busca, null);
  }

  public Map<String, Object> listar(
      String slug, Contexto contextoAtual, int pagina, int tamanho, String busca, String estado) {
    return listar(slug, contextoAtual, pagina, tamanho, busca, estado, Map.of());
  }

  public Map<String, Object> listar(
      String slug,
      Contexto contextoAtual,
      int pagina,
      int tamanho,
      String busca,
      String estado,
      Map<String, String> filtros) {
    // A tabela manda busca, filtros e paginação. A consulta também respeita a empresa e a opção de
    // mostrar bots.

    autorizar(contextoAtual, slug, "read");
    String nomeTabela = modulo(slug).get("tabela").toString();
    var parametros = params(contextoAtual);
    String where = escopo(nomeTabela, "t", contextoAtual);
    parametros.put("tabelaExclusao", nomeTabela);
    where +=
        " AND NOT EXISTS(SELECT 1 FROM firma_relacionamento_exclusao x WHERE x.cliente_id=:cliente AND x.tabela=:tabelaExclusao AND x.registro_id=t.id)";
    String emBot =
        "(COALESCE(t.\"AtendimentoBOTExterno\",false) OR EXISTS(SELECT 1 FROM chat_bot_sessao bs JOIN chat_atendimento ao ON ao.canal_id=bs.canal_id AND ao.contato_id=bs.contato_id JOIN firma_relacionamento_vinculo vb ON vb.origem='chat_atendimento' AND vb.origem_id=ao.id::text AND vb.destino='Atendimento' AND vb.destino_id=t.id WHERE bs.estado='ATIVA' AND ao.estado<>'FINALIZADO'))";
    if (nomeTabela.equals("Atendimento")) {
      where +=
          " AND (t.\"AtendimentoStatus\"='finalizado' OR NOT "
              + emBot
              + " OR EXISTS(SELECT 1 FROM firma.\"Canal\" cb WHERE cb.id=t.\"CanalId\" AND COALESCE(cb.\"CanalVisualizaMenuBOTChat\",false)))";
      if (estado != null && !estado.isBlank()) {
        if (!Set.of("pendente", "emandamento", "finalizado", "bot").contains(estado))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação inválida");
        parametros.put("estado", estado);
        where +=
            estado.equals("bot")
                ? " AND " + emBot + " AND t.\"AtendimentoStatus\"<>'finalizado'"
                : " AND t.\"AtendimentoStatus\"=:estado"
                    + (estado.equals("finalizado") ? "" : " AND NOT " + emBot);
      }
    }
    if (nomeTabela.equals("Atendimento"))
      for (var itemDoRegistro : filtros.entrySet()) {
        String chave = itemDoRegistro.getKey(), valor = itemDoRegistro.getValue();
        if (valor == null || valor.isBlank()) continue;
        if (Set.of("CanalId", "SetorId", "AtendenteId", "TagId").contains(chave)) {
          long id;
          try {
            id = Long.parseLong(valor);
          } catch (NumberFormatException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro inválido");
          }
          registro(chave.substring(0, chave.length() - 2), id, contextoAtual);
          parametros.put("f" + chave, id);
          where +=
              chave.equals("TagId")
                  ? " AND EXISTS(SELECT 1 FROM firma.\"AtendimentoTag\" ta WHERE ta.\"AtendimentoId\"=t.id AND ta.\"TagId\"=:fTagId)"
                  : " AND t." + entreAspas(chave) + "=:f" + chave;
        } else if (chave.equals("inicio") || chave.equals("fim")) {
          try {
            var dia = java.time.LocalDate.parse(valor);
            parametros.put(
                "f" + chave,
                (chave.equals("fim") ? dia.plusDays(1) : dia)
                    .atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo"))
                    .toOffsetDateTime());
            where += " AND t.\"createdAt\"" + (chave.equals("inicio") ? ">=" : "<") + ":f" + chave;
          } catch (java.time.format.DateTimeParseException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data do filtro inválida");
          }
        }
      }
    var visiveis =
        campos(nomeTabela).stream()
            .map(x -> x.get("nome").toString())
            .filter(valorAtual -> !segredo(valorAtual))
            .toList();
    if (busca != null && !busca.isBlank()) {
      parametros.put(
          "busca", "%" + busca.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
      where +=
          " AND ("
              + String.join(
                  " OR ",
                  visiveis.stream()
                      .filter(valorAtual -> !gerenciado(valorAtual))
                      .map(valorAtual ->
                          "CAST(t." + entreAspas(valorAtual) + " AS text) ILIKE :busca")
                      .toList())
              + (nomeTabela.equals("Atendimento")
                  ? " OR EXISTS(SELECT 1 FROM firma.\"Contato\" co WHERE co.id=t.\"ContatoId\" AND (co.\"ContatoNome\" ILIKE :busca OR co.\"ContatoNumero\" ILIKE :busca))"
                  : "")
              + ")";
    }
    var terreno =
        FiltroTratorFirma.preparar(json, filtros, visiveis, campos(nomeTabela), parametros);
    where += terreno.where();
    long total =
        ((Number)
                banco
                    .um(
                        "SELECT count(*) total FROM " + nome(nomeTabela) + " t WHERE " + where,
                        parametros)
                    .get("total"))
            .longValue();
    parametros.put("limite", tamanho);
    parametros.put("offset", (pagina - 1) * tamanho);
    String relacionados =
        nomeTabela.equals("Atendimento")
            ? ",(SELECT co.\"ContatoNome\" FROM firma.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoNome\",(SELECT co.\"ContatoNumero\" FROM firma.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoNumero\",(SELECT co.\"ContatoEmail\" FROM firma.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoEmail\",(SELECT co.\"ContatoAvatar\" FROM firma.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoAvatar\",(SELECT ca.\"CanalNome\" FROM firma.\"Canal\" ca WHERE ca.id=t.\"CanalId\") AS \"CanalNome\",(SELECT CASE WHEN COALESCE(me.\"MensagemDeletada\",false) THEN 'Mensagem excluída' ELSE COALESCE(NULLIF(me.\"MensagemTexto\",''),'Arquivo anexado') END FROM firma.\"Mensagem\" me WHERE me.id=t.\"MensagemId\") AS \"UltimaMensagem\""
            : "";
    if (nomeTabela.equals("Atendimento")) relacionados += "," + emBot + " AS \"AtendimentoEmBot\"";
    var itens =
        banco.listar(
            "SELECT "
                + String.join(
                    ",",
                    visiveis.stream().map(valorAtual -> "t." + entreAspas(valorAtual)).toList())
                + relacionados
                + " FROM "
                + nome(nomeTabela)
                + " t WHERE "
                + where
                + " ORDER BY "
                + terreno.ordem()
                + " LIMIT :limite OFFSET :offset",
            parametros);
    return Map.of("itens", itens, "total", total, "pagina", pagina, "tamanho", tamanho);
  }

  public Map<String, Object> registro(String nomeTabela, long id, Contexto contextoAtual) {
    var parametros = params(contextoAtual);
    parametros.put("id", id);
    var registroLocal3 =
        banco.um(
            "SELECT t.* FROM "
                + nome(nomeTabela)
                + " t WHERE t.id=:id AND "
                + escopo(nomeTabela, "t", contextoAtual),
            parametros);
    if (registroLocal3 == null)
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Registro não encontrado nesta empresa");
    return registroLocal3;
  }

  public Map<String, Object> publico(String slug, long id, Contexto contextoAtual) {
    autorizar(contextoAtual, slug, "read");
    var dadosDoRegistro =
        new LinkedHashMap<>(registro(modulo(slug).get("tabela").toString(), id, contextoAtual));
    dadosDoRegistro.keySet().removeIf(this::segredo);
    return dadosDoRegistro;
  }

  private void validarRelacao(
      String nomeTabela, String coluna, Object valor, Contexto contextoAtual) {
    var ref =
        relacoes.stream()
            .filter(registroLocal ->
                registroLocal.get("schema").equals(tabela(nomeTabela).get("schema"))
                    && registroLocal.get("tabela").equals(nomeTabela)
                    && registroLocal.get("coluna").equals(coluna))
            .findFirst();
    if (ref.isEmpty() || valor == null) return;
    if (!(valor instanceof Number valorAtual))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Escolha um registro válido para " + coluna);
    registro(ref.get().get("alvo").toString(), valorAtual.longValue(), contextoAtual);
  }

  @Transactional
  public Map<String, Object> salvar(
      String slug, Long id, Map<String, Object> entrada, Contexto contextoAtual) {
    // RegrasFirma confere os dados antes da escrita. Depois, EspelhoFirma atualiza a parte usada
    // pelo chat.

    autorizar(contextoAtual, slug, id == null ? "insert" : "update");
    var mod = modulo(slug);
    if (!Boolean.TRUE.equals(mod.get("editavel")))
      throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Este módulo é de consulta");
    String nomeTabela = mod.get("tabela").toString();
    if (nomeTabela.equals("DisparoMassa"))
      throw new ResponseStatusException(
          HttpStatus.METHOD_NOT_ALLOWED, "Use a operação de agendamento de disparos");
    if (nomeTabela.equals("Cliente")) {
      if (id != null && id != contextoAtual.cliente())
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      id = contextoAtual.cliente();
    }
    if (id != null
        && banco.um(
                "SELECT registro_id FROM firma_relacionamento_exclusao WHERE cliente_id=:c AND tabela=:t AND registro_id=:id",
                Map.of("c", contextoAtual.cliente(), "t", nomeTabela, "id", id))
            != null)
      throw new ResponseStatusException(HttpStatus.GONE, "Este registro foi excluído");
    var anterior = id == null ? Map.<String, Object>of() : registro(nomeTabela, id, contextoAtual);
    var valores = new LinkedHashMap<String, Object>();
    var permitidos = new HashMap<String, Map<String, Object>>();
    campos(nomeTabela)
        .forEach(campoAtual -> permitidos.put(campoAtual.get("nome").toString(), campoAtual));
    for (var item : entrada.entrySet()) {
      String coluna = item.getKey();
      if (gerenciado(coluna)
          || !permitidos.containsKey(coluna)
          || (segredo(coluna) && !gravavelSegredo(coluna)))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo não editável: " + coluna);
      Object valor = item.getValue();
      String tipo = permitidos.get(coluna).get("tipo").toString();
      if (valor != null) {
        if (tipo.startsWith("VARCHAR")) {
          int maiorValor =
              Integer.parseInt(tipo.substring(tipo.indexOf('(') + 1, tipo.indexOf(')')));
          if (!(valor instanceof String valorDoRegistro) || valorDoRegistro.length() > maiorValor)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Tamanho inválido para " + coluna);
        }
        if (tipo.equals("BOOLEAN") && !(valor instanceof Boolean))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor booleano inválido");
        if (tipo.equals("INTEGER")
            && !(valor instanceof Number valorAtual
                && valorAtual.doubleValue() == valorAtual.longValue()))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número inteiro inválido");
        if (coluna.equals("UsuarioSenha")) {
          if (!(valor instanceof String valorDoRegistro)
              || valorDoRegistro.length() < 8
              || valorDoRegistro.length() > 256)
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A senha deve ter entre 8 e 256 caracteres");
          var credencial = SenhaFirma.criar((String) valor);
          valor = credencial.hash();
          valores.put("UsuarioRandomBytes", credencial.sal());
          valores.put("UsuarioTipoAutenticacao", "local");
        }
      }
      validarRelacao(nomeTabela, coluna, valor, contextoAtual);
      valores.put(coluna, valor);
    }
    regras.aplicar(nomeTabela, id, valores, anterior, contextoAtual);
    if (permitidos.containsKey("ClienteId")) valores.put("ClienteId", contextoAtual.cliente());
    if (permitidos.containsKey("RepositorioId"))
      valores.put("RepositorioId", contextoAtual.repositorio());
    if (nomeTabela.equals("ListaContato") && valores.containsKey("ListaContatoContatos")) {
      Object itens = valores.get("ListaContatoContatos");
      if (itens != null && !(itens instanceof List<?>))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Os contatos devem formar uma lista");
      valores.put("ListaContatoQuantidadeContatos", itens == null ? 0 : ((List<?>) itens).size());
    }
    if (nomeTabela.equals("Atendente")
        && valores.get("AtendenteUsuarioId") instanceof Number usuarioId)
      registro("Usuario", usuarioId.longValue(), contextoAtual);
    if (nomeTabela.equals("Setor") && id == null && !valores.containsKey("CanalId"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o canal do setor");
    if (nomeTabela.equals("Usuario")) {
      valores.put("UsuarioClienteId", contextoAtual.cliente());
      valores.put(
          "UsuarioNomeCompleto",
          (Objects.toString(
                      valores.getOrDefault(
                          "UsuarioPrimeiroNome", anterior.get("UsuarioPrimeiroNome")),
                      "")
                  + " "
                  + Objects.toString(
                      valores.getOrDefault("UsuarioUltimoNome", anterior.get("UsuarioUltimoNome")),
                      ""))
              .strip());
    }
    if (nomeTabela.equals("Atendimento")
        && valores.containsKey("AtendimentoStatus")
        && !Set.of("pendente", "emandamento", "finalizado")
            .contains(valores.get("AtendimentoStatus")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação inválida");
    if (permitidos.containsKey(nomeTabela + "GUID") && id == null)
      valores.put(nomeTabela + "GUID", UUID.randomUUID());
    valores.put("updatedAt", OffsetDateTime.now());
    if (id == null) valores.put("createdAt", OffsetDateTime.now());
    var parametros = params(contextoAtual);
    var expressoes = new LinkedHashMap<String, String>();
    int indice = 0;
    for (var itemDoRegistro : valores.entrySet()) {
      String chave = "v" + indice++;
      String tipo = permitidos.get(itemDoRegistro.getKey()).get("tipo").toString();
      Object valorLocal = itemDoRegistro.getValue();
      parametros.put(
          chave,
          (tipo.equals("JSONB") || tipo.equals("JSON")) && valorLocal != null
              ? json.writeValueAsString(valorLocal)
              : valorLocal);
      expressoes.put(
          entreAspas(itemDoRegistro.getKey()),
          (tipo.equals("JSONB") || tipo.equals("JSON"))
              ? "CAST(:" + chave + " AS " + tipo.toLowerCase(Locale.ROOT) + ")"
              : tipo.equals("UUID")
                  ? "CAST(:" + chave + " AS uuid)"
                  : tipo.equals("TIMESTAMP WITH TIME ZONE")
                      ? "CAST(:" + chave + " AS timestamptz)"
                      : ":" + chave);
    }
    String sql;
    if (id == null)
      sql =
          "INSERT INTO "
              + nome(nomeTabela)
              + " ("
              + String.join(",", expressoes.keySet())
              + ") VALUES ("
              + String.join(",", expressoes.values())
              + ") RETURNING id";
    else {
      parametros.put("id", id);
      sql =
          "UPDATE "
              + nome(nomeTabela)
              + " t SET "
              + String.join(
                  ",",
                  expressoes.entrySet().stream()
                      .map(itemDoRegistro ->
                          itemDoRegistro.getKey() + "=" + itemDoRegistro.getValue())
                      .toList())
              + " WHERE t.id=:id AND "
              + escopo(nomeTabela, "t", contextoAtual)
              + " RETURNING id";
    }
    long novo = ((Number) banco.um(sql, parametros).get("id")).longValue();
    if (nomeTabela.equals("Usuario") && id != null && valores.containsKey("UsuarioSenha"))
      banco.executar(
          "UPDATE firma_identidade.\"Sessao\" SET \"SessaoStatus\"='F' WHERE \"UsuarioId\"=:id",
          Map.of("id", id));
    var registroLocal3 = new LinkedHashMap<>(registro(nomeTabela, novo, contextoAtual));
    espelho.salvar(nomeTabela, registroLocal3, contextoAtual);
    Object chaveNova =
        nomeTabela.equals("ChaveAPI") && id == null ? registroLocal3.get("ChaveAPIValor") : null;
    registroLocal3.keySet().removeIf(this::segredo);
    if (chaveNova != null) registroLocal3.put("chaveGerada", chaveNova);
    return registroLocal3;
  }

  public Map<String, Object> operador(Contexto contextoAtual) {
    var apelidoTabela =
        banco.um(
            "SELECT id,\"AtendenteNome\",\"AtendenteStatusChat\",\"AtendenteSobreChat\",\"AtendenteNotificacoes\" FROM firma.\"Atendente\" WHERE \"AtendenteUsuarioId\"=:u AND \"ClienteId\"=:c",
            Map.of("u", contextoAtual.usuario(), "c", contextoAtual.cliente()));
    return apelidoTabela == null ? Map.of() : apelidoTabela;
  }

  @Transactional
  public Map<String, Object> preferencias(Contexto contextoAtual, Map<String, Object> parametros) {
    var apelidoTabela = operador(contextoAtual);
    if (apelidoTabela.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Seu usuário não possui cadastro de atendente");
    if (!Set.of(
            "AtendenteNome", "AtendenteStatusChat", "AtendenteSobreChat", "AtendenteNotificacoes")
        .containsAll(parametros.keySet()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferência inválida");
    var alteracoes = new LinkedHashMap<String, Object>();
    for (var itemDoRegistro : parametros.entrySet()) {
      if (itemDoRegistro.getKey().equals("AtendenteNotificacoes")) {
        if (itemDoRegistro.getValue() != null && !(itemDoRegistro.getValue() instanceof Map<?, ?>))
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferências inválidas");
      } else if (itemDoRegistro.getValue() != null
          && (!(itemDoRegistro.getValue() instanceof String tabelaAtual)
              || tabelaAtual.length()
                  > (itemDoRegistro.getKey().equals("AtendenteNome")
                      ? 70
                      : itemDoRegistro.getKey().equals("AtendenteSobreChat") ? 256 : 20)))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferência muito longa");
      alteracoes.put(itemDoRegistro.getKey(), itemDoRegistro.getValue());
    }
    var parametrosAtuais = new HashMap<String, Object>();
    var sets = new ArrayList<String>();
    int indiceAtual = 0;
    for (var itemDoRegistro : alteracoes.entrySet()) {
      String chaveAtual = "p" + indiceAtual++;
      parametrosAtuais.put(
          chaveAtual,
          itemDoRegistro.getKey().equals("AtendenteNotificacoes")
              ? json.writeValueAsString(itemDoRegistro.getValue())
              : itemDoRegistro.getValue());
      sets.add(
          entreAspas(itemDoRegistro.getKey())
              + "="
              + (itemDoRegistro.getKey().equals("AtendenteNotificacoes")
                  ? "CAST(:" + chaveAtual + " AS jsonb)"
                  : ":" + chaveAtual));
    }
    if (sets.isEmpty()) return apelidoTabela;
    parametrosAtuais.put("id", apelidoTabela.get("id"));
    banco.executar(
        "UPDATE firma.\"Atendente\" SET "
            + String.join(",", sets)
            + ",\"updatedAt\"=now() WHERE id=:id",
        parametrosAtuais);
    espelho.salvar(
        "Atendente",
        registro("Atendente", ((Number) apelidoTabela.get("id")).longValue(), contextoAtual),
        contextoAtual);
    return operador(contextoAtual);
  }

  public Map<String, Object> panorama(Contexto contextoAtual) {
    var dadosLocal = new LinkedHashMap<String, Object>();
    for (String slug : List.of("contatos", "canais", "atendentes", "atendimentos")) {
      try {
        dadosLocal.put(slug, listar(slug, contextoAtual, 1, 1, "").get("total"));
      } catch (ResponseStatusException erroAtual) {
        dadosLocal.put(slug, 0);
      }
    }
    return dadosLocal;
  }
}
