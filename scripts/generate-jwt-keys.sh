#!/usr/bin/env bash
# Genere la paire de cles RSA qui signe (backend) et verifie (chatbot) les jetons de connexion.
#
#   ./scripts/generate-jwt-keys.sh            # affiche les deux lignes a coller dans .env
#
# JWT_PRIVATE_KEY : reste sur le BACKEND uniquement ; qui la detient peut fabriquer un jeton valide.
# JWT_PUBLIC_KEY  : donnee au chatbot ; elle ne permet que de verifier.
# Renouveler la cle invalide tous les jetons en cours (les utilisateurs se reconnectent).
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl requis" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
umask 077

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$TMP/private.pem" 2>/dev/null
openssl rsa -in "$TMP/private.pem" -pubout -out "$TMP/public.pem" 2>/dev/null

# Base64 sur une seule ligne, sans en-tetes : simple a mettre dans une variable d'environnement.
echo "JWT_PRIVATE_KEY=$(grep -v -- '-----' "$TMP/private.pem" | tr -d '\n')"
echo "JWT_PUBLIC_KEY=$(grep -v -- '-----' "$TMP/public.pem" | tr -d '\n')"
