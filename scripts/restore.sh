#!/usr/bin/env bash
# Restaure une sauvegarde MySQL et/ou PostgreSQL produite par backup.sh.
#
#   ./scripts/restore.sh --mysql backups/mysql-AAAAMMJJ-HHMMSS.sql.gz
#   ./scripts/restore.sh --pgvector backups/pgvector-AAAAMMJJ-HHMMSS.dump
#
# DESTRUCTIF : remplace le contenu actuel de la base. Arreter l'application avant :
#   docker compose stop backend rag frontend
set -euo pipefail

COMPOSE=(docker compose -f docker-compose.yml)
TARGET="${1:-}"
FILE="${2:-}"

if [ -z "$TARGET" ] || [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  echo "Usage : $0 --mysql <fichier.sql.gz> | --pgvector <fichier.dump>" >&2
  exit 2
fi

read -r -p "Ceci REMPLACE la base ${TARGET#--} par $FILE. Taper OUI pour continuer : " answer
[ "$answer" = "OUI" ] || { echo "Annule."; exit 1; }

case "$TARGET" in
  --mysql)
    gunzip -c "$FILE" | "${COMPOSE[@]}" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" rec_ouv_db'
    ;;
  --pgvector)
    "${COMPOSE[@]}" exec -T pgvector sh -c \
      'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$FILE"
    ;;
  *)
    echo "Option inconnue : $TARGET" >&2
    exit 2
    ;;
esac

echo "Restauration terminee. Redemarrer l'application : docker compose up -d"
