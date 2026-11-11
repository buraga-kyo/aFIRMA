#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
nome_container="afirma-assinatura-testes-$$"
nome_restauracao="afirma-assinatura-restauracao-$$"
pasta_temporaria="$(mktemp -d)"
limpar() {
  docker rm -f "$nome_container" "$nome_restauracao" >/dev/null 2>&1 || true
  rm -rf "$pasta_temporaria"
}
trap limpar EXIT
esperar_banco() {
  for tentativa in {1..30}; do
    if docker exec "$1" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then return; fi
    sleep 1
  done
  echo "PostgreSQL de testes não ficou disponível" >&2
  return 1
}
docker run --rm -d --name "$nome_container" \
  -e POSTGRES_PASSWORD=senha-isolada-testes -e POSTGRES_DB=afirma_assinatura_testes \
  -p 127.0.0.1::5432 postgres:16 >/dev/null
esperar_banco "$nome_container"
porta_banco="$(docker port "$nome_container" 5432/tcp | sed 's/.*://')"
AFIRMA_TESTE_BANCO_URL="jdbc:postgresql://127.0.0.1:${porta_banco}/afirma_assinatura_testes" \
  ./mvnw -Djava.awt.headless=true test "$@"
if [[ "${AFIRMA_TESTAR_RESTAURACAO:-true}" == "true" ]]; then
  docker exec "$nome_container" pg_dump -U postgres -d afirma_assinatura_testes -Fc > "$pasta_temporaria/banco.dump"
  docker run --rm -d --name "$nome_restauracao" \
    -e POSTGRES_PASSWORD=senha-isolada-testes -e POSTGRES_DB=afirma_assinatura_restaurada \
    -p 127.0.0.1::5432 postgres:16 >/dev/null
  esperar_banco "$nome_restauracao"
  docker exec -i "$nome_restauracao" pg_restore -U postgres -d afirma_assinatura_restaurada --no-owner --exit-on-error < "$pasta_temporaria/banco.dump"
  porta_restauracao="$(docker port "$nome_restauracao" 5432/tcp | sed 's/.*://')"
  AFIRMA_TESTE_RESTAURACAO=true \
  AFIRMA_TESTE_BANCO_URL="jdbc:postgresql://127.0.0.1:${porta_restauracao}/afirma_assinatura_restaurada" \
    ./mvnw -Djava.awt.headless=true -Dtest=FluxoRestauracaoAssinaturaTest test "$@"
fi
