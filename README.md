# SmartCDC — plateforme de gestion du recouvrement de créances

Application web pour suivre les clients débiteurs, leurs créances, les règlements et les relances, avec
un assistant IA (chatbot, actuellement inactif) réservé aux administrateurs.

**Modèle de déploiement : une instance dédiée par entreprise cliente (single-tenant).** L'entreprise est
découpée en **départements** (succursales, filiales internes). Clients, créances, règlements et
relances appartiennent obligatoirement à un département.

| Rôle | Périmètre |
|---|---|
| `ADMIN` — administrateur d'entreprise | Toute l'entreprise : tous les départements, tableau de bord consolidé et comparatif, gestion des départements et des comptes, assistant IA |
| `MANAGER` — gestionnaire de département | Uniquement les clients, créances, règlements et relances **de son département**. Assistant IA masqué |
| `AGENT` | Son portefeuille de dossiers, dans son département |

```
                    ┌──────────────────────── réseau Docker « edge » ────────────────────────┐
 navigateur ──HTTPS──►  reverse proxy du client (TLS)                                          │
                    │        │ HTTP                                                          │
                    │        ▼                                                               │
                    │   frontend (nginx, non-root) ── /api/ ──► backend (Spring Boot, 8080)  │
                    │   SPA React/TS                  /chat/ ─► rag     (Spring AI, 8082)    │
                    └──────────────────────────────────────────┬──────────┬──────────────────┘
                              réseau « data » (sans accès Internet)       │          │
                                   ┌───────────────────────────┘          │          │
                              MySQL 8.4                         PostgreSQL + pgvector      │
                          (données métier)                       (base vectorielle)        │
                                                                                            ▼
                                                                          OpenAI (gpt-4o, embeddings)
```

| Dossier | Contenu |
|---|---|
| `backend/` | API REST Spring Boot 3.5 (Java 21), MySQL + Flyway, cloisonnement par département (filtre Hibernate), JWT RS256 |
| `rag/` | Service chatbot Spring AI : RAG (pgvector) + outils en lecture seule qui interrogent le backend |
| `frontend/` | Application React + TypeScript (Vite), servie par nginx |
| `docs/` | Déploiement, exploitation, sécurité, documents de conception |
| `scripts/` | Sauvegarde/restauration, génération des clés JWT |

## Démarrer

Prérequis : Docker 24+ avec Compose v2.

```bash
cp .env.example .env
./scripts/generate-jwt-keys.sh          # coller les 2 lignes affichées dans .env
openssl rand -base64 32                 # CHATBOT_API_KEY (32 caractères minimum)
# renseigner le reste de .env : mots de passe des bases, SMTP, clé OpenAI, compte admin initial

docker compose up -d --build
docker compose ps                       # les 5 services doivent être « healthy »
```

L'application est servie sur `http://localhost:${HTTP_PORT}` (80 par défaut). Se connecter avec
`ADMIN_EMAIL` / `ADMIN_PASSWORD` : ce compte (rôle `ADMIN`) n'est créé qu'au premier démarrage,
sur une base vide, avec un département « Siège ». Il crée ensuite les autres départements depuis
« Départements », puis les comptes depuis « Utilisateurs » (un gestionnaire ou un agent est rattaché à
un département ; un administrateur n'en a aucun).

**Production : le TLS est obligatoire** (le cookie de session est `Secure`). Voir
[docs/DEPLOY.md](docs/DEPLOY.md).

## Développer

| Composant | Lancer | Tester |
|---|---|---|
| backend | `cd backend && ./mvnw spring-boot:run` (voir `backend/.env.example`) | `./mvnw verify` (Docker requis : MySQL de test) |
| rag | `cd rag && ./mvnw spring-boot:run` (voir `rag/.env.example`) | `./mvnw verify` (Docker requis : pgvector de test) |
| frontend | `cd frontend && npm ci && npm run dev` | `npm run lint && npm run typecheck && npm run test:run` |
| bout en bout | pile Docker démarrée | `cd frontend && npx playwright install chromium && npm run e2e` |

Les tests d'intégration utilisent de vraies bases (Testcontainers), pas des simulations : ils
valident les migrations Flyway sur MySQL et l'indexation pgvector.

## Intégration continue

`.github/workflows/` : `ci.yml` (tests des trois modules, images Docker, parcours de bout en bout),
`security.yml` (gitleaks, Trivy, CodeQL, `npm audit`, hebdomadaire), `release.yml` (images publiées
sur GHCR au tag `vX.Y.Z`). Dependabot surveille Maven, npm, Docker et les actions.

## Documentation

- [docs/DEPLOY.md](docs/DEPLOY.md) — installation, variables, TLS, mise à jour, rotation des clés
- [docs/OPERATIONS.md](docs/OPERATIONS.md) — supervision, métriques, logs, sauvegardes, incidents
- [docs/SECURITY.md](docs/SECURITY.md) — flux de données vers OpenAI, contrôles en place, limites connues
- [docs/design/](docs/design/) — notes de conception (pénalités, schéma pour l'IA…)
