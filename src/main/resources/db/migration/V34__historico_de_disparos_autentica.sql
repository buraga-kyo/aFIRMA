-- As campanhas anteriores continuam no executor já existente; seu histórico fica visível no contrato Autentica.
CREATE FUNCTION firma_autentica_campanha_publica(campanha UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE destino INTEGER; resumo RECORD;
BEGIN
 SELECT destino_id INTO destino FROM firma_autentica_vinculo WHERE origem='chat_campanha' AND origem_id=campanha::text;
 IF destino IS NULL THEN RETURN; END IF;
 SELECT count(*)::integer total,
  count(*) FILTER(WHERE e.estado IN ('PENDENTE','ENVIANDO'))::integer pendentes,
  count(*) FILTER(WHERE e.estado='ENVIADO')::integer enviados,
  count(*) FILTER(WHERE e.estado='FALHOU')::integer falhas,
  count(*) FILTER(WHERE e.estado='CANCELADO')::integer cancelados,
  COALESCE(jsonb_agg(jsonb_build_object('Destinatario',c.telefone,'Mensagem',COALESCE(cp.texto,m.corpo,''),'Status',CASE e.estado WHEN 'ENVIADO' THEN 'Enviada' WHEN 'FALHOU' THEN 'Falha' WHEN 'CANCELADO' THEN 'Cancelada' ELSE 'Pendente' END,'EstadoEnvio',e.estado,'Erro',e.erro,'IdentificadorExterno',e.identificador_externo,'Variaveis',jsonb_build_object('nome',c.nome,'telefone',c.telefone)) ORDER BY e.id),'[]'::jsonb) mensagens
 INTO resumo FROM chat_campanha_envio e JOIN chat_contato c ON c.id=e.contato_id JOIN chat_campanha cp ON cp.id=e.campanha_id LEFT JOIN chat_modelo_mensagem m ON m.id=cp.modelo_id WHERE e.campanha_id=campanha;
 UPDATE autentica."DisparoMassa" SET "DisparoMassaQuantidadeMensagens"=resumo.total,"DisparoMassaQuantidadePendente"=resumo.pendentes,"DisparoMassaQuantidadeEnviado"=resumo.enviados,"DisparoMassaQuantidadeFalha"=resumo.falhas,"DisparoMassaQuantidadeCancelado"=resumo.cancelados,"DisparoMassaMensagens"=resumo.mensagens,"DisparoMassaProcessandoMensagens"=false WHERE id=destino;
END $$;
CREATE FUNCTION firma_autentica_campanhas_alteradas() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE c UUID;
BEGIN
 FOR c IN SELECT DISTINCT campanha_id FROM novos LOOP PERFORM firma_autentica_campanha_publica(c); END LOOP;
 RETURN NULL;
END $$;
CREATE TRIGGER firma_autentica_campanhas_inseridas AFTER INSERT ON chat_campanha_envio REFERENCING NEW TABLE AS novos FOR EACH STATEMENT EXECUTE FUNCTION firma_autentica_campanhas_alteradas();
CREATE TRIGGER firma_autentica_campanhas_atualizadas AFTER UPDATE ON chat_campanha_envio REFERENCING NEW TABLE AS novos FOR EACH STATEMENT EXECUTE FUNCTION firma_autentica_campanhas_alteradas();
CREATE FUNCTION firma_autentica_campanha_alterada() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN PERFORM firma_autentica_campanha_publica(NEW.id); RETURN NEW; END $$;
CREATE TRIGGER zz_firma_autentica_campanha AFTER UPDATE ON chat_campanha FOR EACH ROW EXECUTE FUNCTION firma_autentica_campanha_alterada();
DO $$ DECLARE c UUID; BEGIN FOR c IN SELECT origem_id::uuid FROM firma_autentica_vinculo WHERE origem='chat_campanha' LOOP PERFORM firma_autentica_campanha_publica(c); END LOOP; END $$;
