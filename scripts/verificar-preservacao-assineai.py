#!/usr/bin/env python3
"""Confere a migration de compatibilidade em um schema temporário, com rollback."""
from pathlib import Path
import subprocess

raiz = Path(__file__).resolve().parents[1]
migration = (raiz / "src/main/resources/db/migration/V23__assineai_contrato_producao.sql").read_text()
estrutura_original = "\n".join(linha for linha in migration.splitlines() if linha.startswith("CREATE TABLE IF NOT EXISTS"))
fixture = r'''
INSERT INTO "Empresas"("EmpresaId","EmpresaUsuario","EmpresaEmail","EmpresaNome","createdAt","updatedAt")
VALUES (41,'empresa-legada','legada@example.com','Empresa Original','2025-01-01Z','2025-01-01Z');
INSERT INTO "Assinaturas"("AssinaturaId","AssinaturaNome","AssinaturaStatus","createdAt","updatedAt")
VALUES (321,'Assinatura original','Finalizada','2025-01-01Z','2025-01-01Z');
INSERT INTO "AssinaturaDocumentos"("DocumentoId","AssinaturaId","DocumentoTitulo","DocumentoBuffer","DocumentoToken","DocumentoColecaoDeDivArrastavel","createdAt","updatedAt")
VALUES (777,321,'Original preservado',decode('25504446','hex'),'token-original-preservado','[{"GUID":"guid-original-preservado","x":42,"y":84}]','2025-01-01Z','2025-01-01Z');
INSERT INTO "AssinaturaSignatarios"("SignatarioId","AssinaturaId","SignatarioNome","SignatarioAssinou","SignatarioTokenLinkAssinatura","SignatarioFormaDeAutenticacao","createdAt","updatedAt")
VALUES (888,321,'Pessoa original',true,'guid-original-preservado',ARRAY['email','whatsapp'],'2025-01-01Z','2025-01-01Z');
INSERT INTO "SignatarioHistoricos"("SignatarioHistoricoId","SignatarioId","SignatarioHistoricoAcao","createdAt","updatedAt")
VALUES (999,888,'assinatura_original','2025-01-01Z','2025-01-01Z');
SELECT setval(pg_get_serial_sequence('"Assinaturas"','AssinaturaId'),321);
'''
verificacao = r'''
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM "Assinaturas" WHERE "AssinaturaId"=321 AND "AssinaturaNome"='Assinatura original' AND "AssinaturaStatus"='Finalizada') THEN RAISE EXCEPTION 'Assinatura alterada'; END IF;
 IF NOT EXISTS(SELECT 1 FROM "AssinaturaDocumentos" WHERE "DocumentoId"=777 AND "AssinaturaId"=321 AND "DocumentoToken"='token-original-preservado' AND "DocumentoBuffer"=decode('25504446','hex') AND "DocumentoColecaoDeDivArrastavel"::jsonb='[{"GUID":"guid-original-preservado","x":42,"y":84}]'::jsonb) THEN RAISE EXCEPTION 'Documento ou campos alterados'; END IF;
 IF NOT EXISTS(SELECT 1 FROM "AssinaturaSignatarios" WHERE "SignatarioId"=888 AND "AssinaturaId"=321 AND "SignatarioAssinou" AND "SignatarioTokenLinkAssinatura"='guid-original-preservado' AND "SignatarioFormaDeAutenticacao"=ARRAY['email','whatsapp']::varchar[]) THEN RAISE EXCEPTION 'Signatario alterado'; END IF;
 IF NOT EXISTS(SELECT 1 FROM "SignatarioHistoricos" WHERE "SignatarioHistoricoId"=999 AND "SignatarioId"=888 AND "SignatarioHistoricoAcao"='assinatura_original' AND "createdAt"='2025-01-01Z'::timestamptz) THEN RAISE EXCEPTION 'Historico alterado'; END IF;
 IF NOT EXISTS(SELECT 1 FROM "Empresas" WHERE "EmpresaId"=41 AND "EmpresaEmail"='legada@example.com') THEN RAISE EXCEPTION 'Empresa alterada'; END IF;
 IF nextval(pg_get_serial_sequence('"Assinaturas"','AssinaturaId'))<>322 THEN RAISE EXCEPTION 'Sequencia reiniciada'; END IF;
END $$;
ROLLBACK;
SELECT 'PASS: tabelas existentes, IDs, vínculos, tokens, JSON, bytes, datas e sequência preservados; verificação revertida.';
'''
sql = "BEGIN; CREATE SCHEMA firma_assineai_verificacao; SET LOCAL search_path TO firma_assineai_verificacao;\n" + estrutura_original + "\n" + fixture + "\n" + migration + "\n" + verificacao
comando = ["docker", "compose", "exec", "-T", "postgres", "sh", "-c", 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
raise SystemExit(subprocess.run(comando, input=sql, text=True, cwd=raiz).returncode)
