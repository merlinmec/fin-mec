#!/usr/bin/env bash
# Prova que o último backup restaura: cria um banco descartável, restaura o dump mais recente,
# confere a versão das migrations e a contagem de usuários, e apaga o banco. Rodar com:
#   docker compose -f docker-compose.prod.yml --env-file .env.prod exec backup bash /scripts/restore-check.sh
# Backup que nunca foi restaurado não é backup — rode isto depois do deploy e periodicamente.
set -euo pipefail

LATEST="$(ls -1t /backups/fin-mec-*.dump 2>/dev/null | head -1 || true)"
if [ -z "$LATEST" ]; then
  echo "[restore-check] nenhum backup em /backups" >&2
  exit 1
fi

CHECK_DB="restore_check_$(date +%s)"
cleanup() { dropdb --if-exists "$CHECK_DB" || true; }
trap cleanup EXIT

echo "[restore-check] restaurando $LATEST em $CHECK_DB"
createdb "$CHECK_DB"
pg_restore --no-owner --exit-on-error -d "$CHECK_DB" "$LATEST"

VERSION="$(psql -d "$CHECK_DB" -tAc "SELECT max(version::int) FROM flyway_schema_history WHERE success")"
USERS="$(psql -d "$CHECK_DB" -tAc "SELECT count(*) FROM users")"
echo "[restore-check] OK — migrations até V$VERSION, $USERS usuário(s)"
