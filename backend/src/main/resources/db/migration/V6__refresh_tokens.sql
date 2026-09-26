-- Jetons de renouvellement de session (cookie httpOnly cote navigateur).
--
-- Le jeton d'acces est court (15 min) et ne vit qu'en memoire dans le navigateur ; ce jeton opaque,
-- lui, permet d'en obtenir un nouveau sans ressaisir le mot de passe. Seule son EMPREINTE (SHA-256)
-- est stockee : une fuite de la base ne donne aucun jeton utilisable.
--
-- famille : tous les jetons issus d'une meme connexion. Un jeton deja utilise qu'on represente
-- (vol probable) revoque toute la famille.
CREATE TABLE refresh_token (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  token_hash  VARCHAR(64)  NOT NULL,
  user_id     BIGINT       NOT NULL,
  family_id   VARCHAR(36)  NOT NULL,
  created_at  DATETIME(6)  NOT NULL,
  expires_at  DATETIME(6)  NOT NULL,
  -- Utilise (remplace par un nouveau jeton lors d'un renouvellement).
  used_at     DATETIME(6)  NULL,
  -- Revoque (deconnexion, changement de role, rejeu detecte) : plus aucun usage possible.
  revoked_at  DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_token_user (user_id),
  KEY idx_refresh_token_family (family_id),
  KEY idx_refresh_token_expires (expires_at),
  -- La suppression d'un compte supprime ses sessions.
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES utilisateur (id_agent_recouv) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
