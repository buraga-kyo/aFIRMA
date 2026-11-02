-- Lotes idempotentes e associação estável entre conversa importada e atendimento.
CREATE TABLE firma_autentica_historico_lote (
 canal_id INTEGER NOT NULL REFERENCES autentica."Canal"(id),
 lote UUID NOT NULL, mensagens INTEGER NOT NULL DEFAULT 0,
 duplicadas INTEGER NOT NULL DEFAULT 0, ignoradas INTEGER NOT NULL DEFAULT 0,
 recebida_em TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(canal_id,lote)
);
CREATE TABLE firma_autentica_historico_conversa (
 canal_id INTEGER NOT NULL REFERENCES autentica."Canal"(id),
 contato_id INTEGER NOT NULL REFERENCES autentica."Contato"(id),
 atendimento_id INTEGER NOT NULL REFERENCES autentica."Atendimento"(id),
 PRIMARY KEY(canal_id,contato_id)
);
-- Histórico não é uma nova entrada: não dispara webhook, bot, IA ou notificações.
DO $$
DECLARE r RECORD; ddl TEXT;
BEGIN
 FOR r IN SELECT n.nspname,t.relname,g.tgname,pg_get_triggerdef(g.oid) AS definicao
  FROM pg_trigger g JOIN pg_class t ON t.oid=g.tgrelid JOIN pg_namespace n ON n.oid=t.relnamespace
  WHERE NOT g.tgisinternal AND g.tgname IN (
   'firma_autentica_evento_mensagem','firma_autentica_ia_agendar','firma_autentica_ultima_mensagem',
   'chat_mensagem_evento','chat_automacao_mensagem','chat_atendimento_evento')
 LOOP
  ddl=replace(r.definicao,' EXECUTE FUNCTION',' WHEN (COALESCE(current_setting(''firma.importando_historico'',true),'''') <> ''true'') EXECUTE FUNCTION');
  EXECUTE format('DROP TRIGGER %I ON %I.%I',r.tgname,r.nspname,r.relname);
  EXECUTE ddl;
 END LOOP;
END $$;
CREATE INDEX firma_autentica_mensagem_cronologia ON autentica."Mensagem" ("AtendimentoId",COALESCE("MensagemDataHoraWhatsApp","createdAt") DESC,id DESC);
