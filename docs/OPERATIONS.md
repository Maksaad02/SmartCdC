# Exploitation

## Supervision

### Santé

Chaque service Java expose ses sondes sur un **port d'administration interne (9090)**, jamais publié
hors du réseau Docker. Le port public n'expose aucun endpoint actuator.

| Sonde | Commande |
|---|---|
| Liveness / readiness du backend | `docker compose exec backend wget -qO- localhost:9090/actuator/health/readiness` |
| Idem pour le chatbot | `docker compose exec rag wget -qO- localhost:9090/actuator/health/readiness` |
| Front | `curl -s http://localhost:${HTTP_PORT}/healthz` |
| Tout d'un coup | `docker compose ps` (le healthcheck de chaque image est déjà branché) |

La readiness du backend et du chatbot inclut la connexion à leur base : un service « unhealthy » a
perdu sa base.

### Métriques (Prometheus)

`GET :9090/actuator/prometheus` sur `backend` et `rag`. Pour les collecter, attacher le serveur
Prometheus au réseau Docker du projet (ou lui donner accès à ces conteneurs) : ne **pas** publier le
port 9090.

Métriques applicatives ajoutées :

| Métrique | Sens | Alerte suggérée |
|---|---|---|
| `smartcdc_login_failures_total` | échecs de connexion | > 50/10 min : attaque par force brute |
| `smartcdc_login_throttled_total` | connexions refusées (limiteur) | croissance soutenue |
| `smartcdc_login_accounts_locked_total` | comptes verrouillés (10 échecs) | > 0 hors incident connu |
| `smartcdc_refresh_reuse_detected_total` | jeton de renouvellement rejoué (vol probable) | **> 0 : enquêter** |
| `smartcdc_emails_total{result="failed"}` | relances automatiques non envoyées | > 0 : SMTP à vérifier |
| `smartcdc_penalties_nightly_failures_total` | échecs du recalcul nocturne des pénalités | > 0 |
| `smartcdc_chat_questions_total` | questions posées au chatbot | suivi du coût |
| `smartcdc_chat_suspicious_total` | tentatives d'injection repérées | pic soudain |
| `smartcdc_chat_throttled_total` | questions refusées (plafond par utilisateur) | croissance soutenue |
| `gen_ai_client_token_usage_*` (Spring AI) | jetons consommés chez OpenAI | **dépassement du budget quotidien** |
| `hikaricp_connections_active` / `_pending` | saturation du pool MySQL | pending > 0 durablement |
| `http_server_requests_seconds` | latence et erreurs par route | taux de 5xx > 1 % |

### Logs

JSON (format ECS) sur la sortie standard, avec `requestId` sur chaque ligne. `docker compose logs -f backend`.
La rotation est faite par Docker (3 fichiers de 10 Mo par service).

Un `requestId` traverse nginx → chatbot → backend : pour suivre une question du chatbot de bout en
bout, filtrer les trois services sur la même valeur (renvoyée aussi à l'utilisateur dans l'en-tête de
réponse `X-Request-Id`). Le logger `chat.audit` trace chaque question (utilisateur, empreinte, taille,
durée) et chaque appel d'outil (empreinte de l'argument) **sans jamais écrire le contenu**.

## Sauvegardes et restauration

| Quoi | Comment | Fréquence |
|---|---|---|
| MySQL (données métier) | `./scripts/backup.sh` (`mysqldump --single-transaction`, cohérent, sans arrêt) | chaque nuit |
| PostgreSQL / pgvector | `./scripts/backup.sh` (`pg_dump`, format custom) | chaque nuit |
| Configuration | `.env` (coffre-fort de secrets, pas dans Git) | à chaque changement |

Restauration : `docker compose stop backend rag frontend`, puis
`./scripts/restore.sh --mysql <fichier>` et/ou `--pgvector <fichier>`, puis `docker compose up -d`.

La base vectorielle ne contient que le texte de loi indexé : elle se **reconstruit seule** au démarrage
du chatbot (réindexation automatique si la table est vide) ; sa sauvegarde n'est qu'un gain de temps
(et évite de repayer les embeddings).

## Tâches planifiées

| Tâche | Quand | Rôle |
|---|---|---|
| Recalcul des pénalités et statuts | 01 h 30 (Africa/Casablanca), `app.penalties.cron` | remet à jour le statut stocké (filtres, statistiques) |
| Purge des jetons de renouvellement expirés | 03 h 15 | hygiène de la table `refresh_token` |
| Nouvelle tentative d'indexation du chatbot | toutes les 10 min, seulement après un échec | reprise si OpenAI était indisponible au démarrage |

Ces tâches supposent **une seule instance** du backend. Répliquer le backend exige un verrou
(ShedLock) et un magasin partagé pour les limiteurs (Redis).

## Limites et dimensionnement

- Listes paginées : 20 par page par défaut, 200 au maximum.
- Réponses destinées au chatbot : 50 impayés par défaut, 200 au maximum.
- Chatbot : 5 questions/minute et 60/heure par utilisateur (`app.rag.chat.per-minute`, `per-hour`).
- Connexion : 5 échecs/minute par compte, 20 par adresse IP (seuls les échecs comptent) ; verrouillage 15 min après 10 échecs.
- Session : jeton d'accès 15 min (renouvelé en silence), session absolue 12 h.

## Incidents courants

| Symptôme | Cause probable | Action |
|---|---|---|
| Le chatbot répond « service d'IA momentanément indisponible » | OpenAI en panne, clé invalide ou quota dépassé | `docker compose logs rag` (401 = clé, 429 = quota) ; vérifier la limite de dépense OpenAI |
| Le chatbot répond « accès refusé » à un agent | Portefeuille sans ce client, ou jeton expiré côté chatbot | Vérifier que le client est bien à cet agent ; se reconnecter |
| Relances : statut `ECHEC` | SMTP refusé (mot de passe d'application, quota Gmail) | `smartcdc_emails_total{result="failed"}`, logs du backend ; corriger `MAIL_*`, puis « Envoyer » depuis la relance |
| Utilisateurs déconnectés toutes les 15 min | Cookie `Secure` sur HTTP, ou proxy qui supprime `Set-Cookie` | Vérifier HTTPS et que le proxy laisse passer `/api/auth` |
| « Trop de tentatives » à la connexion | Limiteur d’échecs (5/min) ou verrouillage | Attendre 15 min ; un ADMIN peut réinitialiser le mot de passe |
| Backend « unhealthy » au démarrage | MySQL pas prêt, migration échouée | `docker compose logs backend` ; une base **non vide sans historique Flyway** est refusée volontairement |
| Le chatbot ne connaît pas un texte de loi modifié | Document source changé | Redéployer : l'empreinte change, le chatbot réindexe seul au démarrage |
| `smartcdc_refresh_reuse_detected` > 0 | Jeton de renouvellement copié puis rejoué | La session concernée est déjà révoquée ; contacter l'utilisateur, lire `docs/SECURITY.md` § Incident |

## Mise à jour des dépendances

Dependabot ouvre chaque semaine des pull requests (Maven, npm, Docker, Actions). Le workflow
`security.yml` scanne le dépôt chaque lundi (Trivy, CodeQL, gitleaks, `npm audit`) même sans commit.
