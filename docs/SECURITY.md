# Sécurité

## Données envoyées à OpenAI

Le chatbot utilise un fournisseur d'IA tiers (OpenAI, `gpt-4o` et `text-embedding-3-small`, hébergé
hors du Maroc). **À valider avec le client** au regard de la loi 09-08 (CNDP) et, le cas échéant, du
RGPD, et à couvrir par le contrat de sous-traitance / DPA d'OpenAI.

| Ce qui part chez OpenAI | Détail |
|---|---|
| La question de l'agent | Texte saisi (2000 caractères max) |
| Des extraits du texte de loi | Passages du document de référence retrouvés par similarité (4 max) |
| Le résultat des outils, **pour le portefeuille de l'agent uniquement** | Raison sociale, identifiant, ICE et téléphone d'un client ; pour ses factures : numéro, montants, solde, statut, jours de retard, pénalités |

Ne partent **pas** : l'e-mail des clients, les adresses, les mots de passe, les jetons, les données
d'autres agents ni d'autres départements, ni aucune écriture (les outils sont en lecture seule).

Minimisation : réponses bornées (50 factures détaillées maximum), champs nettoyés et tronqués. Pour
supprimer tout transfert à un tiers, il faut remplacer le fournisseur par un modèle hébergé chez le
client (Ollama ou équivalent) : c'est un changement de configuration Spring AI, mais il impose un
serveur adapté et une réindexation (dimension d'embedding différente).

## Contrôles en place

| Menace | Contrôle |
|---|---|
| Vol de mot de passe par force brute | Limiteur (5/min par compte, 20/min par IP) + verrouillage 15 min après 10 échecs ; message d'erreur identique pour compte inconnu et mot de passe faux |
| Mots de passe lisibles en base | BCrypt ; migration V3 a haché les comptes hérités |
| Vol de jeton par XSS | Jeton d'accès **en mémoire seulement** (15 min) ; renouvellement par cookie `HttpOnly; Secure; SameSite=Strict` limité à `/api/auth` ; CSP `script-src 'self'` sans script tiers |
| Rejeu d'un jeton de renouvellement | Rotation à chaque usage ; rejeu hors fenêtre de 10 s = **révocation de toute la session** + alerte métrique ; seule l'empreinte SHA-256 est stockée |
| Chatbot compromis → faux jeton administrateur | Signature **RS256** : le chatbot ne détient que la clé publique ; il ne peut que vérifier |
| Jeton confondu entre services / algorithmes | `iss` et `aud` vérifiés (API ≠ chatbot) ; algorithme asymétrique imposé (refus de HS256 avec la clé publique, de `alg: none`) ; testés |
| Accès aux données d'un autre département (MANAGER, AGENT) | Filtre Hibernate `departement`, activé à l'ouverture de **chaque transaction** pour ces rôles (`DepartementFilterTransactionManager`) : listes, comptages, agrégats, `@Query`, recherche du chatbot. `findById` redéfini en JPQL (le filtre ne s'applique pas à `em.find`), repositories transactionnels (échec fermé hors service). Contrôle applicatif `assertCanAccess` en seconde ligne. Test d'isolation sur base réelle (`DepartementIsolationTest`) |
| Incohérence de département (créance ↔ client, règlement/relance ↔ créance) | Le département est **imposé** (client : celui de l'appelant, choisi par l'ADMIN) ou **hérité** (jamais lu dans la requête) ; clés étrangères composites en base (`(client_id, departement_id)`, `(creance_id, departement_id)`), testées sur MySQL |
| Accès au portefeuille d'un autre agent | Un AGENT reste limité à son portefeuille (Specification SQL), dans son département ; contrôle d'appartenance sur création/modification de règlements et de relances ; tests |
| Tâches système (recalcul nocturne, e-mails asynchrones) | Sans utilisateur authentifié, donc sans filtre : elles traitent toute l'entreprise (testé) |
| Fraude sur les montants | `montantEncaisse` et `statut` calculés côté serveur uniquement ; jamais lus dans la requête |
| Escalade de rôle | Gestion des comptes et des départements réservée aux ADMIN (`@PreAuthorize`) ; hiérarchie `ADMIN > MANAGER > AGENT` ; changement de rôle ou de mot de passe = sessions révoquées |
| Assistant IA visible d'un MANAGER | L'interface est masquée hors ADMIN, mais **masquer n'est pas autoriser** : un MANAGER qui appellerait directement le chatbot ne verrait que les données de son département (le chatbot rejoue son jeton, le backend filtre). Vérifier le rôle côté chatbot reste possible plus tard |
| Injection de prompt | Défense en profondeur (voir ci-dessous) ; **le contrôle d'accès reste côté backend**, pas dans le prompt |
| Fuite via une réponse du modèle | Filtre de sortie (images, liens, HTML, constructions non fermées), y compris en streaming ; le navigateur n'affiche de toute façon ni image ni lien |
| Injection SQL | Aucune requête native ; JPA/Specifications avec paramètres ; recherche avec jokers échappés ; tri en liste blanche |
| Abus / coût du chatbot | Plafond par utilisateur, `max-tokens=1000`, réponses bornées, délais réseau, 2 essais OpenAI |
| E-mails de relance non voulus | Envoyé **après** le commit de la transaction (rollback = aucun e-mail) ; destinataire déduit de la créance côté serveur, jamais fourni par le client |
| CSRF sur le renouvellement de session | `SameSite=Strict` + en-tête `X-Requested-With` exigé |
| Secrets | Variables d'environnement, échec au démarrage si absentes ; `.env` hors Git ; gitleaks en pré-commit et en CI |
| Conteneurs | Utilisateur non-root, réseau `data` sans accès Internet, bases jamais publiées, `X-Content-Type-Options`, `frame-ancestors 'none'` |

### Injection de prompt : ce qui est et n'est pas garanti

Aucun filtre ne garantit qu'un modèle de langage ne sera jamais manipulé. Le système est conçu pour
que **manipuler le modèle ne donne rien de plus que ce que l'agent a déjà le droit de voir** :

1. Le chatbot rejoue le jeton de l'utilisateur : le backend filtre chaque réponse sur son périmètre (département, portefeuille).
2. Les outils sont en lecture seule, leurs arguments sont validés par liste blanche (le modèle est
   traité comme une entrée non fiable).
3. Les données renvoyées par les outils sont nettoyées (une raison sociale piégée ne devient pas une
   consigne) et bornées.
4. La sortie est filtrée (pas d'image ni de lien exfiltrant) et affichée sans contenu actif.
5. Les tentatives évidentes sont journalisées et comptées (`smartcdc_chat_suspicious_total`) : c'est
   de la télémétrie, pas une barrière.

Ce qui reste possible : un agent malveillant peut faire dire au chatbot des choses fausses sur le
droit ou reformater ses propres données. Le prompt exige de citer la section de la source et d'admettre
quand elle ne couvre pas la question, mais la réponse d'un modèle doit toujours être relue.

## Limites connues (à connaître avant d'exploiter)

- **Instance unique.** Les limiteurs (connexion, chatbot) et les tâches planifiées sont en mémoire.
  Répliquer le backend ou le chatbot exige Redis et un verrou de tâches.
- **TLS non fourni.** Le reverse proxy du client doit l'assurer ; sans HTTPS le cookie `Secure` n'est pas
  transmis. `COOKIE_SECURE=false` existe pour un essai en HTTP pur et ne doit pas servir en production.
- **Un jeton d'accès reste valable jusqu'à 15 min après une révocation.** Le rôle et l'existence du
  compte sont relus à chaque requête : un compte supprimé ou rétrogradé perd ses droits immédiatement ;
  seule la prolongation de session est coupée.
- **Suivi d'erreurs** : les erreurs de rendu du navigateur sont journalisées dans la console
  (`reportError`) mais aucun service de suivi (Sentry…) n'est branché ; c'est le point d'extension
  prévu (`frontend/src/lib/reportError.ts`).
- **Statut filtré = statut stocké.** Le filtre par statut et les statistiques lisent le statut en base,
  recalculé à chaque écriture et chaque nuit ; entre deux recalculs, une créance devenue en retard
  dans la journée peut encore apparaître « impayée » dans un filtre (l'affichage de la fiche, lui, est
  toujours à jour).
- **Réponse du modèle non vérifiée** : voir ci-dessus.

## Incident : un secret a fuité

1. **Changer le secret** (procédure `docs/DEPLOY.md` § 5). Pour la clé JWT : tous les jetons en
   circulation deviennent invalides.
2. Mot de passe de base, clé OpenAI, SMTP : les révoquer chez le fournisseur **avant** de les remplacer.
3. Consulter `smartcdc_refresh_reuse_detected_total`, `smartcdc_login_failures_total` et les logs
   `chat.audit` sur la période.
4. Si des données de recouvrement ont pu être exposées, informer le client (obligations CNDP).
5. Ne pas se contenter de retirer le secret du dépôt : il reste dans l'historique Git. Le dépôt de ce projet
   a déjà dû être purgé une fois (voir l'historique) ; utiliser `git filter-repo` puis forcer la mise à jour
   et re-cloner partout.
