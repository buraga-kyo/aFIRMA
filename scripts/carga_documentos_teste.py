"""Gera SQL reproduzível para 500 documentos de teste do AssineAi.

Uso: python scripts/carga_documentos_teste.py > /tmp/carga-assineai.sql
     docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /tmp/carga-assineai.sql
"""

import sys


def pdf_teste() -> bytes:
    conteudo = b"BT /F1 18 Tf 50 720 Td (CARGA DE TESTE - aFirma AssineAi) Tj ET\n"
    objetos = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(conteudo)).encode() + b" >>\nstream\n" + conteudo + b"endstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    dados = bytearray(b"%PDF-1.4\n")
    posicoes = [0]
    for numero, objeto in enumerate(objetos, 1):
        posicoes.append(len(dados))
        dados.extend(f"{numero} 0 obj\n".encode() + objeto + b"\nendobj\n")
    inicio_xref = len(dados)
    dados.extend(f"xref\n0 {len(posicoes)}\n0000000000 65535 f \n".encode())
    for posicao in posicoes[1:]:
        dados.extend(f"{posicao:010d} 00000 n \n".encode())
    dados.extend(
        f"trailer\n<< /Size {len(posicoes)} /Root 1 0 R >>\nstartxref\n{inicio_xref}\n%%EOF\n".encode()
    )
    return bytes(dados)


def main() -> None:
    hex_pdf = pdf_teste().hex()
    sys.stdout.write(f"""\\set ON_ERROR_STOP on
BEGIN;
DELETE FROM campo_assinatura WHERE signatario_id IN (
    SELECT s.id FROM signatario_envelope s JOIN envelope_assinatura e ON e.id = s.envelope_id
    WHERE e.nome_documento LIKE 'CARGA-TESTE-ASSINEAI-%' AND e.criador = 'teste-local'
);
DELETE FROM signatario_envelope WHERE envelope_id IN (
    SELECT id FROM envelope_assinatura
    WHERE nome_documento LIKE 'CARGA-TESTE-ASSINEAI-%' AND criador = 'teste-local'
);
DELETE FROM envelope_assinatura
WHERE nome_documento LIKE 'CARGA-TESTE-ASSINEAI-%' AND criador = 'teste-local';

CREATE TEMP TABLE carga_ids ON COMMIT DROP AS
SELECT numero, gen_random_uuid() AS id FROM generate_series(1, 500) AS numero;

INSERT INTO envelope_assinatura
    (id, nome_empresa, nome_documento, criador, pdf_original, criado_em, expira_em)
SELECT id,
       'Empresa Teste ' || lpad((((numero - 1) % 20) + 1)::text, 2, '0'),
       'CARGA-TESTE-ASSINEAI-' || lpad(numero::text, 4, '0') || '-' ||
           (ARRAY['Contrato', 'Proposta', 'Termo', 'Autorizacao', 'Aditivo'])[((numero - 1) % 5) + 1] || '.pdf',
       'teste-local',
       decode('{hex_pdf}', 'hex'),
       now() - numero * interval '6 hours',
       now() + interval '30 days'
FROM carga_ids;

INSERT INTO signatario_envelope (id, envelope_id, nome, email, resumo_convite)
SELECT gen_random_uuid(), c.id,
       'Signatario Teste ' || s.indice,
       'signatario-' || c.numero || '-' || s.indice || '@example.invalid',
       encode(sha256(convert_to(c.id::text || ':' || s.indice::text, 'UTF8')), 'hex')
FROM carga_ids c
CROSS JOIN LATERAL generate_series(1, 1 + (c.numero % 3)) AS s(indice);

DO $$ BEGIN
    IF (SELECT count(*) FROM envelope_assinatura
        WHERE nome_documento LIKE 'CARGA-TESTE-ASSINEAI-%' AND criador = 'teste-local') <> 500 THEN
        RAISE EXCEPTION 'Carga incompleta';
    END IF;
END $$;
COMMIT;
SELECT count(*) AS documentos_teste FROM envelope_assinatura
WHERE nome_documento LIKE 'CARGA-TESTE-ASSINEAI-%' AND criador = 'teste-local';
""")


if __name__ == "__main__":
    main()
