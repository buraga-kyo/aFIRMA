ALTER TABLE empresa ADD COLUMN fuso_horario VARCHAR(80) NOT NULL DEFAULT 'America/Sao_Paulo';
ALTER TABLE chat_campanha ADD COLUMN maximo_diario INTEGER CHECK (maximo_diario IS NULL OR maximo_diario > 0);
ALTER TABLE chat_campanha ADD COLUMN horario_inicio TIME NOT NULL DEFAULT '00:00';
ALTER TABLE chat_campanha ADD COLUMN horario_fim TIME NOT NULL DEFAULT '23:59:59';
ALTER TABLE chat_campanha ADD COLUMN dias_semana SMALLINT[] NOT NULL DEFAULT '{0,1,2,3,4,5,6}';
ALTER TABLE chat_campanha ADD CONSTRAINT chat_campanha_horario_check
    CHECK (horario_inicio <= horario_fim);
CREATE INDEX chat_campanha_envio_diario ON chat_campanha_envio(campanha_id,enviado_em)
    WHERE estado='ENVIADO';

CREATE OR REPLACE FUNCTION chat_verificar_limite_canais() RETURNS TRIGGER AS $$
DECLARE limite INTEGER;
DECLARE quantidade BIGINT;
BEGIN
    IF NOT NEW.ativo OR (TG_OP='UPDATE' AND OLD.ativo) THEN RETURN NEW; END IF;
    PERFORM pg_advisory_xact_lock(NEW.empresa_id * 1000 + 901);
    SELECT plano.limite_canais INTO limite FROM chat_assinatura_plano assinatura
    JOIN chat_plano plano ON plano.id=assinatura.plano_id
    WHERE assinatura.empresa_id=NEW.empresa_id AND assinatura.estado='ATIVA';
    IF limite IS NULL THEN RETURN NEW; END IF;
    SELECT count(*) INTO quantidade FROM chat_canal
    WHERE empresa_id=NEW.empresa_id AND ativo;
    IF quantidade >= limite THEN
        RAISE EXCEPTION 'Limite de canais do plano atingido' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_canal_limite BEFORE INSERT OR UPDATE OF ativo ON chat_canal
    FOR EACH ROW EXECUTE FUNCTION chat_verificar_limite_canais();

CREATE OR REPLACE FUNCTION chat_verificar_limite_atendentes() RETURNS TRIGGER AS $$
DECLARE limite INTEGER;
DECLARE quantidade BIGINT;
BEGIN
    IF NOT NEW.ativo OR (TG_OP='UPDATE' AND OLD.ativo) THEN RETURN NEW; END IF;
    PERFORM pg_advisory_xact_lock(NEW.empresa_id * 1000 + 902);
    SELECT plano.limite_atendentes INTO limite FROM chat_assinatura_plano assinatura
    JOIN chat_plano plano ON plano.id=assinatura.plano_id
    WHERE assinatura.empresa_id=NEW.empresa_id AND assinatura.estado='ATIVA';
    IF limite IS NULL THEN RETURN NEW; END IF;
    SELECT count(*) INTO quantidade FROM chat_atendente
    WHERE empresa_id=NEW.empresa_id AND ativo;
    IF quantidade >= limite THEN
        RAISE EXCEPTION 'Limite de atendentes do plano atingido' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER chat_atendente_limite BEFORE INSERT OR UPDATE OF ativo ON chat_atendente
    FOR EACH ROW EXECUTE FUNCTION chat_verificar_limite_atendentes();
