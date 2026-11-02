CREATE FUNCTION firma_autentica_vinculo_atendente() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE atendente BIGINT; setor BIGINT; empresa BIGINT; direcao TEXT;
BEGIN
 direcao=current_setting('firma.autentica_direcao',true);
 PERFORM set_config('firma.autentica_direcao','canonico',true);
 IF TG_OP IN ('DELETE','UPDATE') THEN
  SELECT origem_id::bigint INTO atendente FROM firma_autentica_vinculo WHERE destino='Atendente' AND destino_id=OLD."AtendenteId";
  SELECT origem_id::bigint INTO setor FROM firma_autentica_vinculo WHERE destino='Setor' AND destino_id=OLD."SetorId";
  DELETE FROM chat_atendente_setor WHERE atendente_id=atendente AND setor_id=setor;
 END IF;
 IF TG_OP IN ('INSERT','UPDATE') THEN
  SELECT origem_id::bigint INTO atendente FROM firma_autentica_vinculo WHERE destino='Atendente' AND destino_id=NEW."AtendenteId";
  SELECT origem_id::bigint INTO setor FROM firma_autentica_vinculo WHERE destino='Setor' AND destino_id=NEW."SetorId";
  SELECT empresa_id INTO empresa FROM chat_atendente WHERE id=atendente;
  IF atendente IS NOT NULL AND setor IS NOT NULL THEN INSERT INTO chat_atendente_setor(atendente_id,setor_id,empresa_id) VALUES(atendente,setor,empresa) ON CONFLICT DO NOTHING; END IF;
 END IF;
 PERFORM set_config('firma.autentica_direcao',COALESCE(direcao,''),true);
 RETURN COALESCE(NEW,OLD);
END $$;
CREATE TRIGGER firma_autentica_vinculo_atendente AFTER INSERT OR UPDATE OR DELETE ON autentica."AtendenteSetor" FOR EACH ROW EXECUTE FUNCTION firma_autentica_vinculo_atendente();

CREATE FUNCTION firma_autentica_bot_encerrado() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.estado<>'ATIVA' THEN
  DELETE FROM autentica."ChatbotMetadata" WHERE id=(SELECT destino_id FROM firma_autentica_vinculo WHERE origem='chat_bot_sessao' AND origem_id=NEW.id::text);
  DELETE FROM firma_autentica_vinculo WHERE origem='chat_bot_sessao' AND origem_id=NEW.id::text;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER zz_firma_autentica_bot_encerrado AFTER INSERT OR UPDATE ON chat_bot_sessao FOR EACH ROW EXECUTE FUNCTION firma_autentica_bot_encerrado();
