#!/usr/bin/env bash
# Backup diário do Postgres (serviço "backup" do docker-compose.prod.yml).
# - pg_dump em formato custom (-Fc): comprimido e restaurável tabela a tabela;
# - cada dump é conferido com pg_restore --list logo após gerado (arquivo truncado = falha já);
# - mantém BACKUP_KEEP_DAYS dias. Para copiar para fora da VPS, ver docs/DEPLOY.md.
set -euo pipefail

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
DIR=/backups

run_backup() {
  local file="$DIR/fin-mec-$(date +%Y%m%d-%H%M%S).dump"
  echo "[backup] gerando $file"
  pg_dump -Fc -f "$file.tmp"
  pg_restore --list "$file.tmp" > /dev/null
  mv "$file.tmp" "$file"
  find "$DIR" -name 'fin-mec-*.dump' -mtime +"$KEEP_DAYS" -print -delete
  echo "[backup] ok ($(du -h "$file" | cut -f1))"
}

mkdir -p "$DIR"
until pg_isready -q; do sleep 2; done
while true; do
  run_backup || echo "[backup] FALHOU — verifique os logs do serviço backup" >&2
  sleep 86400
done
