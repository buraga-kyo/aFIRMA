package com.afirma.assina;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoNostrEmpresa {
  public static final String AUTORIZACAO =
      "Autorizo a FIRMA a publicar pelo perfil Nostr da empresa o perfil público e os compromissos"
          + " criptográficos dos aceites, sem publicar o PDF ou os dados pessoais dos"
          + " participantes.";
  private final JdbcTemplate banco;
  private final ServicoPublicacaoNostr publicacoes;
  private final ObjectMapper json;

  public ServicoNostrEmpresa(
      JdbcTemplate banco, ServicoPublicacaoNostr publicacoes, ObjectMapper json) {
    this.banco = banco;
    this.publicacoes = publicacoes;
    this.json = json;
  }

  private Map<String, Object> configuracao(long empresa) {
    var registros =
        banco.queryForList("SELECT * FROM firma_nostr_configuracao WHERE empresa_id=?", empresa);
    return registros.isEmpty() ? null : registros.getFirst();
  }

  public Map<String, Object> estado(long empresa) {
    var config = configuracao(empresa);
    var r = new LinkedHashMap<String, Object>();
    r.put("empresaId", empresa);
    r.put("servicoConfigurado", publicacoes.configurado(empresa));
    r.put("configurada", config != null);
    r.put("ativa", config != null && Boolean.TRUE.equals(config.get("ativa")));
    r.put("autorizacao", AUTORIZACAO);
    if (config != null)
      for (var par :
          Map.of(
                  "chavePublica",
                  "chave_publica",
                  "referencia",
                  "referencia",
                  "nome",
                  "nome",
                  "sobre",
                  "sobre")
              .entrySet()) r.put(par.getKey(), config.get(par.getValue()));
    return r;
  }

  public Map<String, Object> identidades(long empresa) {
    return publicacoes.identidades(empresa);
  }

  @Transactional
  public Map<String, Object> salvar(
      long empresa,
      String ator,
      String referencia,
      String nome,
      String sobre,
      boolean ativa,
      String autorizacao) {
    if (referencia == null
        || !referencia.matches("[a-zA-Z0-9_-]{1,80}")
        || referencia.startsWith("nsec")
        || referencia.matches("[0-9a-fA-F]{64}")
        || nome == null
        || nome.isBlank()
        || nome.length() > 100
        || sobre == null
        || sobre.length() > 500)
      throw erro("Informe o assinador, o nome público e uma descrição de até 500 caracteres");
    if (ativa && !AUTORIZACAO.equals(autorizacao))
      throw erro("Confirme a autorização para publicações públicas automáticas");
    travar(empresa);
    if (ativa) conferirLimite(empresa);
    var resposta = publicacoes.conferirIdentidade(empresa, referencia);
    String chave = Objects.toString(resposta.get("chavePublica"), "");
    if (!chave.matches("[0-9a-f]{64}")) throw erro("Chave do assinador inválida");
    banco.update(
        "INSERT INTO"
            + " firma_nostr_configuracao(empresa_id,chave_publica,referencia,nome,sobre,ativa,atualizado_por)"
            + " VALUES(?,?,?,?,?,?,?) ON CONFLICT(empresa_id) DO UPDATE SET"
            + " chave_publica=excluded.chave_publica,referencia=excluded.referencia,nome=excluded.nome,sobre=excluded.sobre,ativa=excluded.ativa,atualizado_por=excluded.atualizado_por,atualizado_em=now()",
        empresa,
        chave,
        referencia,
        nome.trim(),
        sobre,
        ativa,
        ator);
    banco.update(
        "UPDATE firma_nostr_publicacao SET"
            + " publicacao_estado='SUSPENSA',erro_codigo='IDENTIDADE_ALTERADA' WHERE empresa_id=?"
            + " AND (chave_publica<>? OR referencia<>?) AND publicacao_estado='PENDENTE'",
        empresa,
        chave,
        referencia);
    auditar(empresa, ator, ativa ? "AUTOMACAO_AUTORIZADA" : "CONFIGURACAO_SALVA", chave);
    if (ativa) {
      var pedido = base(empresa, referencia, "PERFIL");
      pedido.put("nome", nome.trim());
      pedido.put("sobre", sobre);
      inserir(empresa, ator, chave, referencia, "PERFIL", UUID.randomUUID().toString(), pedido);
    }
    return estado(empresa);
  }

  @Transactional
  public void pausar(long empresa, String ator) {
    banco.update(
        "UPDATE firma_nostr_configuracao SET ativa=false,atualizado_por=?,atualizado_em=now() WHERE"
            + " empresa_id=?",
        ator,
        empresa);
    auditar(empresa, ator, "AUTOMACAO_PAUSADA", null);
  }

  @Transactional
  public UUID enfileirar(long empresa, String ator, Map<String, Object> aceite) {
    if (aceite == null || json.writeValueAsBytes(aceite).length > 48000)
      throw erro("Prova Nostr inválida ou excessiva");
    travar(empresa);
    var config = configuracao(empresa);
    if (config == null || !Boolean.TRUE.equals(config.get("ativa")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Ative a identidade Nostr da empresa antes de publicar");
    String referencia = config.get("referencia").toString();
    String evento = publicacoes.conferirAceite(empresa, referencia, aceite);
    if (!evento.matches("[0-9a-f]{64}")) throw erro("Evento Nostr inválido");
    var anteriores =
        banco.queryForList(
            "SELECT id FROM firma_nostr_publicacao WHERE empresa_id=? AND tipo='COMPROVANTE' AND"
                + " deduplicacao=?",
            empresa,
            evento);
    if (!anteriores.isEmpty()) return (UUID) anteriores.getFirst().get("id");
    conferirLimite(empresa);
    byte[] aleatorio = new byte[32];
    new SecureRandom().nextBytes(aleatorio);
    var pedido = base(empresa, referencia, "COMPROVANTE");
    pedido.put("aceite", aceite);
    pedido.put("sal", HexFormat.of().formatHex(aleatorio));
    return inserir(
        empresa,
        ator,
        config.get("chave_publica").toString(),
        referencia,
        "COMPROVANTE",
        evento,
        pedido);
  }

  private void travar(long empresa) {
    banco.queryForList(
        "SELECT pg_advisory_xact_lock(hashtext('firma.nostr'),hashtext(?))",
        Long.toString(empresa));
  }

  private void conferirLimite(long empresa) {
    Long quantidade =
        banco.queryForObject(
            "SELECT count(*) FROM firma_nostr_publicacao WHERE empresa_id=? AND"
                + " criado_em>now()-interval '1 day'",
            Long.class,
            empresa);
    if (quantidade != null && quantidade >= 500)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Limite diário de publicações da empresa atingido");
  }

  private LinkedHashMap<String, Object> base(long empresa, String referencia, String tipo) {
    var p = new LinkedHashMap<String, Object>();
    p.put("empresaId", empresa);
    p.put("referencia", referencia);
    p.put("tipo", tipo);
    p.put("criadoEm", Instant.now().getEpochSecond());
    return p;
  }

  private UUID inserir(
      long empresa,
      String ator,
      String chave,
      String referencia,
      String tipo,
      String dedup,
      Map<String, Object> pedido) {
    UUID id = UUID.randomUUID();
    var ids =
        banco.queryForList(
            "INSERT INTO"
                + " firma_nostr_publicacao(id,empresa_id,chave_publica,referencia,tipo,deduplicacao,pedido,criado_por,anterioridade_estado)"
                + " VALUES(?,?,?,?,?,?,?::jsonb,?,?) ON CONFLICT(empresa_id,tipo,deduplicacao) DO"
                + " UPDATE SET deduplicacao=excluded.deduplicacao RETURNING id",
            id,
            empresa,
            chave,
            referencia,
            tipo,
            dedup,
            json.writeValueAsString(pedido),
            ator,
            tipo.equals("PERFIL") ? "NAO_APLICAVEL" : "PENDENTE");
    return (UUID) ids.getFirst().get("id");
  }

  public List<Map<String, Object>> listar(long empresa) {
    var lista =
        banco.queryForList(
            "SELECT"
                + " p.id,p.tipo,p.evento_id,p.publicacao_estado,p.anterioridade_estado,p.relays,p.bloco,p.altura,p.data_bloco,p.tentativas,p.erro_codigo,p.criado_em,(p.ots"
                + " IS NOT NULL) AS tem_ots,COALESCE(c.ativa,false) AS automacao_ativa FROM"
                + " firma_nostr_publicacao p LEFT JOIN firma_nostr_configuracao c ON"
                + " c.empresa_id=p.empresa_id WHERE p.empresa_id=? ORDER BY p.criado_em DESC LIMIT"
                + " 100",
            empresa);
    for (var item : lista)
      item.put("relays", json.readValue(item.get("relays").toString(), List.class));
    return lista;
  }

  public Map<String, Object> obter(long empresa, UUID id) {
    var registros =
        banco.queryForList(
            "SELECT * FROM firma_nostr_publicacao WHERE empresa_id=? AND id=?", empresa, id);
    if (registros.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicação não encontrada");
    return registros.getFirst();
  }

  public byte[] evento(long empresa, UUID id) {
    var registro = obter(empresa, id);
    if (registro.get("evento_canonico") == null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O evento ainda não foi assinado");
    return registro.get("evento_canonico").toString().getBytes(StandardCharsets.UTF_8);
  }

  public byte[] ots(long empresa, UUID id) {
    var registro = obter(empresa, id);
    if (!(registro.get("ots") instanceof byte[] bytes))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "O carimbo OTS ainda está pendente");
    return bytes;
  }

  public Map<String, Object> pacote(long empresa, UUID id) {
    var r = obter(empresa, id);
    var p = new LinkedHashMap<String, Object>();
    p.put("formato", "afirma.nostr.publicacao.v1");
    p.put("pedidoPrivado", json.readValue(r.get("pedido").toString(), Map.class));
    p.put("eventoCanonico", r.get("evento_canonico"));
    p.put("publicacaoEstado", r.get("publicacao_estado"));
    p.put("anterioridadeEstado", r.get("anterioridade_estado"));
    p.put("relays", json.readValue(r.get("relays").toString(), List.class));
    if (r.get("ots") instanceof byte[] dados)
      p.put("otsBase64", Base64.getEncoder().encodeToString(dados));
    p.put("bloco", r.get("bloco"));
    p.put("altura", r.get("altura"));
    p.put("dataBloco", r.get("data_bloco"));
    return p;
  }

  @Transactional
  public void tentarNovamente(long empresa, UUID id) {
    obter(empresa, id);
    banco.update(
        "UPDATE firma_nostr_publicacao SET proxima_tentativa=now(),erro_codigo=null WHERE"
            + " empresa_id=? AND id=? AND (lease_ate IS NULL OR lease_ate<now()) AND"
            + " publicacao_estado<>'SUSPENSA'",
        empresa,
        id);
  }

  private void auditar(long empresa, String ator, String acao, String chave) {
    banco.update(
        "INSERT INTO firma_nostr_auditoria(empresa_id,acao,chave_publica,responsavel)"
            + " VALUES(?,?,?,?)",
        empresa,
        acao,
        chave,
        ator);
  }

  private ResponseStatusException erro(String texto) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, texto);
  }
}
