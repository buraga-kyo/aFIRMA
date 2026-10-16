#!/bin/sh
set -eu

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  printf '%s\n' 'Arquivo .env não encontrado. Copie .env.example para .env e preencha os valores.' >&2
  exit 1
fi

set -a
. ./.env
set +a

exec ./mvnw spring-boot:run "$@"
