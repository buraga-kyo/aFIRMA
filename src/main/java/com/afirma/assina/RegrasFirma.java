package com.afirma.assina;

import java.util.*;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** ServicoFirma chama estas regras antes de salvar os cadastros e seus vínculos. */
@Component
public class RegrasFirma {
  private final BancoAssineai banco;
  private final MotorFluxoBot motor;
  private final ObjectMapper json;

  public RegrasFirma(BancoAssineai banco, MotorFluxoBot motor, ObjectMapper json) {
    this.banco = banco;
    this.motor = motor;
    this.json = json;
  }

  static void exigir(boolean ok, String mensagem) {
    if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  static String texto(Map<String, Object> registro, String textoAtual) {
    return Objects.toString(registro.get(textoAtual), "");
  }

  static long numero(Map<String, Object> registro, String textoAtual) {
    return registro.get(textoAtual) instanceof Number valorAtual ? valorAtual.longValue() : 0;
  }

  static void opcao(Map<String, Object> registro, String nome, String... opcoes) {
    if (registro.get(nome) != null)
      exigir(Set.of(opcoes).contains(registro.get(nome)), "Valor inválido para " + nome);
  }

  static void url(String valor) {
    try {
      var usuarioAtual = URI.create(valor);
      exigir(
          "https".equalsIgnoreCase(usuarioAtual.getScheme())
              && usuarioAtual.getHost() != null
              && usuarioAtual.getUserInfo() == null,
          "Informe uma URL HTTPS válida");
    } catch (IllegalArgumentException erro) {
      exigir(false, "URL inválida");
    }
  }

  public void aplicar(
      String tabela,
      Long id,
      Map<String, Object> valores,
      Map<String, Object> anterior,
      ServicoFirma.Contexto contexto) {
    var dados = new HashMap<>(anterior);
    dados.putAll(valores);
    if (id == null) {
      String situacao =
          switch (tabela) {
            case "Canal" -> "CanalStatusUso";
            case "MensagemTemplate" -> "MensagemTemplateStatusUso";
            default -> tabela + "Status";
          };
      if (Set.of(
                  "Canal",
                  "Atendente",
                  "Setor",
                  "Tag",
                  "ListaContato",
                  "MensagemTemplate",
                  "ChatbotDefinicao",
                  "Integracao",
                  "InteligenciaArtificial",
                  "ChaveAPI")
              .contains(tabela)
          && !dados.containsKey(situacao)) valores.put(situacao, true);
    }
    switch (tabela) {
      case "Contato" -> {
        if (valores.containsKey("ContatoNumero")) {
          String telefone = texto(dados, "ContatoNumero").replaceAll("[^0-9]", "");
          exigir(telefone.length() >= 10 && telefone.length() <= 15, "Telefone inválido");
          if (telefone.length() == 10 || telefone.length() == 11) telefone = "55" + telefone;
          valores.put("ContatoNumero", telefone);
          unico(tabela, "ContatoNumero", telefone, id, contexto);
        }
        if (id == null)
          exigir(
              !texto(dados, "ContatoNome").isBlank() && valores.containsKey("ContatoNumero"),
              "Informe nome e telefone do contato");
      }
      case "Canal" -> {
        String telefone = texto(dados, "CanalNumeroCelular").replaceAll("[^0-9]", "");
        if (!telefone.isBlank()) {
          exigir(telefone.length() >= 10 && telefone.length() <= 15, "Número do canal inválido");
          if (telefone.length() == 10 || telefone.length() == 11) telefone = "55" + telefone;
          if (valores.containsKey("CanalNumeroCelular"))
            valores.put("CanalNumeroCelular", telefone);
          banco.um(
              "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
              Map.of("k", "CanalNumero:" + contexto.cliente() + ":" + telefone));
          exigir(
              banco.um(
                      "SELECT ca.id FROM firma.\"Canal\" ca WHERE ca.\"ClienteId\"=:c AND ca.id<>:id AND CASE WHEN length(regexp_replace(COALESCE(ca.\"CanalNumeroCelular\",''),'[^0-9]','','g')) IN (10,11) THEN '55'||regexp_replace(ca.\"CanalNumeroCelular\",'[^0-9]','','g') ELSE regexp_replace(ca.\"CanalNumeroCelular\",'[^0-9]','','g') END=:n AND NOT EXISTS(SELECT 1 FROM firma_relacionamento_exclusao x WHERE x.cliente_id=:c AND x.tabela='Canal' AND x.registro_id=ca.id) LIMIT 1",
                      Map.of("c", contexto.cliente(), "id", id == null ? 0 : id, "n", telefone))
                  == null,
              "Já existe um canal não excluído com este número");
        }
        exigir(!texto(dados, "CanalNome").isBlank(), "Informe o nome do canal");
        if (id == null) {
          valores.putIfAbsent("CanalProvedorAPI", "Baileys");
          valores.putIfAbsent("CanalStatus", "OFFLINE");
        }
        opcao(dados, "CanalProvedorAPI", "Baileys", "WWebJS", "Meta");
        if (Boolean.TRUE.equals(dados.get("CanalFinalizaAtendimentoPorTempoInatividade")))
          exigir(
              numero(dados, "CanalAtendimentoTempoInatividade") > 0,
              "Informe o tempo de inatividade em minutos");
      }
      case "Setor" -> exigir(!texto(dados, "SetorNome").isBlank(), "Informe o nome do setor");
      case "Atendente" -> {
        exigir(!texto(dados, "AtendenteNome").isBlank(), "Informe o nome do atendente");
        if (id == null) valores.putIfAbsent("AtendenteStatusChat", "DISPONIVEL");
        if (dados.get("AtendenteUsuarioId") != null)
          unico(tabela, "AtendenteUsuarioId", dados.get("AtendenteUsuarioId"), id, contexto);
      }
      case "AtendenteSetor" -> unicoVinculo(tabela, "AtendenteId", "SetorId", dados, id, contexto);
      case "PerfilPermissao" ->
          unicoVinculo(tabela, "PerfilId", "PermissaoId", dados, id, contexto);
      case "UsuarioPermissao" ->
          unicoVinculo(tabela, "UsuarioId", "PermissaoId", dados, id, contexto);
      case "Tag" -> exigir(!texto(dados, "TagNome").isBlank(), "Informe o nome da tag");
      case "MensagemRapida" ->
          exigir(
              !texto(dados, "MensagemRapidaTexto").isBlank() || dados.get("ArquivoId") != null,
              "Informe o texto ou arquivo da resposta");
      case "Usuario" -> {
        String email = texto(dados, "UsuarioEmail").strip().toLowerCase(Locale.ROOT);
        exigir(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "E-mail inválido");
        valores.put("UsuarioEmail", email);
        unico(tabela, "UsuarioEmail", email, id, contexto);
        exigir(!texto(dados, "UsuarioPrimeiroNome").isBlank(), "Informe o nome do usuário");
        if (id == null) {
          exigir(valores.get("UsuarioSenha") != null, "Defina uma senha para o usuário");
          valores.putIfAbsent("UsuarioBloqueado", false);
        }
      }
      case "ListaContato" -> {
        exigir(!texto(dados, "ListaContatoNome").isBlank(), "Informe o nome da lista");
        if (dados.get("ListaContatoContatos") instanceof List<?> lista) {
          exigir(lista.size() <= 100000, "Lista muito grande");
          exigir(
              lista.stream().allMatch(valorAtual -> valorAtual instanceof Map<?, ?>),
              "Cada destinatário deve ser um objeto com suas colunas");
        }
      }
      case "MensagemTemplate" -> {
        exigir(
            texto(dados, "MensagemTemplateNome").matches("[a-z0-9_]{1,100}"),
            "Use letras minúsculas, números e sublinhado no nome do template");
        exigir(!texto(dados, "MensagemTemplateCorpo").isBlank(), "Informe o corpo do template");
        opcao(dados, "MensagemTemplateCategoria", "AUTHENTICATION", "MARKETING", "UTILITY");
        if (id == null) {
          valores.putIfAbsent("MensagemTemplateStatusMeta", "RASCUNHO");
          valores.putIfAbsent("MensagemTemplateCategoria", "UTILITY");
        }
      }
      case "ChatbotDefinicao" -> {
        exigir(
            numero(dados, "CanalId") > 0 && !texto(dados, "ChatbotDefinicaoNome").isBlank(),
            "Informe nome e canal do bot");
        String provedor = texto(dados, "ChatbotDefinicaoProvedor");
        opcao(dados, "ChatbotDefinicaoProvedor", "firma", "n8n", "INTERNO", "N8N");
        if (provedor.equalsIgnoreCase("n8n")) url(texto(dados, "ChatbotDefinicaoN8nURL"));
        else {
          Object fluxo = dados.get("ChatbotDefinicaoFlow");
          exigir(fluxo instanceof Map<?, ?>, "Informe o fluxo do bot");
          motor.validar(json.valueToTree(fluxo));
          var compilado = CompiladorBotFirma.compilar((Map<String, Object>) fluxo);
          valores.put("ChatbotDefinicaoJSON", json.writeValueAsString(compilado.get("Definicao")));
          valores.put(
              "ChatbotDefinicaoMetadata", json.writeValueAsString(compilado.get("Metadata")));
        }
        if (id == null) {
          valores.putIfAbsent("ChatbotDefinicaoProvedor", "firma");
          valores.putIfAbsent("ChatbotDefinicaoFuncionamento", Map.of("Integral", true));
        }
      }
      case "InteligenciaArtificial" -> {
        exigir(
            !texto(dados, "InteligenciaArtificialNome").isBlank()
                && !texto(dados, "InteligenciaArtificialModelo").isBlank()
                && !texto(dados, "InteligenciaArtificialTextoPrompt").isBlank(),
            "Informe nome, modelo e instruções da IA");
        opcao(
            dados,
            "InteligenciaArtificialProvedor",
            "chat-gpt",
            "deepseek",
            "OPENAI",
            "DEEPSEEK",
            "ChatGPT",
            "OpenAI",
            "DeepSeek",
            "ChatGPT_CompletionsAPI");
        opcao(dados, "InteligenciaArtificialAtivador", "manual-chat", "todas-mensagens");
        if (id == null) valores.putIfAbsent("InteligenciaArtificialAtivador", "manual-chat");
        if (!Boolean.FALSE.equals(dados.get("InteligenciaArtificialStatus"))) {
          banco.um(
              "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
              Map.of(
                  "k",
                  "ia:"
                      + contexto.cliente()
                      + ":"
                      + dados.get("CanalId")
                      + ":"
                      + dados.get("SetorId")));
          var parametros = new HashMap<String, Object>();
          parametros.put("c", contexto.cliente());
          parametros.put("canal", dados.get("CanalId"));
          parametros.put("setor", dados.get("SetorId"));
          parametros.put("a", dados.getOrDefault("InteligenciaArtificialAtivador", "manual-chat"));
          parametros.put("id", id == null ? 0 : id);
          exigir(
              banco.um(
                      "SELECT id FROM firma.\"InteligenciaArtificial\" WHERE \"ClienteId\"=:c AND \"CanalId\" IS NOT DISTINCT FROM CAST(:canal AS integer) AND (\"SetorId\" IS NOT DISTINCT FROM CAST(:setor AS integer) OR \"SetorId\" IS NULL) AND \"InteligenciaArtificialAtivador\"=:a AND \"InteligenciaArtificialStatus\" AND id<>:id LIMIT 1",
                      parametros)
                  == null,
              "Já existe IA ativa neste canal, setor e ativador");
        }
      }
      case "Webhook" -> {
        url(texto(dados, "WebhookURL"));
        opcao(
            dados,
            "WebhookEvento",
            "mensagem_recebida",
            "status_canal",
            "chatbot_externo_assumido");
        exigir(!texto(dados, "WebhookEvento").isBlank(), "Selecione o evento do webhook");
        if (id == null) valores.putIfAbsent("WebhookTodosCanais", dados.get("CanalId") == null);
        exigir(
            Boolean.TRUE.equals(
                    valores.getOrDefault("WebhookTodosCanais", dados.get("WebhookTodosCanais")))
                || dados.get("CanalId") != null,
            "Selecione o canal do webhook");
      }
      case "Integracao" -> {
        exigir(
            !texto(dados, "IntegracaoNome").isBlank()
                && (dados.get("IntegracaoDados") instanceof Map<?, ?>
                    || dados.get("IntegracaoDados") instanceof List<?>),
            "Informe o nome e os dados da integração");
      }
      case "ChaveAPI" -> {
        exigir(!texto(dados, "ChaveAPIDescricao").isBlank(), "Informe a descrição da chave");
        if (id == null) {
          valores.put("ChaveAPIValor", UUID.randomUUID());
          valores.put(
              "ChaveAPIResponsavelCriacao",
              contexto.usuario() == 0 ? "Empresa" : "Usuário " + contexto.usuario());
        }
      }
      default -> {}
    }
  }

  private void unico(
      String textoDoRegistro, String campo, Object valor, Long id, ServicoFirma.Contexto contexto) {
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of("k", textoDoRegistro + ":" + contexto.cliente() + ":" + valor));
    String esquema = textoDoRegistro.equals("Usuario") ? "firma_identidade" : "firma",
        empresa = textoDoRegistro.equals("Usuario") ? "UsuarioClienteId" : "ClienteId";
    var parametros =
        new HashMap<String, Object>(
            Map.of("v", valor, "c", contexto.cliente(), "id", id == null ? 0 : id));
    exigir(
        banco.um(
                "SELECT id FROM "
                    + esquema
                    + ".\""
                    + textoDoRegistro
                    + "\" WHERE \""
                    + campo
                    + "\"=:v AND \""
                    + empresa
                    + "\"=:c AND id<>:id LIMIT 1",
                parametros)
            == null,
        "Já existe um registro com este valor de " + campo);
  }

  private void unicoVinculo(
      String nomeTabela,
      String primeiroCampo,
      String segundoCampo,
      Map<String, Object> dadosVinculo,
      Long id,
      ServicoFirma.Contexto contexto) {
    exigir(
        dadosVinculo.get(primeiroCampo) instanceof Number
            && dadosVinculo.get(segundoCampo) instanceof Number,
        "Selecione os dois registros do vínculo");
    banco.um(
        "SELECT pg_advisory_xact_lock(hashtextextended(:k,0))",
        Map.of(
            "k",
            nomeTabela
                + ":"
                + dadosVinculo.get(primeiroCampo)
                + ":"
                + dadosVinculo.get(segundoCampo)));
    String esquema = nomeTabela.equals("AtendenteSetor") ? "firma" : "firma_identidade";
    exigir(
        banco.um(
                "SELECT id FROM "
                    + esquema
                    + ".\""
                    + nomeTabela
                    + "\" WHERE \""
                    + primeiroCampo
                    + "\"=:a AND \""
                    + segundoCampo
                    + "\"=:b AND id<>:id",
                Map.of(
                    "a",
                    dadosVinculo.get(primeiroCampo),
                    "b",
                    dadosVinculo.get(segundoCampo),
                    "id",
                    id == null ? 0 : id))
            == null,
        "Este vínculo já existe");
  }
}
