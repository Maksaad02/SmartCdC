-- Cloisonnement par organisation (client payant).
--
-- Avant cette migration, un ADMIN voyait findAll() : en vendant a deux cabinets
-- de recouvrement, l'admin du cabinet A voyait les debiteurs, factures et
-- reglements du cabinet B. Le cloisonnement existant est au niveau agent, a
-- l'interieur d'une seule organisation.
--
-- organisation_id est porte par chaque table metier plutot que deduit par
-- jointure : le filtre de cloisonnement s'applique alors sur une seule colonne
-- indexee, sur toutes les requetes.

CREATE TABLE `organisation` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `nom`           VARCHAR(255) NOT NULL,
  `actif`         BIT(1)       NOT NULL DEFAULT b'1',
  `date_creation` DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_organisation_nom` (`nom`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Organisation de reprise : rattache les donnees deja presentes.
INSERT INTO `organisation` (`nom`, `actif`, `date_creation`)
VALUES ('Organisation par defaut', b'1', NOW(6));

SET @org_defaut = (SELECT `id` FROM `organisation` WHERE `nom` = 'Organisation par defaut');

-- --- utilisateur -----------------------------------------------------------
ALTER TABLE `utilisateur` ADD COLUMN `organisation_id` BIGINT NULL AFTER `role_id`;
UPDATE `utilisateur` SET `organisation_id` = @org_defaut WHERE `organisation_id` IS NULL;
ALTER TABLE `utilisateur`
  MODIFY COLUMN `organisation_id` BIGINT NOT NULL,
  ADD KEY `idx_utilisateur_org` (`organisation_id`),
  ADD CONSTRAINT `fk_utilisateur_org` FOREIGN KEY (`organisation_id`) REFERENCES `organisation` (`id`);

-- L'email reste unique globalement : il sert d'identifiant de connexion et le
-- resolveur d'utilisateur (CustomUserDetailsService) travaille avant que
-- l'organisation soit connue.

-- --- client ----------------------------------------------------------------
ALTER TABLE `client` ADD COLUMN `organisation_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `client` SET `organisation_id` = @org_defaut WHERE `organisation_id` IS NULL;
ALTER TABLE `client`
  MODIFY COLUMN `organisation_id` BIGINT NOT NULL,
  ADD KEY `idx_client_org` (`organisation_id`),
  ADD CONSTRAINT `fk_client_org` FOREIGN KEY (`organisation_id`) REFERENCES `organisation` (`id`);

-- Unicite par organisation : deux cabinets peuvent legitimement recouvrer
-- aupres de la meme societe debitrice, donc porter le meme ICE ou la meme
-- raison sociale.
ALTER TABLE `client`
  ADD UNIQUE KEY `uk_client_org_ice`     (`organisation_id`, `ice`),
  ADD UNIQUE KEY `uk_client_org_raison`  (`organisation_id`, `raison_sociale`),
  ADD UNIQUE KEY `uk_client_org_rc`      (`organisation_id`, `rc`),
  ADD UNIQUE KEY `uk_client_org_fiscale` (`organisation_id`, `identite_fiscale`);

-- --- creance ---------------------------------------------------------------
ALTER TABLE `creance` ADD COLUMN `organisation_id` BIGINT NULL AFTER `client_id`;
UPDATE `creance` SET `organisation_id` = @org_defaut WHERE `organisation_id` IS NULL;
ALTER TABLE `creance`
  MODIFY COLUMN `organisation_id` BIGINT NOT NULL,
  ADD KEY `idx_creance_org` (`organisation_id`),
  ADD CONSTRAINT `fk_creance_org` FOREIGN KEY (`organisation_id`) REFERENCES `organisation` (`id`);

-- Le numero de facture n'est unique qu'au sein d'une organisation : deux
-- cabinets numerotent leurs factures independamment.
ALTER TABLE `creance` DROP INDEX `uk_creance_num_facture`;
ALTER TABLE `creance` ADD UNIQUE KEY `uk_creance_org_num_facture` (`organisation_id`, `num_facture`);

-- --- reglement -------------------------------------------------------------
ALTER TABLE `reglement` ADD COLUMN `organisation_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `reglement` r
  JOIN `creance` c ON c.`id` = r.`creance_id`
  SET r.`organisation_id` = c.`organisation_id`
  WHERE r.`organisation_id` IS NULL;
UPDATE `reglement` SET `organisation_id` = @org_defaut WHERE `organisation_id` IS NULL;
ALTER TABLE `reglement`
  MODIFY COLUMN `organisation_id` BIGINT NOT NULL,
  ADD KEY `idx_reglement_org` (`organisation_id`),
  ADD CONSTRAINT `fk_reglement_org` FOREIGN KEY (`organisation_id`) REFERENCES `organisation` (`id`);

-- --- relance ---------------------------------------------------------------
ALTER TABLE `relance` ADD COLUMN `organisation_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `relance` r
  JOIN `creance` c ON c.`id` = r.`creance_id`
  SET r.`organisation_id` = c.`organisation_id`
  WHERE r.`organisation_id` IS NULL;
UPDATE `relance` SET `organisation_id` = @org_defaut WHERE `organisation_id` IS NULL;
ALTER TABLE `relance`
  MODIFY COLUMN `organisation_id` BIGINT NOT NULL,
  ADD KEY `idx_relance_org` (`organisation_id`),
  ADD CONSTRAINT `fk_relance_org` FOREIGN KEY (`organisation_id`) REFERENCES `organisation` (`id`);

-- --- roles -----------------------------------------------------------------
-- SUPER_ADMIN : exploitant de la plateforme, seul a voir au-dela d'une
-- organisation. ADMIN devient administrateur DE SON organisation.
ALTER TABLE `role` MODIFY COLUMN `nom` ENUM('SUPER_ADMIN','ADMIN','AGENT') DEFAULT NULL;
INSERT INTO `role` (`nom`)
  SELECT 'SUPER_ADMIN' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `nom` = 'SUPER_ADMIN');
