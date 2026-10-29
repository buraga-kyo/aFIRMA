BEGIN;
DO $$
DECLARE
 a UUID := gen_random_uuid(); b UUID := gen_random_uuid();
 p UUID := gen_random_uuid(); d UUID := gen_random_uuid();
BEGIN
 INSERT INTO envelope_assinatura(id,nome_empresa,nome_documento,criador,pdf_original,criado_em,expira_em)
 VALUES (a,'Teste FK','a.pdf','verificacao-local',''::bytea,now(),now()+interval '1 day'),
 (b,'Teste FK','b.pdf','verificacao-local',''::bytea,now(),now()+interval '1 day');
 INSERT INTO signatario_envelope(id,envelope_id,nome,email,resumo_convite) VALUES(p,a,'Pessoa de teste','','f'||repeat('0',63));
 INSERT INTO documento_assinatura(id,envelope_id,titulo,pagina_inicio,pagina_fim) VALUES(d,b,'Documento de outro envelope',1,1);
 BEGIN
  INSERT INTO campo_assinatura(id,signatario_id,envelope_id,documento_id,tipo,pagina,posicao_x,posicao_y,largura,altura)
   VALUES(gen_random_uuid(),p,a,d,'ASSINATURA',1,0,0,.3,.1);
  RAISE EXCEPTION 'Falha: permitiu campo de outro envelope';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO documento_assinatura(id,envelope_id,titulo) VALUES(gen_random_uuid(),gen_random_uuid(),'Documento órfão');
  RAISE EXCEPTION 'Falha: permitiu documento órfão';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO signatario_historico(id,signatario_id,status,acao,criado_em) VALUES(gen_random_uuid(),gen_random_uuid(),'teste','teste',now());
  RAISE EXCEPTION 'Falha: permitiu histórico órfão';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO desafio_assinatura(id,signatario_id,canal,resumo,expira_em,enviado_em)
   VALUES(p::text||':whatsapp',p,'email',repeat('0',64),now(),now());
  RAISE EXCEPTION 'Falha: permitiu desafio com identidade divergente';
 EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
ROLLBACK;
SELECT 'PASS: FKs e isolamento entre envelopes; histórico e desafio consistentes. Verificação revertida.';
