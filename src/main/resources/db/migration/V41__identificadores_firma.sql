-- Completa a identidade dos objetos auxiliares, preservando dependências por OID.
DO $firma$
DECLARE r record;
BEGIN
 FOR r IN
  SELECT n.nspname,c.relname,k.conname FROM pg_constraint k
  JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','firma','firma_identidade') AND k.conname LIKE '%autentica%'
 LOOP
  EXECUTE format('ALTER TABLE %I.%I RENAME CONSTRAINT %I TO %I',r.nspname,r.relname,r.conname,
    replace(r.conname,'autentica','relacionamento'));
 END LOOP;
 FOR r IN
  SELECT n.nspname,c.relname,c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','firma','firma_identidade') AND c.relkind IN ('i','S') AND c.relname LIKE '%autentica%'
 LOOP
  EXECUTE format('ALTER %s %I.%I RENAME TO %I',CASE WHEN r.relkind='S' THEN 'SEQUENCE' ELSE 'INDEX' END,
    r.nspname,r.relname,replace(r.relname,'autentica','relacionamento'));
 END LOOP;
 FOR r IN
  SELECT n.nspname,c.relname,t.tgname FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
  JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('public','firma','firma_identidade') AND NOT t.tgisinternal AND t.tgname LIKE '%autentica%'
 LOOP
  EXECUTE format('ALTER TRIGGER %I ON %I.%I RENAME TO %I',r.tgname,r.nspname,r.relname,
    replace(r.tgname,'autentica','relacionamento'));
 END LOOP;
END $firma$;
