# Déploiement

## 1. Prérequis

- Serveur Linux, Docker 24+ et Docker Compose v2. 4 Go de RAM recommandés (les conteneurs sont
  limités : backend 768 Mo, rag 768 Mo, MySQL 1 Go, pgvector 512 Mo, frontend 128 Mo).
- Un **reverse proxy qui termine le TLS** devant le conteneur `frontend` (nginx, Caddy, Traefik, un
  load balancer du client…). Le cookie de session est `Secure` : sans HTTPS, la session ne tient pas.
- Un compte OpenAI avec une clé **dédiée à ce projet** et une **limite de dépense mensuelle** fixée
  dans le tableau de bord OpenAI.
- Un compte SMTP (mot de passe d'application Gmail ou relais du client).

## 2. Première installation

```bash
git clone <dépôt> /opt/smartcdc && cd /opt/smartcdc
cp .env.example .env && chmod 600 .env
./scripts/generate-jwt-keys.sh          # affiche JWT_PRIVATE_KEY= et JWT_PUBLIC_KEY= : les coller dans .env
openssl rand -base64 32                 # CHATBOT_API_KEY
openssl rand -base64 24                 # MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, POSTGRES_PASSWORD (un par variable)
$EDITOR .env
docker compose up -d --build
docker compose ps                       # 5 services « healthy »
```

`docker compose` refuse de démarrer si une variable obligatoire manque (le message nomme la variable).
Au premier démarrage, Flyway crée le schéma MySQL (V1 à V7), le département « Siège » et le compte `ADMIN` `ADMIN_EMAIL` sont créés.
**Changer ce mot de passe dès la première connexion**, puis vider `ADMIN_PASSWORD` du `.env`.

### Variables

| Variable | Rôle |
|---|---|
| `PUBLIC_URL` | URL publique de l'application (origine CORS), ex. `https://recouvrement.client.ma` |
| `HTTP_PORT` | Port HTTP publié sur l'hôte (le reverse proxy pointe dessus) |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | Base métier |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base vectorielle |
| `JWT_PRIVATE_KEY` | Clé RSA privée de signature (backend uniquement) |
| `JWT_PUBLIC_KEY` | Clé publique de vérification (donnée au chatbot) |
| `CHATBOT_API_KEY` | Clé de service backend ↔ chatbot (32 caractères minimum) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP (relances par e-mail) |
| `OPENAI_API_KEY` | Clé OpenAI dédiée (chatbot ; lit aussi les factures PDF si `ANTHROPIC_API_KEY` est vide) |
| `ANTHROPIC_API_KEY` | Optionnelle. Import automatique des factures PDF par Claude (prioritaire) ; sans aucune clé d'IA, la fonctionnalité est désactivée. Clé dédiée avec limite de dépense |
| `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` | Optionnelles. Import des factures via OpenRouter si `ANTHROPIC_API_KEY` est vide (modèle par défaut `openai/gpt-4o`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | Premier compte (ignoré une fois un utilisateur créé) |
| `COOKIE_SECURE` | `true` (défaut, exige HTTPS). `false` uniquement pour un essai HTTP sans TLS |

## 3. Reverse proxy TLS (exemple Caddy)

```caddyfile
recouvrement.client.ma {
    reverse_proxy localhost:80
}
```

Caddy obtient et renouvelle le certificat seul. Avec nginx, transmettre
`X-Forwarded-For` et `X-Forwarded-Proto` (le backend s'en sert pour l'adresse IP du client dans la
limitation des tentatives de connexion).

## 4. Mise à jour

```bash
git pull
docker compose build
docker compose up -d                    # recrée seulement les services modifiés
docker compose ps
```

- Les migrations Flyway s'appliquent au démarrage du backend. **Sauvegarder avant** (§ 6).
- Pour ne pas construire sur le serveur : les images sont publiées sur GHCR à chaque tag `vX.Y.Z`
  (`.github/workflows/release.yml`) ; remplacer `build:` par `image:` dans `docker-compose.yml`.

### Retour arrière

Un retour arrière de version applicative est simple ; celui du **schéma** ne l'est pas (Flyway ne défait
pas). Procédure : arrêter, restaurer la sauvegarde MySQL d'avant la mise à jour
(`scripts/restore.sh`), redéployer l'ancienne version.

## 5. Rotation des secrets

| Secret | Procédure | Conséquence |
|---|---|---|
| Clé JWT | `generate-jwt-keys.sh`, mettre à jour les DEUX variables, `docker compose up -d backend rag` | Tous les utilisateurs doivent se reconnecter |
| `CHATBOT_API_KEY` | Nouvelle valeur dans `.env`, `docker compose up -d backend rag` | Aucune |
| Mots de passe des bases | Changer dans la base **puis** dans `.env`, redémarrer les services | Brève interruption |
| Clé OpenAI | Créer la nouvelle clé, la mettre dans `.env`, `docker compose up -d rag backend`, révoquer l'ancienne | Aucune |
| Clé Anthropic | Créer la nouvelle clé, la mettre dans `.env`, `docker compose up -d backend`, révoquer l'ancienne | Aucune |
| Mot de passe SMTP | Mettre à jour `.env`, `docker compose up -d backend` | Aucune |

En cas de fuite d'un secret : le changer immédiatement, puis lire `docs/SECURITY.md` § Incident.

## 6. Sauvegardes

```bash
./scripts/backup.sh /var/backups/smartcdc      # MySQL (mysqldump) + PostgreSQL (pg_dump), rétention 14 jours
```

À planifier chaque nuit (cron), à copier hors du serveur et à chiffrer. **Tester la restauration**
(`./scripts/restore.sh --mysql <fichier>`) avant d'en avoir besoin. Détails dans `docs/OPERATIONS.md`.

## 7. PostgreSQL géré (sans superutilisateur)

Les extensions `vector`, `hstore` et `uuid-ossp` exigent un rôle privilégié à leur première création.
Sur une base gérée où le rôle applicatif ne l'est pas, les activer **une fois** en administrateur :

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

La migration `V1__vector_store.sql` du chatbot utilise `IF NOT EXISTS` et n'exige alors plus aucun privilège.

## 8. Vérifier une installation

```bash
docker compose ps                                          # 5 × healthy
curl -sI https://recouvrement.client.ma/debts/1/details    # 200 (repli SPA)
docker compose exec backend wget -qO- localhost:9090/actuator/health/readiness   # {"status":"UP"}
```
