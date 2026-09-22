-- Baseline du schema SmartCDC.
--
-- Reprend l'etat produit jusqu'ici par ddl-auto, montants en DECIMAL(19,2) et
-- suppression logique des creances inclus. A partir d'ici le schema est gere
-- par Flyway et ddl-auto passe en "validate".
--
-- Installation existante creee par ddl-auto : Flyway est configure avec
-- baseline-on-migrate, cette version est donc marquee comme appliquee sans etre
-- rejouee, et la migration suivante s'applique normalement.

CREATE TABLE IF NOT EXISTS `role` (
  `id`  BIGINT NOT NULL AUTO_INCREMENT,
  `nom` ENUM('ADMIN','AGENT') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `utilisateur` (
  `id_agent_recouv` BIGINT       NOT NULL AUTO_INCREMENT,
  `email`           VARCHAR(255) NOT NULL,
  `mot_de_passe`    VARCHAR(255) NOT NULL,
  `nom`             VARCHAR(255) NOT NULL,
  `role_id`         BIGINT       DEFAULT NULL,
  PRIMARY KEY (`id_agent_recouv`),
  UNIQUE KEY `uk_utilisateur_email` (`email`),
  KEY `idx_utilisateur_role` (`role_id`),
  CONSTRAINT `fk_utilisateur_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `client` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `raison_sociale`   VARCHAR(255) NOT NULL,
  `email`            VARCHAR(255) NOT NULL,
  `telephone`        VARCHAR(255) NOT NULL,
  `rc`               VARCHAR(255) NOT NULL,
  `adresse`          VARCHAR(255) NOT NULL,
  `ice`              VARCHAR(255) NOT NULL,
  `identite_fiscale` VARCHAR(255) NOT NULL,
  `id_agent_recouv`  BIGINT       DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_client_agent` (`id_agent_recouv`),
  CONSTRAINT `fk_client_agent` FOREIGN KEY (`id_agent_recouv`) REFERENCES `utilisateur` (`id_agent_recouv`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `creance` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `num_facture`           VARCHAR(255)  NOT NULL,
  `date_emission`         DATE          DEFAULT NULL,
  `echeance`              DATE          DEFAULT NULL,
  `montant_facture`       DECIMAL(19,2) DEFAULT NULL,
  `montant_encaisse`      DECIMAL(19,2) DEFAULT NULL,
  `montant_penalites`     DECIMAL(19,2) DEFAULT NULL,
  `date_calcul_penalites` DATE          DEFAULT NULL,
  `statut`                ENUM('EN_RETARD','IMPAYEE','PARTIELLEMENT_PAYEE','PAYEE','PENALISEE') DEFAULT NULL,
  `supprimee`             BIT(1)        NOT NULL DEFAULT b'0',
  `agent_recouv`          BIGINT        DEFAULT NULL,
  `client_id`             BIGINT        DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creance_num_facture` (`num_facture`),
  KEY `idx_creance_agent` (`agent_recouv`),
  KEY `idx_creance_client` (`client_id`),
  CONSTRAINT `fk_creance_agent`  FOREIGN KEY (`agent_recouv`) REFERENCES `utilisateur` (`id_agent_recouv`),
  CONSTRAINT `fk_creance_client` FOREIGN KEY (`client_id`)    REFERENCES `client` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `reglement` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `montant`         DECIMAL(19,2) DEFAULT NULL,
  `date_reglement`  DATE          DEFAULT NULL,
  `mode_paiement`   ENUM('CARTE_BANCAIRE','CHEQUE','ESPECES','TRAITE','VIREMENT') DEFAULT NULL,
  `statut`          ENUM('EFFECTUE','NON_EFFECTUE') DEFAULT NULL,
  `reference`       VARCHAR(255)  DEFAULT NULL,
  `creance_id`      BIGINT        DEFAULT NULL,
  `id_agent_recouv` BIGINT        DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reglement_creance` (`creance_id`),
  KEY `idx_reglement_agent` (`id_agent_recouv`),
  CONSTRAINT `fk_reglement_creance` FOREIGN KEY (`creance_id`)      REFERENCES `creance` (`id`),
  CONSTRAINT `fk_reglement_agent`   FOREIGN KEY (`id_agent_recouv`) REFERENCES `utilisateur` (`id_agent_recouv`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `relance` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `type_relance`    ENUM('COURRIER','EMAIL','TELEPHONE','VISITE') DEFAULT NULL,
  `statut_relance`  ENUM('ANNULEE','ECHEC','EFFECTUEE','ENVOYEE','EN_ATTENTE','REPORTEE') DEFAULT NULL,
  `date_relance`    DATE         DEFAULT NULL,
  `date_creation`   DATETIME(6)  DEFAULT NULL,
  `date_envoi`      DATETIME(6)  DEFAULT NULL,
  `date_programmee` DATETIME(6)  DEFAULT NULL,
  `message`         VARCHAR(1000) DEFAULT NULL,
  `commentaire`     VARCHAR(255) DEFAULT NULL,
  `agent_envoi`     VARCHAR(255) DEFAULT NULL,
  `creance_id`      BIGINT       NOT NULL,
  `id_agent_recouv` BIGINT       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_relance_creance` (`creance_id`),
  KEY `idx_relance_agent` (`id_agent_recouv`),
  CONSTRAINT `fk_relance_creance` FOREIGN KEY (`creance_id`)      REFERENCES `creance` (`id`),
  CONSTRAINT `fk_relance_agent`   FOREIGN KEY (`id_agent_recouv`) REFERENCES `utilisateur` (`id_agent_recouv`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
