import json
from pathlib import Path
root=Path(__file__).resolve().parents[2];meta=json.loads((root/'src/main/resources/autentica/esquema.json').read_text());tabelas={t['nome']:t for t in meta['tabelas'] if t['schema']=='autentica'}
# Os valores que excedem limites do modelo de produção permanecem integralmente em dados_origem.
maps=[
('empresa','Cliente',{'ClienteNome':'nome','ClienteStatus':'ativa','ClienteRazaoSocial':'razao_social','ClienteCpfCnpj':'documento','ClienteTelefone':'telefone','ClienteEmail':'email'},{}),
('chat_plano','Plano',{'PlanoNome':'nome','PlanoStatus':'ativo','PlanoValor':'valor_centavos','PlanoRenovacaoDias':'renovacao_dias','PlanoQtdDisparos':'limite_envios_dia','PlanoQtdCanais':'limite_canais','PlanoQtdAtendentes':'limite_atendentes','PlanoPermiteIntegracao':'permite_integracao','PlanoPermiteWebhook':'permite_webhook'},{}),
('chat_canal','Canal',{'CanalNome':'nome','CanalGUID':'id','CanalNumeroCelular':'telefone','CanalStatus':'conexao','CanalStatusUso':'ativo','CanalProvedorAPI':'provedor','CanalMsgEncerramentoAtendimento':'mensagem_encerramento','CanalMetaNumeroIdentificador':'identificador_meta'}, {'ClienteId':('empresa','empresa_id')}),
('chat_setor','Setor',{'SetorNome':'nome','SetorStatus':'ativo'},{}),
('chat_atendente','Atendente',{'AtendenteNome':'nome','AtendenteStatus':'ativo','AtendenteStatusChat':'disponibilidade'},{'ClienteId':('empresa','empresa_id')}),
('chat_contato','Contato',{'ContatoNome':'nome','ContatoNumero':'telefone','ContatoEmail':'email','ContatoInformacaoAdicional':'informacoes','ContatoAvatarChave':'avatar_chave','ContatoJidWhatsApp':'identificador_whatsapp'},{'ClienteId':('empresa','empresa_id')}),
('chat_atendimento','Atendimento',{'AtendimentoProtocolo':'protocolo','AtendimentoStatus':'estado','AtendimentoAvaliacao':'avaliacao','AtendimentoQtdMensagensNaoLidas':'mensagens_nao_lidas','AtendimentoBOTExterno':'bot_externo','AtendimentoDataFinalizado':'finalizado_em','AtendimentoReceptivo':'receptivo','AtendimentoDuracao':'duracao_segundos','AtendimentoTempoPendente':'espera_segundos','AtendimentoContextoIA':'contexto_ia','AtendimentoDataUltimaMensagemCliente':'ultima_mensagem_cliente_em'}, {'ClienteId':('empresa','empresa_id'),'CanalId':('chat_canal','canal_id'),'ContatoId':('chat_contato','contato_id'),'SetorId':('chat_setor','setor_id'),'AtendenteId':('chat_atendente','atendente_id')}),
('chat_arquivo','Arquivo',{'ArquivoNome':'nome','ArquivoMimeType':'tipo_mime','ArquivoSize':'tamanho','ArquivoChave':'chave_armazenamento','ArquivoExtensao':'extensao','ArquivoThumbnailChave':'miniatura_chave','ArquivoLatitude':'latitude','ArquivoLongitude':'longitude'},{}),
('chat_mensagem','Mensagem',{'MensagemTexto':'texto','MensagemTipo':'tipo','MensagemIdWhatsApp':'identificador_externo','MensagemACK':'confirmacao','MensagemEditada':'editada','MensagemDeletada':'excluida','MensagemReacao':'reacoes','MensagemInterativa':'conteudo_interativo'}, {'AtendimentoId':('chat_atendimento','atendimento_id'),'CanalId':('chat_canal','canal_id'),'ArquivoId':('chat_arquivo','arquivo_id')}),
('chat_atendente_setor','AtendenteSetor',{}, {'AtendenteId':('chat_atendente','atendente_id'),'SetorId':('chat_setor','setor_id')}),
('chat_acao','AtendimentoAcao',{'AtendimentoAcaoTipo':'tipo','AtendimentoAcaoTexto':'descricao'},{'AtendimentoId':('chat_atendimento','atendimento_id')}),
('chat_etiqueta','Tag',{'TagNome':'nome','TagStatus':'ativa'},{'ClienteId':('empresa','empresa_id')}),
('chat_atendimento_etiqueta','AtendimentoTag',{}, {'AtendimentoId':('chat_atendimento','atendimento_id'),'TagId':('chat_etiqueta','etiqueta_id')}),
('chat_mensagem_rapida','MensagemRapida',{'MensagemRapidaTexto':'texto'},{'ClienteId':('empresa','empresa_id'),'SetorId':('chat_setor','setor_id')}),
('chat_modelo_mensagem','MensagemTemplate',{'MensagemTemplateNome':'nome','MensagemTemplateCategoria':'categoria','MensagemTemplateCorpo':'corpo','MensagemTemplateStatusMeta':'estado','MensagemTemplateStatusUso':True,'MensagemTemplateCabecalhoConteudo':'cabecalho','MensagemTemplateRodape':'rodape','MensagemTemplateBotoes':'botoes','MensagemTemplateMetaId':'identificador_meta','MensagemTemplateCabecalhoTipo':'cabecalho_tipo','MensagemTemplateCabecalhoVariaveis':'cabecalho_variaveis','MensagemTemplateCorpoVariaveis':'corpo_variaveis','MensagemTemplateMotivoRejeicaoMeta':'motivo_rejeicao','MensagemTemplateMetodoEnvio':'metodo_envio'},{'ClienteId':('empresa','empresa_id'),'CanalId':('chat_canal','canal_id'),'SetorId':('chat_setor','setor_id')}),
('chat_lista_contatos','ListaContato',{'ListaContatoNome':'nome','ListaContatoStatus':'ativa','ListaContatoFonteContatos':'fonte'},{'ClienteId':('empresa','empresa_id')}),
('chat_integracao','Integracao',{'IntegracaoNome':'nome','IntegracaoDados':'configuracao','IntegracaoStatus':'ativa'},{'ClienteId':('empresa','empresa_id')}),
('chat_agente_ia','InteligenciaArtificial',{'InteligenciaArtificialNome':'nome','InteligenciaArtificialStatus':'ativa','InteligenciaArtificialProvedor':'provedor','InteligenciaArtificialModelo':'modelo','InteligenciaArtificialTextoPrompt':'instrucoes','InteligenciaArtificialAtivador':'ativador'},{'ClienteId':('empresa','empresa_id'),'CanalId':('chat_canal','canal_id'),'SetorId':('chat_setor','setor_id')}),
('chat_bot_definicao','ChatbotDefinicao',{'ChatbotDefinicaoNome':'nome','ChatbotDefinicaoStatus':'ativo','ChatbotDefinicaoFuncionamento':'funcionamento','ChatbotDefinicaoFlow':'fluxo','ChatbotDefinicaoProvedor':'provedor','ChatbotDefinicaoN8nURL':'endereco_externo'},{'CanalId':('chat_canal','canal_id')}),
('chat_webhook','Webhook',{'WebhookURL':'destino','WebhookTodosCanais':'todos_canais','WebhookEvento':'eventos'},{'ClienteId':('empresa','empresa_id')}),
('chat_webhook_entrega','WebhookLog',{'WebhookLogEvento':'evento','WebhookLogDados':'dados'},{'ClienteId':('empresa','empresa_id')}),
('chat_chave_integracao','ChaveAPI',{'ChaveAPIDescricao':'nome','ChaveAPIStatus':'ativa'},{'ClienteId':('empresa','empresa_id')}),
('chat_campanha','DisparoMassa',{'DisparoMassaDataHoraInicio':'agendada_em','DisparoMassaStatus':'estado','DisparoMassaIntervaloEntreDisparos':'intervalo_segundos','DisparoMassaConteudo':'texto','DisparoMassaMaxMensagemDia':'maximo_diario'}, {'ClienteId':('empresa','empresa_id'),'CanalId':('chat_canal','canal_id'),'MensagemTemplateId':('chat_modelo_mensagem','modelo_id'),'ListaContatoId':('chat_lista_contatos','lista_id')})]
sql=['''-- Mantém as tabelas anteriores e registra a correspondência de cada ID e de seus dados originais.
CREATE TABLE firma_autentica_vinculo (
 origem VARCHAR(100) NOT NULL, origem_id TEXT NOT NULL,
 destino VARCHAR(100) NOT NULL, destino_id INTEGER NOT NULL,
 cliente_id INTEGER REFERENCES autentica."Cliente"(id), dados_origem JSONB NOT NULL,
 PRIMARY KEY(origem,origem_id), UNIQUE(destino,destino_id)
);
CREATE TABLE firma_autentica_empresa (
 empresa_id INTEGER PRIMARY KEY REFERENCES "Empresas"("EmpresaId"),
 cliente_id INTEGER NOT NULL REFERENCES autentica."Cliente"(id),
 repositorio_id INTEGER REFERENCES autentica_identidade."Repositorio"(id)
);
CREATE FUNCTION firma_autentica_id(tabela TEXT, identificador TEXT) RETURNS INTEGER LANGUAGE SQL STABLE AS $$
 SELECT destino_id FROM firma_autentica_vinculo WHERE origem=tabela AND origem_id=identificador
$$;
''']
triggers=[]
for source,target,fields,refs in maps:
 columns={c['nome']:c for c in tabelas[target]['colunas']};expr={}
 for col,old in fields.items():
  typ=columns[col]['tipo']
  if isinstance(old,bool): value=str(old).lower()
  else:
   value=f"r.dados->>'{old}'"
   if typ=='JSONB':value=f"r.dados->'{old}'"
   elif typ.startswith('VARCHAR'):value=f"left({value},{typ.split('(')[1].split(')')[0]})"
   elif typ in ['TEXT']:pass
   else:value=f"NULLIF({value},'')::{typ}"
  expr[col]=value
 for col,(table,old) in refs.items():expr[col]=f"firma_autentica_id('{table}',r.dados->>'{old}')"
 if target=='Plano':expr['PlanoValor']="(NULLIF(r.dados->>'valor_centavos','')::numeric/100)::numeric(12,2)"
 if target=='MensagemTemplate':expr['MensagemTemplateCategoria']="CASE r.dados->>'categoria' WHEN 'UTILIDADE' THEN 'UTILITY' WHEN 'AUTENTICACAO' THEN 'AUTHENTICATION' ELSE r.dados->>'categoria' END"
 if target=='Canal':expr['CanalProvedorAPI']="CASE WHEN r.dados->>'provedor'='META' THEN 'Meta' ELSE 'Baileys' END"
 if target=='Atendimento':expr['AtendimentoStatus']="CASE r.dados->>'estado' WHEN 'EM_ANDAMENTO' THEN 'emandamento' WHEN 'FINALIZADO' THEN 'finalizado' ELSE 'pendente' END"
 if target=='Mensagem':
  expr['MensagemDataHoraWhatsApp']="COALESCE(NULLIF(r.dados->>'enviada_em','')::timestamptz,NULLIF(r.dados->>'criado_em','')::timestamptz)"
  expr['MensagemFromMe']="(r.dados->>'sentido'='SAIDA')";expr['MensagemEnviadaPeloAtendente']="(r.dados->>'origem'='ATENDENTE')";expr['MensagemEnviadaPeloBot']="(r.dados->>'origem'='BOT')"
  expr['MensagemRAW']="(r.dados->'dados_externos')::text"
 if target=='Webhook':expr['WebhookEvento']="(r.dados->'eventos')::text"
 expr['createdAt']="COALESCE(NULLIF(r.dados->>'criado_em','')::timestamptz,NULLIF(r.dados->>'criada_em','')::timestamptz,now())";expr['updatedAt']="COALESCE(NULLIF(r.dados->>'atualizado_em','')::timestamptz,now())"
 originid="COALESCE(r.dados->>'id',(r.dados->>'atendente_id')||':'||(r.dados->>'setor_id'),(r.dados->>'atendimento_id')||':'||(r.dados->>'etiqueta_id'))"
 names=','.join('"'+k+'"' for k in expr)
 updates=','.join('"'+k+'"='+v for k,v in expr.items() if k!='createdAt')
 triggers.append(f"""IF TG_TABLE_NAME='{source}' THEN
  chave := {originid};
  PERFORM pg_advisory_xact_lock(hashtext(TG_TABLE_NAME||':'||chave));
  SELECT destino_id INTO novo FROM firma_autentica_vinculo WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  IF novo IS NULL THEN
   INSERT INTO autentica.\"{target}\" ({names}) VALUES ({','.join(expr.values())}) RETURNING id INTO novo;
   cliente := {'novo' if target=='Cliente' else "firma_autentica_id('empresa',r.dados->>'empresa_id')"};
   INSERT INTO firma_autentica_vinculo VALUES(TG_TABLE_NAME,chave,'{target}',novo,cliente,r.dados);
  ELSE
   UPDATE autentica.\"{target}\" SET {updates} WHERE id=novo;
   UPDATE firma_autentica_vinculo SET dados_origem=r.dados WHERE origem=TG_TABLE_NAME AND origem_id=chave;
  END IF;
 END IF;""")
 sql.append(f'''DO $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN
 FOR r IN SELECT to_jsonb(t) dados FROM public.{source} t LOOP
  chave := {originid};
  INSERT INTO autentica."{target}" ({names}) VALUES ({','.join(expr.values())}) RETURNING id INTO novo;
  cliente := {'novo' if target=='Cliente' else "firma_autentica_id('empresa',r.dados->>'empresa_id')"};
  INSERT INTO firma_autentica_vinculo VALUES ('{source}',chave,'{target}',novo,cliente,r.dados);
 END LOOP;
END $$;''')
# Restabelece os relacionamentos cíclicos depois da cópia dos registros.
sql.append('''UPDATE autentica."Mensagem" m SET "MensagemRespostaId"=firma_autentica_id('chat_mensagem',v.dados_origem->>'mensagem_referenciada_id') FROM firma_autentica_vinculo v WHERE v.destino='Mensagem' AND v.destino_id=m.id;
UPDATE autentica."Setor" s SET "CanalId"=(SELECT min(c.id) FROM autentica."Canal" c JOIN firma_autentica_vinculo vc ON vc.destino='Canal' AND vc.destino_id=c.id WHERE vc.dados_origem->>'setor_padrao_id'=v.origem_id) FROM firma_autentica_vinculo v WHERE v.destino='Setor' AND v.destino_id=s.id;
DO $$ DECLARE e RECORD; cid INTEGER; rid INTEGER; BEGIN
 FOR e IN SELECT * FROM "Empresas" LOOP
  SELECT id INTO cid FROM autentica."Cliente" WHERE (NULLIF("ClienteEmail",'') IS NOT NULL AND lower("ClienteEmail")=lower(e."EmpresaEmail")) OR (NULLIF("ClienteCpfCnpj",'') IS NOT NULL AND "ClienteCpfCnpj"=e."EmpresaCNPJ") ORDER BY id LIMIT 1;
  IF cid IS NULL AND (SELECT count(*) FROM empresa)=1 AND (SELECT count(*) FROM "Empresas")=1 THEN SELECT min(id) INTO cid FROM autentica."Cliente"; END IF;
  IF cid IS NULL THEN INSERT INTO autentica."Cliente"("ClienteNome","ClienteEmail","ClienteCpfCnpj","ClienteStatus","createdAt","updatedAt") VALUES(COALESCE(e."EmpresaNome",e."EmpresaUsuario"),e."EmpresaEmail",e."EmpresaCNPJ",true,now(),now()) RETURNING id INTO cid; END IF;
  INSERT INTO autentica_identidade."Repositorio"("RepositorioNome","createdAt","updatedAt") VALUES('aFIRMA · '||cid,now(),now()) RETURNING id INTO rid;
  INSERT INTO firma_autentica_empresa VALUES(e."EmpresaId",cid,rid);
 END LOOP;
END $$;
''')
sql.append('UPDATE autentica."Cliente" c SET "PlanoId"=firma_autentica_id(\'chat_plano\',a.plano_id::text) FROM chat_assinatura_plano a JOIN firma_autentica_vinculo v ON v.origem=\'empresa\' AND v.origem_id=a.empresa_id::text WHERE c.id=v.destino_id AND a.estado=\'ATIVA\';\nUPDATE autentica."ListaContato" l SET "ListaContatoContatos"=COALESCE((SELECT jsonb_agg(jsonb_build_object(\'ContatoId\',firma_autentica_id(\'chat_contato\',c.id::text),\'Nome\',c.nome,\'Numero\',c.telefone,\'Email\',c.email)) FROM chat_lista_destinatario d JOIN chat_contato c ON c.id=d.contato_id WHERE d.lista_id=v.origem_id::bigint),\'[]\'::jsonb),"ListaContatoQuantidadeContatos"=(SELECT count(*) FROM chat_lista_destinatario d WHERE d.lista_id=v.origem_id::bigint) FROM firma_autentica_vinculo v WHERE v.destino=\'ListaContato\' AND v.destino_id=l.id;')
(root/'src/main/resources/db/migration/V26__vinculos_e_dados_autentica.sql').write_text('\n\n'.join(sql))
(root/'docs').mkdir(exist_ok=True)
(root/'docs/autentica-mapeamento.json').write_text(json.dumps([{'origem':s,'destino':t,'campos':f,'relacionamentos':r} for s,t,f,r in maps],ensure_ascii=False,indent=2))

trigger_sql="CREATE FUNCTION firma_autentica_sincronizar() RETURNS TRIGGER LANGUAGE plpgsql AS $$ DECLARE r RECORD; novo INTEGER; cliente INTEGER; chave TEXT; BEGIN IF current_setting('firma.autentica_direcao',true)='canonico' THEN RETURN NEW; END IF; SELECT to_jsonb(NEW) AS dados INTO r;\n"+'\n'.join(triggers)+"\nRETURN NEW; END $$;\n"
for source,_,_,_ in maps: trigger_sql+=f"CREATE TRIGGER firma_autentica_sync AFTER INSERT OR UPDATE ON public.{source} FOR EACH ROW EXECUTE FUNCTION firma_autentica_sincronizar();\n"
(root/'src/main/resources/db/migration/V27__sincronizacao_autentica.sql').write_text(trigger_sql)
