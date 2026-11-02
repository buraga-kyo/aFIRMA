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

/** Persistência sobre nomes, tipos e relacionamentos do Autentica, sem um segundo modelo de CRUD. */
@Service
public class ServicoAutentica {
  public record Contexto(long empresa,long cliente,long repositorio,long usuario) {}
  private final BancoAssineai banco;
  private final NamedParameterJdbcTemplate jdbc;
  private final IdentidadeAssineai identidades;
  private final EspelhoAutentica espelho;
  private final ObjectMapper json;
  private final RegrasAutentica regras;
  private final List<Map<String,Object>> modulos,tabelas,relacoes;
  private final Map<String,Object> carga;

  @SuppressWarnings("unchecked")
  public ServicoAutentica(BancoAssineai banco,NamedParameterJdbcTemplate jdbc,IdentidadeAssineai identidades,ObjectMapper json,EspelhoAutentica espelho,RegrasAutentica regras) throws Exception {
    this.regras=regras;this.banco=banco;this.jdbc=jdbc;this.identidades=identidades;this.json=json;this.espelho=espelho;
    var esquema=json.readValue(new ClassPathResource("autentica/esquema.json").getInputStream(),Map.class);
    tabelas=(List<Map<String,Object>>)esquema.get("tabelas");relacoes=(List<Map<String,Object>>)esquema.get("relacionamentos");
    carga=json.readValue(new ClassPathResource("autentica/carga-inicial.json").getInputStream(),Map.class);
    modulos=json.readValue(new ClassPathResource("autentica/modulos.json").getInputStream(),List.class);
  }
  @Transactional
  public Contexto contexto(String identidade) {
    var empresa=identidades.empresa(identidade);long id=BancoAssineai.id(empresa,"EmpresaId");
    // Serializa o vínculo inicial quando duas abas são abertas na mesma conta.
    banco.um("SELECT pg_advisory_xact_lock(:id)",Map.of("id",id));
    var vinculo=banco.um("SELECT * FROM firma_autentica_empresa WHERE empresa_id=:id",Map.of("id",id));
    if(vinculo==null){
      var p=new HashMap<String,Object>();p.put("nome",Objects.toString(empresa.get("EmpresaNome"),Objects.toString(empresa.get("EmpresaUsuario"),"Empresa")));p.put("email",empresa.get("EmpresaEmail"));p.put("doc",empresa.get("EmpresaCNPJ"));
      long cliente=((Number)banco.um("INSERT INTO autentica.\"Cliente\"(\"ClienteNome\",\"ClienteEmail\",\"ClienteCpfCnpj\",\"ClienteStatus\",\"createdAt\",\"updatedAt\") VALUES(:nome,:email,:doc,true,now(),now()) RETURNING id",p).get("id")).longValue();
      long repo=((Number)banco.um("INSERT INTO autentica_identidade.\"Repositorio\"(\"RepositorioNome\",\"createdAt\",\"updatedAt\") VALUES(:nome,now(),now()) RETURNING id",p).get("id")).longValue();
      banco.executar("INSERT INTO firma_autentica_empresa VALUES(:e,:c,:r)",Map.of("e",id,"c",cliente,"r",repo));
      return preparar(new Contexto(id,cliente,repo,identidade.startsWith("autentica:")?Long.parseLong(identidade.substring(10)):0));
    }
    return preparar(new Contexto(id,((Number)vinculo.get("cliente_id")).longValue(),((Number)vinculo.get("repositorio_id")).longValue(),identidade.startsWith("autentica:")?Long.parseLong(identidade.substring(10)):0));
  }
  @SuppressWarnings("unchecked")
  private Contexto preparar(Contexto c) {
    if(((Number)banco.um("SELECT count(*) n FROM autentica_identidade.\"Perfil\" WHERE \"RepositorioId\"=:r",Map.of("r",c.repositorio())).get("n")).longValue()>0)return c;
    var perfis=new HashMap<String,Object>();var permissoes=new HashMap<String,Object>();
    for(var p:(List<Map<String,Object>>)carga.get("perfis"))perfis.put(p.get("Nome").toString(),banco.um("INSERT INTO autentica_identidade.\"Perfil\"(\"PerfilNome\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:n,:r,now(),now()) RETURNING id",Map.of("n",p.get("Nome"),"r",c.repositorio())).get("id"));
    for(var p:(List<Map<String,Object>>)carga.get("permissoes"))permissoes.put(p.get("Nome")+":"+p.get("Acao"),banco.um("INSERT INTO autentica_identidade.\"Permissao\"(\"PermissaoNome\",\"PermissaoAcao\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:n,:a,:r,now(),now()) RETURNING id",Map.of("n",p.get("Nome"),"a",p.get("Acao"),"r",c.repositorio())).get("id"));
    for(var p:(List<Map<String,Object>>)carga.get("vinculos")){Object perfil=perfis.get(p.get("PerfilNome")),permissao=permissoes.get(p.get("PermissaoNome")+":"+p.get("PermissaoAcao"));if(perfil!=null&&permissao!=null)banco.executar("INSERT INTO autentica_identidade.\"PerfilPermissao\"(\"PerfilId\",\"PermissaoId\",\"RepositorioId\",\"createdAt\",\"updatedAt\") VALUES(:p,:a,:r,now(),now())",Map.of("p",perfil,"a",permissao,"r",c.repositorio()));}
    return c;
  }
  public Map<String,Object> modulo(String slug){return modulos.stream().filter(m->m.get("slug").equals(slug)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
  private Map<String,Object> tabela(String nome){return tabelas.stream().filter(t->t.get("nome").equals(nome)).findFirst().orElseThrow();}
  @SuppressWarnings("unchecked") private List<Map<String,Object>> campos(String nome){return (List<Map<String,Object>>)tabela(nome).get("colunas");}
  public String q(String s){return "\""+s+"\"";}
  private String nome(String tabela){return q(this.tabela(tabela).get("schema").toString())+"."+q(tabela);}
  private boolean segredo(String c){return c.matches(".*(Senha|Token|JWT|Creds|APIKey|RandomBytes|RecSenha|MensagemRAW|KeyWhatsApp|ClienteMetaToken|ChaveAPIValor).*");}
  private boolean gravavelSegredo(String c){return Set.of("UsuarioSenha","InteligenciaArtificialProvedorAPIKey","ClienteMetaToken").contains(c);}
  private boolean gerenciado(String c){return Set.of("ChaveAPIValor","ChaveAPIResponsavelCriacao","ChatbotDefinicaoJSON","ChatbotDefinicaoMetadata","MensagemTemplateMetaId","MensagemTemplateStatusMeta","MensagemTemplateMotivoRejeicaoMeta").contains(c)|| c.endsWith("GUID")||Set.of("id","ClienteId","RepositorioId","UsuarioClienteId","createdAt","updatedAt","UsuarioNomeCompleto","PlanoId","ListaContatoQuantidadeContatos").contains(c);}
  public String escopo(String t,String a) {
    if(t.equals("Cliente"))return a+".id=:cliente";
    if(t.equals("Plano"))return "FALSE";
    if(t.equals("Mensagem"))return "EXISTS(SELECT 1 FROM autentica.\"Canal\" ca WHERE ca.id="+a+".\"CanalId\" AND ca.\"ClienteId\"=:cliente)";
    if(t.equals("Repositorio"))return a+".id=:repositorio";
    if(t.equals("Usuario"))return a+".\"UsuarioClienteId\"=:cliente AND "+a+".\"RepositorioId\"=:repositorio";
    if(tabela(t).get("schema").equals("autentica_identidade"))return a+".\"RepositorioId\"=:repositorio";
    if(campos(t).stream().anyMatch(c->c.get("nome").equals("ClienteId")))return a+".\"ClienteId\"=:cliente";
    String via=switch(t){case "Setor","ChatbotDefinicao","ChatbotMetadata"->"Canal";case "Mensagem","AtendimentoAcao","AtendimentoTag"->"Atendimento";case "AtendenteSetor","FCMToken"->"Atendente";default->null;};
    String direta=via==null?"FALSE":"EXISTS(SELECT 1 FROM autentica."+q(via)+" v WHERE v.id="+a+"."+q(via+"Id")+" AND v.\"ClienteId\"=:cliente)";
    return "("+direta+" OR EXISTS(SELECT 1 FROM firma_autentica_vinculo o WHERE o.destino='"+t+"' AND o.destino_id="+a+".id AND o.cliente_id=:cliente))";
  }
  private Map<String,Object> params(Contexto c){return new HashMap<>(Map.of("cliente",c.cliente(),"repositorio",c.repositorio(),"usuario",c.usuario()));}
  public String escopo(String t,String a,Contexto c){
    String base=escopo(t,a);if(c.usuario()==0||pode(c,"atendenteww","read"))return base;
    String operador="SELECT at.id FROM autentica.\"Atendente\" at WHERE at.\"AtendenteAutenticaAIUsuarioId\"=:usuario AND at.\"ClienteId\"=:cliente";
    String setores="SELECT ast.\"SetorId\" FROM autentica.\"AtendenteSetor\" ast WHERE ast.\"AtendenteId\" IN("+operador+")";
    return base+switch(t){
      case "Atendente"->" AND ("+a+".id IN("+operador+") OR "+a+".id IN(SELECT ast.\"AtendenteId\" FROM autentica.\"AtendenteSetor\" ast WHERE ast.\"SetorId\" IN("+setores+")))";
      case "Setor"->" AND "+a+".id IN("+setores+")";
      case "Canal"->" AND "+a+".id IN(SELECT s.\"CanalId\" FROM autentica.\"Setor\" s WHERE s.id IN("+setores+"))";
      case "Atendimento"->" AND ("+a+".\"AtendenteId\" IN("+operador+") OR ("+a+".\"AtendenteId\" IS NULL AND "+a+".\"SetorId\" IN("+setores+")))";
      case "Contato"->" AND EXISTS(SELECT 1 FROM autentica.\"Atendimento\" atd WHERE atd.\"ContatoId\"="+a+".id AND (atd.\"AtendenteId\" IN("+operador+") OR atd.\"SetorId\" IN("+setores+")))";
      default->"";
    };
  }
  public boolean pode(Contexto c,String permissao,String acao) {
    if(c.usuario()==0)return true;
    return ((Number)banco.um("SELECT count(*) n FROM autentica_identidade.\"Permissao\" p WHERE p.\"RepositorioId\"=:r AND ((p.\"PermissaoNome\"='all' AND p.\"PermissaoAcao\"='manage') OR (p.\"PermissaoNome\"=:nome AND p.\"PermissaoAcao\"=:acao)) AND (p.id IN (SELECT pp.\"PermissaoId\" FROM autentica_identidade.\"PerfilPermissao\" pp JOIN autentica_identidade.\"Usuario\" u ON u.\"PerfilId\"=pp.\"PerfilId\" AND u.\"RepositorioId\"=pp.\"RepositorioId\" WHERE u.id=:u) OR p.id IN(SELECT up.\"PermissaoId\" FROM autentica_identidade.\"UsuarioPermissao\" up WHERE up.\"UsuarioId\"=:u AND up.\"RepositorioId\"=:r))",Map.of("r",c.repositorio(),"u",c.usuario(),"nome",permissao,"acao",acao)).get("n")).longValue()>0;
  }
  public void autorizar(Contexto c,String slug,String acao) {
    String t=modulo(slug).get("tabela").toString().toLowerCase(Locale.ROOT);
    String p=Set.of("webhooks","chaves","integracoes","ia","bots").contains(slug)?switch(slug){case "webhooks"->"webhook";case "chaves"->"chaveapi";case "integracoes"->"integracao";case "ia"->"chatinteligente";default->"chatbot";}:t;
    if(pode(c,p+(acao.equals("read")?"ww":"detail"),acao)||pode(c,p,acao))return;
    if(acao.equals("read")&&(slug.equals("cliente")||Set.of("atendimentos","contatos","canais","setores","atendentes","tags","respostas","ia","templates").contains(slug)&&pode(c,"chat","read")))return;
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seu perfil não permite esta operação");
  }
  public List<Map<String,Object>> catalogo(Contexto c){return catalogo().stream().filter(m->{try{autorizar(c,m.get("slug").toString(),"read");return true;}catch(ResponseStatusException e){return false;}}).map(m->{Map<String,Object> n=new LinkedHashMap<>(m);try{autorizar(c,m.get("slug").toString(),"update");}catch(ResponseStatusException e){n.put("editavel",false);}String slug=m.get("slug").toString();var acoes=new ArrayList<String>();
      try{autorizar(c,slug,"insert");n.put("criavel",m.get("editavel"));}catch(ResponseStatusException e){n.put("criavel",false);}
      boolean editar=Boolean.TRUE.equals(n.get("editavel"));
      if(slug.equals("canais")&&editar)acoes.addAll(List.of("conectar","qr","estado","importar","desconectar"));
      if(slug.equals("templates")&&editar)acoes.addAll(List.of("publicar","sincronizar","excluir-meta"));if(slug.equals("ia"))acoes.add("execucoes-ia");
      if(slug.equals("disparos")){acoes.add("detalhes");if(editar)acoes.addAll(List.of("cancelar","repetir-falhas"));n.put("editavel",false);}
      if(slug.equals("bots"))acoes.add("execucoes");
      if(slug.equals("webhooks"))acoes.add("entregas");
      if(slug.equals("integracoes"))acoes.add("consultar");
      if(slug.equals("sessoes")&&(c.usuario()==0||pode(c,"all","manage")||pode(c,"usuariodetail","update")))acoes.add("revogar");
      try{autorizar(c,slug,"delete");if(!Set.of("cliente","atendimentos","disparos","sessoes").contains(slug))acoes.add("remover");}catch(ResponseStatusException e){}
      n.put("acoes",acoes);return n;}).toList();}
  public List<Map<String,Object>> catalogo(){
    var result=new ArrayList<Map<String,Object>>();
    for(var m:modulos){var copia=new LinkedHashMap<>(m);String t=m.get("tabela").toString();var cs=new ArrayList<Map<String,Object>>();
      for(var campo:campos(t)){String n=campo.get("nome").toString();if(n.equals("UsuarioSenha")||gerenciado(n)||segredo(n)&&!gravavelSegredo(n)||t.equals("DisparoMassa")&&!Set.of("CanalId","ListaContatoId","MensagemTemplateId","SetorId","DisparoMassaDataHoraInicio","DisparoMassaMensagensPorDisparos","DisparoMassaIntervaloEntreDisparos","DisparoMassaMaxMensagemDia","DisparoMassaUltimoHorario","DisparoMassaHoraReinicio","DisparoMassaDiasEnvio","DisparoMassaConteudo","DisparoMassaColunaDestinatario","DisparoMassaTemplateVariaveis","DisparoMassaDesejaGerarAtendimento").contains(n))continue;var f=new LinkedHashMap<>(campo);if(segredo(n))f.put("tipo","PASSWORD");
        relacoes.stream().filter(r->r.get("schema").equals(tabela(t).get("schema"))&&r.get("tabela").equals(t)&&r.get("coluna").equals(n)).findFirst().ifPresent(r->{f.put("referencia",r.get("alvo"));f.put("moduloReferencia",modulos.stream().filter(mm->mm.get("tabela").equals(r.get("alvo"))).map(mm->mm.get("slug")).findFirst().orElse(""));});
        cs.add(f);
      }
      if(t.equals("Usuario"))cs.add(Map.of("nome","UsuarioSenha","tipo","PASSWORD","obrigatorio",false));
      copia.put("campos",cs);result.add(copia);
    }return result;
  }
  public Map<String,Object> listar(String slug,Contexto c,int pagina,int tamanho,String busca) {return listar(slug,c,pagina,tamanho,busca,null);}
  public Map<String,Object> listar(String slug,Contexto c,int pagina,int tamanho,String busca,String estado) {return listar(slug,c,pagina,tamanho,busca,estado,Map.of());}
  public Map<String,Object> listar(String slug,Contexto c,int pagina,int tamanho,String busca,String estado,Map<String,String> filtros) {
    autorizar(c,slug,"read");String t=modulo(slug).get("tabela").toString();var p=params(c);String where=escopo(t,"t",c);
    p.put("tabelaExclusao",t);where+=" AND NOT EXISTS(SELECT 1 FROM firma_autentica_exclusao x WHERE x.cliente_id=:cliente AND x.tabela=:tabelaExclusao AND x.registro_id=t.id)";
    if(t.equals("Atendimento")&&estado!=null&&!estado.isBlank()){if(!Set.of("pendente","emandamento","finalizado","bot").contains(estado))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Situação inválida");p.put("estado",estado);where+=estado.equals("bot")?" AND COALESCE(t.\"AtendimentoBOTExterno\",false) AND t.\"AtendimentoStatus\"='emandamento'":" AND t.\"AtendimentoStatus\"=:estado"+(estado.equals("emandamento")?" AND NOT COALESCE(t.\"AtendimentoBOTExterno\",false)":"");}
    if(t.equals("Atendimento"))for(var e:filtros.entrySet()){
      String chave=e.getKey(),valor=e.getValue();if(valor==null||valor.isBlank())continue;
      if(Set.of("CanalId","SetorId","AtendenteId","TagId").contains(chave)){long id;try{id=Long.parseLong(valor);}catch(NumberFormatException erro){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filtro inválido");}registro(chave.substring(0,chave.length()-2),id,c);p.put("f"+chave,id);where+=chave.equals("TagId")?" AND EXISTS(SELECT 1 FROM autentica.\"AtendimentoTag\" ta WHERE ta.\"AtendimentoId\"=t.id AND ta.\"TagId\"=:fTagId)":" AND t."+q(chave)+"=:f"+chave;}
      else if(chave.equals("inicio")||chave.equals("fim")){try{var dia=java.time.LocalDate.parse(valor);p.put("f"+chave,(chave.equals("fim")?dia.plusDays(1):dia).atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toOffsetDateTime());where+=" AND t.\"createdAt\""+(chave.equals("inicio")?">=":"<")+":f"+chave;}catch(java.time.format.DateTimeParseException erro){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Data do filtro inválida");}}
    }
    var visiveis=campos(t).stream().map(x->x.get("nome").toString()).filter(n->!segredo(n)).toList();
    if(busca!=null&&!busca.isBlank()){p.put("busca","%"+busca.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");where+=" AND ("+String.join(" OR ",visiveis.stream().filter(n->!gerenciado(n)).map(n->"CAST(t."+q(n)+" AS text) ILIKE :busca").toList())+(t.equals("Atendimento")?" OR EXISTS(SELECT 1 FROM autentica.\"Contato\" co WHERE co.id=t.\"ContatoId\" AND (co.\"ContatoNome\" ILIKE :busca OR co.\"ContatoNumero\" ILIKE :busca))":"")+")";}
    var terreno=FiltroTratorAutentica.preparar(json,filtros,visiveis,campos(t),p);where+=terreno.where();
    long total=((Number)banco.um("SELECT count(*) total FROM "+nome(t)+" t WHERE "+where,p).get("total")).longValue();
    p.put("limite",tamanho);p.put("offset",(pagina-1)*tamanho);
    String relacionados=t.equals("Atendimento")?",(SELECT co.\"ContatoNome\" FROM autentica.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoNome\",(SELECT co.\"ContatoNumero\" FROM autentica.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoNumero\",(SELECT co.\"ContatoEmail\" FROM autentica.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoEmail\",(SELECT co.\"ContatoAvatar\" FROM autentica.\"Contato\" co WHERE co.id=t.\"ContatoId\") AS \"ContatoAvatar\",(SELECT ca.\"CanalNome\" FROM autentica.\"Canal\" ca WHERE ca.id=t.\"CanalId\") AS \"CanalNome\",(SELECT CASE WHEN COALESCE(me.\"MensagemDeletada\",false) THEN 'Mensagem excluída' ELSE COALESCE(NULLIF(me.\"MensagemTexto\",''),'Arquivo anexado') END FROM autentica.\"Mensagem\" me WHERE me.id=t.\"MensagemId\") AS \"UltimaMensagem\"":"";
    var itens=banco.listar("SELECT "+String.join(",",visiveis.stream().map(n->"t."+q(n)).toList())+relacionados+" FROM "+nome(t)+" t WHERE "+where+" ORDER BY "+terreno.ordem()+" LIMIT :limite OFFSET :offset",p);
    return Map.of("itens",itens,"total",total,"pagina",pagina,"tamanho",tamanho);
  }
  public Map<String,Object> registro(String t,long id,Contexto c){var p=params(c);p.put("id",id);var row=banco.um("SELECT t.* FROM "+nome(t)+" t WHERE t.id=:id AND "+escopo(t,"t",c),p);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro não encontrado nesta empresa");return row;}
  public Map<String,Object> publico(String slug,long id,Contexto c){autorizar(c,slug,"read");var m=new LinkedHashMap<>(registro(modulo(slug).get("tabela").toString(),id,c));m.keySet().removeIf(this::segredo);return m;}
  private void validarRelacao(String t,String coluna,Object valor,Contexto c) {
    var ref=relacoes.stream().filter(r->r.get("schema").equals(tabela(t).get("schema"))&&r.get("tabela").equals(t)&&r.get("coluna").equals(coluna)).findFirst();
    if(ref.isEmpty()||valor==null)return;
    if(!(valor instanceof Number n))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha um registro válido para "+coluna);
    registro(ref.get().get("alvo").toString(),n.longValue(),c);
  }
  @Transactional
  public Map<String,Object> salvar(String slug,Long id,Map<String,Object> entrada,Contexto c){
    autorizar(c,slug,id==null?"insert":"update");var mod=modulo(slug);if(!Boolean.TRUE.equals(mod.get("editavel")))throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,"Este módulo é de consulta");
    String t=mod.get("tabela").toString();if(t.equals("DisparoMassa"))throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,"Use a operação de agendamento de disparos");if(t.equals("Cliente")){if(id!=null&&id!=c.cliente())throw new ResponseStatusException(HttpStatus.NOT_FOUND);id=c.cliente();}
    if(id!=null&&banco.um("SELECT registro_id FROM firma_autentica_exclusao WHERE cliente_id=:c AND tabela=:t AND registro_id=:id",Map.of("c",c.cliente(),"t",t,"id",id))!=null)throw new ResponseStatusException(HttpStatus.GONE,"Este registro foi excluído");
    var anterior=id==null?Map.<String,Object>of():registro(t,id,c);
    var valores=new LinkedHashMap<String,Object>();
    var permitidos=new HashMap<String,Map<String,Object>>();campos(t).forEach(f->permitidos.put(f.get("nome").toString(),f));
    for(var item:entrada.entrySet()){
      String coluna=item.getKey();if(gerenciado(coluna)||!permitidos.containsKey(coluna)||(segredo(coluna)&&!gravavelSegredo(coluna)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Campo não editável: "+coluna);
      Object valor=item.getValue();String tipo=permitidos.get(coluna).get("tipo").toString();
      if(valor!=null){if(tipo.startsWith("VARCHAR")){int max=Integer.parseInt(tipo.substring(tipo.indexOf('(')+1,tipo.indexOf(')')));if(!(valor instanceof String s)||s.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tamanho inválido para "+coluna);}
        if(tipo.equals("BOOLEAN")&&!(valor instanceof Boolean))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valor booleano inválido");
        if(tipo.equals("INTEGER")&&!(valor instanceof Number n&&n.doubleValue()==n.longValue()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Número inteiro inválido");
        if(coluna.equals("UsuarioSenha")){if(!(valor instanceof String s)||s.length()<8||s.length()>256)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A senha deve ter entre 8 e 256 caracteres");var credencial=SenhaAutentica.criar((String)valor);valor=credencial.hash();valores.put("UsuarioRandomBytes",credencial.sal());valores.put("UsuarioTipoAutenticacao","local");}
      }
      validarRelacao(t,coluna,valor,c);valores.put(coluna,valor);
    }
    regras.aplicar(t,id,valores,anterior,c);
    if(permitidos.containsKey("ClienteId"))valores.put("ClienteId",c.cliente());
    if(permitidos.containsKey("RepositorioId"))valores.put("RepositorioId",c.repositorio());
    if(t.equals("ListaContato")&&valores.containsKey("ListaContatoContatos")){Object itens=valores.get("ListaContatoContatos");if(itens!=null&&!(itens instanceof List<?>))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Os contatos devem formar uma lista");valores.put("ListaContatoQuantidadeContatos",itens==null?0:((List<?>)itens).size());}
    if(t.equals("Atendente")&&valores.get("AtendenteAutenticaAIUsuarioId") instanceof Number uid)registro("Usuario",uid.longValue(),c);
    if(t.equals("Setor")&&id==null&&!valores.containsKey("CanalId"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecione o canal do setor");
    if(t.equals("Usuario")){valores.put("UsuarioClienteId",c.cliente());valores.put("UsuarioNomeCompleto",(Objects.toString(valores.getOrDefault("UsuarioPrimeiroNome",anterior.get("UsuarioPrimeiroNome")),"")+" "+Objects.toString(valores.getOrDefault("UsuarioUltimoNome",anterior.get("UsuarioUltimoNome")),"")).strip());}
    if(t.equals("Atendimento")&&valores.containsKey("AtendimentoStatus")&&!Set.of("pendente","emandamento","finalizado").contains(valores.get("AtendimentoStatus")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Situação inválida");
    if(permitidos.containsKey(t+"GUID")&&id==null)valores.put(t+"GUID",UUID.randomUUID());
    valores.put("updatedAt",OffsetDateTime.now());if(id==null)valores.put("createdAt",OffsetDateTime.now());
    var p=params(c);var expressoes=new LinkedHashMap<String,String>();int indice=0;
    for(var e:valores.entrySet()){String chave="v"+indice++;String tipo=permitidos.get(e.getKey()).get("tipo").toString();Object v=e.getValue();p.put(chave,(tipo.equals("JSONB")||tipo.equals("JSON"))&&v!=null?json.writeValueAsString(v):v);expressoes.put(q(e.getKey()),(tipo.equals("JSONB")||tipo.equals("JSON"))?"CAST(:"+chave+" AS "+tipo.toLowerCase(Locale.ROOT)+")":tipo.equals("UUID")?"CAST(:"+chave+" AS uuid)":tipo.equals("TIMESTAMP WITH TIME ZONE")?"CAST(:"+chave+" AS timestamptz)":":"+chave);}
    String sql;if(id==null)sql="INSERT INTO "+nome(t)+" ("+String.join(",",expressoes.keySet())+") VALUES ("+String.join(",",expressoes.values())+") RETURNING id";
    else{p.put("id",id);sql="UPDATE "+nome(t)+" t SET "+String.join(",",expressoes.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+" WHERE t.id=:id AND "+escopo(t,"t",c)+" RETURNING id";}
    long novo=((Number)banco.um(sql,p).get("id")).longValue();if(t.equals("Usuario")&&id!=null&&valores.containsKey("UsuarioSenha"))banco.executar("UPDATE autentica_identidade.\"Sessao\" SET \"SessaoStatus\"='F' WHERE \"UsuarioId\"=:id",Map.of("id",id));var row=new LinkedHashMap<>(registro(t,novo,c));espelho.salvar(t,row,c);Object chaveNova=t.equals("ChaveAPI")&&id==null?row.get("ChaveAPIValor"):null;row.keySet().removeIf(this::segredo);if(chaveNova!=null)row.put("chaveGerada",chaveNova);return row;
  }

  public Map<String,Object> operador(Contexto c){var a=banco.um("SELECT id,\"AtendenteNome\",\"AtendenteStatusChat\",\"AtendenteSobreChat\",\"AtendenteNotificacoes\" FROM autentica.\"Atendente\" WHERE \"AtendenteAutenticaAIUsuarioId\"=:u AND \"ClienteId\"=:c",Map.of("u",c.usuario(),"c",c.cliente()));return a==null?Map.of():a;}
  @Transactional public Map<String,Object> preferencias(Contexto c,Map<String,Object> p){var a=operador(c);if(a.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Seu usuário não possui cadastro de atendente");if(!Set.of("AtendenteNome","AtendenteStatusChat","AtendenteSobreChat","AtendenteNotificacoes").containsAll(p.keySet()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Preferência inválida");var alteracoes=new LinkedHashMap<String,Object>();for(var e:p.entrySet()){if(e.getKey().equals("AtendenteNotificacoes")){if(e.getValue()!=null&&!(e.getValue() instanceof Map<?,?>))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Preferências inválidas");}else if(e.getValue()!=null&&(!(e.getValue() instanceof String t)||t.length()>(e.getKey().equals("AtendenteNome")?70:e.getKey().equals("AtendenteSobreChat")?256:20)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Preferência muito longa");alteracoes.put(e.getKey(),e.getValue());}var params=new HashMap<String,Object>();var sets=new ArrayList<String>();int i=0;for(var e:alteracoes.entrySet()){String k="p"+i++;params.put(k,e.getKey().equals("AtendenteNotificacoes")?json.writeValueAsString(e.getValue()):e.getValue());sets.add(q(e.getKey())+"="+(e.getKey().equals("AtendenteNotificacoes")?"CAST(:"+k+" AS jsonb)":":"+k));}if(sets.isEmpty())return a;params.put("id",a.get("id"));banco.executar("UPDATE autentica.\"Atendente\" SET "+String.join(",",sets)+",\"updatedAt\"=now() WHERE id=:id",params);espelho.salvar("Atendente",registro("Atendente",((Number)a.get("id")).longValue(),c),c);return operador(c);}
  public Map<String,Object> panorama(Contexto c){var r=new LinkedHashMap<String,Object>();for(String slug:List.of("contatos","canais","atendentes","atendimentos")){try{r.put(slug,listar(slug,c,1,1,"").get("total"));}catch(ResponseStatusException e){r.put(slug,0);}}return r;}
}
