-- O número fica reservado aos cadastros existentes, inclusive inativos,
-- mas deixa de bloquear um novo canal depois de uma exclusão lógica.
ALTER TABLE chat_canal ADD COLUMN excluido_em TIMESTAMPTZ;
UPDATE chat_canal ca SET excluido_em=x.excluido_em
FROM firma_autentica_vinculo v JOIN firma_autentica_exclusao x
 ON x.tabela='Canal' AND x.registro_id=v.destino_id AND x.cliente_id=v.cliente_id
WHERE v.destino='Canal' AND v.origem='chat_canal' AND ca.id=v.origem_id::uuid;
ALTER TABLE chat_canal DROP CONSTRAINT chat_canal_empresa_telefone_key;
CREATE UNIQUE INDEX chat_canal_empresa_telefone_vigente ON chat_canal(empresa_id,telefone) WHERE excluido_em IS NULL;
CREATE FUNCTION firma_autentica_excluir_canal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.tabela='Canal' THEN
  UPDATE chat_canal SET excluido_em=NEW.excluido_em,ativo=false,conexao='DESCONECTADO'
  WHERE id IN (SELECT origem_id::uuid FROM firma_autentica_vinculo WHERE origem='chat_canal' AND destino='Canal' AND destino_id=NEW.registro_id AND cliente_id=NEW.cliente_id);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER firma_autentica_excluir_canal AFTER INSERT ON firma_autentica_exclusao FOR EACH ROW EXECUTE FUNCTION firma_autentica_excluir_canal();
