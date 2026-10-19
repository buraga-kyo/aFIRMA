#!/bin/sh
set -eu

cd "$(dirname "$0")"
if [ ! -f .env ]; then
  printf '%s\n' 'Arquivo .env não encontrado.' >&2
  exit 1
fi
set -a
. ./.env
set +a
exec npm --prefix chat-baileys start
