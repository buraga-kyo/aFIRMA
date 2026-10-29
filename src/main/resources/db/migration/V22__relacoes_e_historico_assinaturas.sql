ALTER TABLE documento_assinatura ADD CONSTRAINT documento_envelope_fk
    FOREIGN KEY (envelope_id) REFERENCES envelope_assinatura(id);
ALTER TABLE documento_assinatura ADD CONSTRAINT documento_id_envelope_unico UNIQUE (id, envelope_id);
ALTER TABLE documento_assinatura ADD CONSTRAINT documento_intervalo_valido CHECK (pagina_inicio > 0 AND pagina_fim >= pagina_inicio);
ALTER TABLE signatario_envelope ADD CONSTRAINT signatario_id_envelope_unico UNIQUE (id, envelope_id);

ALTER TABLE campo_assinatura ADD COLUMN envelope_id UUID;
ALTER TABLE campo_assinatura ADD COLUMN documento_id UUID;
UPDATE campo_assinatura c SET envelope_id = s.envelope_id FROM signatario_envelope s WHERE s.id = c.signatario_id;
ALTER TABLE campo_assinatura ALTER COLUMN envelope_id SET NOT NULL;
-- Registros anteriores recebem documento somente quando o intervalo identifica uma única origem.
WITH candidatos AS (
    SELECT c.id AS campo_id, (array_agg(d.id))[1] AS documento_id
    FROM campo_assinatura c JOIN documento_assinatura d ON d.envelope_id = c.envelope_id
      AND c.pagina BETWEEN d.pagina_inicio AND d.pagina_fim
    GROUP BY c.id HAVING count(*) = 1
)
UPDATE campo_assinatura c SET documento_id = x.documento_id FROM candidatos x WHERE x.campo_id = c.id;
ALTER TABLE campo_assinatura ADD CONSTRAINT campo_signatario_envelope_fk
    FOREIGN KEY (signatario_id, envelope_id) REFERENCES signatario_envelope(id, envelope_id);
ALTER TABLE campo_assinatura ADD CONSTRAINT campo_documento_envelope_fk
    FOREIGN KEY (documento_id, envelope_id) REFERENCES documento_assinatura(id, envelope_id);
CREATE INDEX campo_assinatura_documento ON campo_assinatura(documento_id);

CREATE TABLE signatario_historico (
    id UUID PRIMARY KEY,
    signatario_id UUID NOT NULL REFERENCES signatario_envelope(id),
    status VARCHAR(120) NOT NULL,
    acao VARCHAR(120) NOT NULL,
    endereco_ip VARCHAR(64),
    dispositivo TEXT,
    criado_em TIMESTAMPTZ NOT NULL
);
CREATE INDEX signatario_historico_pessoa_data ON signatario_historico(signatario_id, criado_em, id);
-- Não fabrica eventos de acesso ou assinatura para os registros antigos.

ALTER TABLE desafio_assinatura ADD COLUMN signatario_id UUID;
ALTER TABLE desafio_assinatura ADD COLUMN canal VARCHAR(20);
UPDATE desafio_assinatura SET signatario_id = split_part(id, ':', 1)::UUID, canal = split_part(id, ':', 2);
ALTER TABLE desafio_assinatura ALTER COLUMN signatario_id SET NOT NULL;
ALTER TABLE desafio_assinatura ALTER COLUMN canal SET NOT NULL;
ALTER TABLE desafio_assinatura ADD CONSTRAINT desafio_signatario_fk FOREIGN KEY (signatario_id) REFERENCES signatario_envelope(id);
ALTER TABLE desafio_assinatura ADD CONSTRAINT desafio_canal_valido CHECK (canal IN ('email', 'whatsapp'));
ALTER TABLE desafio_assinatura ADD CONSTRAINT desafio_pessoa_canal_unico UNIQUE (signatario_id, canal);
ALTER TABLE desafio_assinatura ADD CONSTRAINT desafio_identidade_valida CHECK (id = signatario_id::TEXT || ':' || canal);

UPDATE envelope_assinatura SET nome = COALESCE(NULLIF(nome, ''), nome_documento),
    responsavel = COALESCE(NULLIF(responsavel, ''), criador),
    status = CASE WHEN finalizado_em IS NOT NULL THEN 'Assinado'
        WHEN EXISTS (SELECT 1 FROM signatario_envelope s WHERE s.envelope_id = envelope_assinatura.id AND s.assinado_em IS NOT NULL) THEN 'Em Processo'
        ELSE 'Pendente' END,
    quantidade_documento_assinado = COALESCE(quantidade_documento_assinado, '0');
UPDATE signatario_envelope SET assinou = assinado_em IS NOT NULL,
    data_assinatura = COALESCE(data_assinatura, assinado_em::TEXT),
    situacao_assinatura = CASE WHEN assinado_em IS NOT NULL THEN 'Assinado' ELSE COALESCE(situacao_assinatura, 'Não abriu o link') END,
    quantidade_de_acessos_no_link_de_assinatura = COALESCE(quantidade_de_acessos_no_link_de_assinatura, '0');
