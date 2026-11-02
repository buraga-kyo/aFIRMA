ALTER TABLE chat_arquivo ADD COLUMN IF NOT EXISTS url TEXT;

CREATE FUNCTION firma_autentica_ultima_mensagem() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW."AtendimentoId" IS NOT NULL THEN
  UPDATE autentica."Atendimento" SET "MensagemId"=NEW.id,"updatedAt"=now(),
   "AtendimentoDataUltimaMensagemCliente"=CASE WHEN NOT COALESCE(NEW."MensagemFromMe",false) THEN COALESCE(NEW."MensagemDataHoraWhatsApp",now()) ELSE "AtendimentoDataUltimaMensagemCliente" END,
   "AtendimentoQtdMensagensNaoLidas"=COALESCE("AtendimentoQtdMensagensNaoLidas",0)+CASE WHEN COALESCE(NEW."MensagemFromMe",false) THEN 0 ELSE 1 END
  WHERE id=NEW."AtendimentoId";
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER firma_autentica_ultima_mensagem AFTER INSERT ON autentica."Mensagem" FOR EACH ROW EXECUTE FUNCTION firma_autentica_ultima_mensagem();
-- Também resolve os ciclos de última mensagem dos registros que já existiam antes desta regra.
UPDATE autentica."Atendimento" a SET "MensagemId"=m.id FROM (
 SELECT DISTINCT ON ("AtendimentoId") id,"AtendimentoId" FROM autentica."Mensagem" WHERE "AtendimentoId" IS NOT NULL ORDER BY "AtendimentoId",id DESC
) m WHERE a.id=m."AtendimentoId" AND a."MensagemId" IS NULL;
UPDATE autentica."Atendimento" a SET "AtendimentoDataUltimaMensagemCliente"=m.data FROM (
 SELECT "AtendimentoId",max(COALESCE("MensagemDataHoraWhatsApp","createdAt")) data FROM autentica."Mensagem" WHERE NOT COALESCE("MensagemFromMe",false) GROUP BY "AtendimentoId"
) m WHERE a.id=m."AtendimentoId" AND a."AtendimentoDataUltimaMensagemCliente" IS NULL;
