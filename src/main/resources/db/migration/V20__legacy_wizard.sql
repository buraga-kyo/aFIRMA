CREATE TABLE documento_assinatura (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL,
    titulo VARCHAR(255),
    buffer BYTEA,
    assinado_buffer BYTEA,
    colecao_de_div_arrastavel JSONB,
    token TEXT,
    criptografia_chave_publica TEXT,
    criptografia_assinatura TEXT,
    hash_do_pdf_original TEXT,
    link_autenticacao TEXT
);

ALTER TABLE envelope_assinatura
ADD COLUMN nome VARCHAR(255),
ADD COLUMN responsavel VARCHAR(255),
ADD COLUMN status VARCHAR(255),
ADD COLUMN quantidade_documento_assinado VARCHAR(255);

ALTER TABLE signatario_envelope
ADD COLUMN forma_de_autenticacao JSONB,
ADD COLUMN forma_de_notificacao JSONB,
ADD COLUMN assinou BOOLEAN,
ADD COLUMN rg VARCHAR(255),
ADD COLUMN cpf VARCHAR(255),
ADD COLUMN whatsapp VARCHAR(255),
ADD COLUMN token_email VARCHAR(255),
ADD COLUMN token_whatsapp VARCHAR(255),
ADD COLUMN token_link_assinatura TEXT,
ADD COLUMN link_assinatura TEXT,
ADD COLUMN qualificacao VARCHAR(255),
ADD COLUMN situacao_assinatura VARCHAR(255),
ADD COLUMN data_assinatura VARCHAR(255),
ADD COLUMN quantidade_de_acessos_no_link_de_assinatura VARCHAR(255),
ADD COLUMN mensagem_sobre_visualizacao_do_link_de_assinatura VARCHAR(255),
ADD COLUMN dispositivo TEXT,
ADD COLUMN geolocalizacao TEXT,
ADD COLUMN autenticado_via VARCHAR(255),
ADD COLUMN selfie_base64 TEXT,
ADD COLUMN assinatura_escrita_base64 TEXT;
