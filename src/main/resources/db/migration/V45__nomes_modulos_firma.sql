-- Renomeia as estruturas auxiliares sem recriar registros, tokens ou vínculos.
ALTER TABLE firma_assineai_identidade RENAME TO firma_cartorio_identidade;
ALTER TABLE firma_assineai_controle RENAME TO firma_cartorio_controle;
ALTER TABLE firma_assineai_codigo RENAME TO firma_cartorio_codigo;
ALTER TABLE firma_assineai_marca RENAME TO firma_cartorio_marca;
ALTER INDEX firma_assineai_criador RENAME TO firma_cartorio_criador;

-- Inclui os nomes dos índices de chave primária e das chaves estrangeiras.
DO $$
DECLARE restricao RECORD;
BEGIN
    FOR restricao IN
        SELECT c.conname, t.relname
        FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema() AND c.conname LIKE 'firma_assineai_%'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
            restricao.relname, restricao.conname,
            replace(restricao.conname, 'firma_assineai_', 'firma_cartorio_'));
    END LOOP;
END $$;
