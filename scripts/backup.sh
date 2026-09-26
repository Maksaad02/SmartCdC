#!/usr/bin/env bash
# Sauvegarde des deux bases de SmartCDC (MySQL metier + PostgreSQL/pgvector).
#
#   ./scripts/backup.sh [dossier-de-sortie]        # defaut : ./backups
#
# A planifier (cron) sur le serveur, ex. chaque nuit :
#   0 2 * * *  cd /opt/smartcdc && ./scripts/backup.sh /var/backups/smartcdc >> /var/log/smartcdc-backup.log 2>&1
#
# Les sauvegardes contiennent des donnees de recouvrement : les stocker chiffrees, hors du serveur,
# et tester regulierement la restauration (scripts/restore.sh).
set -euo pipefail

OUT="${1:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
COMPOSE=(docker compose -f docker-compose.yml)

mkdir -p "$OUT"
umask 077

echo "[$(date '+%Y-%m-%dT%H:%M:%S%z')] Sauvegarde MySQL..."
# --single-transaction : instantane coherent sans bloquer l'application (InnoDB).
"${COMPOSE[@]}" exec -T mysql sh -c \
  'mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF -uroot -p"$MYSQL_ROOT_PASSWORD" rec_ouv_db' \
  | gzip > "$OUT/mysql-$STAMP.sql.gz"

echo "[$(date '+%Y-%m-%dT%H:%M:%S%z')] Sauvegarde PostgreSQL (pgvector)..."
"${COMPOSE[@]}" exec -T pgvector sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "$OUT/pgvector-$STAMP.dump"

# Une sauvegarde vide est pire que pas de sauvegarde : on refuse de la garder.
for f in "$OUT/mysql-$STAMP.sql.gz" "$OUT/pgvector-$STAMP.dump"; do
  if [ ! -s "$f" ]; then
    echo "ECHEC : $f est vide" >&2
    rm -f "$f"
    exit 1
  fi
done

find "$OUT" -maxdepth 1 \( -name 'mysql-*.sql.gz' -o -name 'pgvector-*.dump' \) -mtime "+$KEEP_DAYS" -delete

echo "[$(date '+%Y-%m-%dT%H:%M:%S%z')] OK : $OUT/mysql-$STAMP.sql.gz et $OUT/pgvector-$STAMP.dump"
