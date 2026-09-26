-- Single-tenant avec departements.
--
-- Une instance = une entreprise. Le cloisonnement "par organisation" (V2) est remplace par un
-- cloisonnement "par departement" : un ADMIN voit toute l'entreprise, un MANAGER ou un AGENT
-- uniquement son departement. Le role SUPER_ADMIN (exploitant de plateforme) disparait.
--
-- departement_id est porte par chaque table metier (comme l'etait organisation_id) : le filtre
-- s'applique sur une seule colonne indexee, sans jointure. La coherence de cette denormalisation
-- est garantie EN BASE par des cles etrangeres composites : une creance ne peut pas pointer un
-- client d'un autre departement, un reglement ou une relance ne peuvent pas quitter le departement
-- de leur creance, meme en cas de bug applicatif.
--
-- Donnees existantes : tout est rattache au departement "Siege". Les unicites redeviennent
-- globales a l'entreprise ; si deux anciennes organisations portaient le meme ICE ou le meme
-- numero de facture, la migration echoue (volontairement) plutot que de fusionner en silence.

-- --- departement -----------------------------------------------------------
CREATE TABLE `departement` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `nom`           VARCHAR(255) NOT NULL,
  `code`          VARCHAR(30)  NOT NULL,
  `actif`         BIT(1)       NOT NULL DEFAULT b'1',
  `date_creation` DATETIME(6)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_departement_nom`  (`nom`),
  UNIQUE KEY `uk_departement_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `departement` (`nom`, `code`, `actif`, `date_creation`)
VALUES ('Siège', 'SIEGE', b'1', NOW(6));

SET @dep = (SELECT `id` FROM `departement` WHERE `code` = 'SIEGE');

-- --- roles : ADMIN > MANAGER > AGENT ---------------------------------------
UPDATE `utilisateur`
   SET `role_id` = (SELECT `id` FROM `role` WHERE `nom` = 'ADMIN')
 WHERE `role_id` IN (SELECT `id` FROM `role` WHERE `nom` = 'SUPER_ADMIN');
DELETE FROM `role` WHERE `nom` = 'SUPER_ADMIN';
ALTER TABLE `role` MODIFY COLUMN `nom` ENUM('ADMIN','MANAGER','AGENT') DEFAULT NULL;
INSERT INTO `role` (`nom`)
  SELECT 'MANAGER' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `nom` = 'MANAGER');

-- --- utilisateur : departement facultatif (NULL pour un ADMIN) ---------------
ALTER TABLE `utilisateur`
  DROP FOREIGN KEY `fk_utilisateur_org`,
  DROP INDEX `idx_utilisateur_org`,
  DROP COLUMN `organisation_id`,
  ADD COLUMN `departement_id` BIGINT NULL AFTER `role_id`;
UPDATE `utilisateur` SET `departement_id` = @dep;
UPDATE `utilisateur` u JOIN `role` r ON r.`id` = u.`role_id`
   SET u.`departement_id` = NULL WHERE r.`nom` = 'ADMIN';
ALTER TABLE `utilisateur`
  ADD KEY `idx_utilisateur_dept` (`departement_id`),
  ADD CONSTRAINT `fk_utilisateur_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`);

-- --- client ----------------------------------------------------------------
ALTER TABLE `client`
  DROP FOREIGN KEY `fk_client_org`,
  DROP INDEX `idx_client_org`,
  DROP INDEX `uk_client_org_ice`,
  DROP INDEX `uk_client_org_raison`,
  DROP INDEX `uk_client_org_rc`,
  DROP INDEX `uk_client_org_fiscale`,
  DROP COLUMN `organisation_id`,
  ADD COLUMN `departement_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `client` SET `departement_id` = @dep;
ALTER TABLE `client`
  MODIFY COLUMN `departement_id` BIGINT NOT NULL,
  ADD KEY `idx_client_dept` (`departement_id`),
  ADD CONSTRAINT `fk_client_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`),
  ADD UNIQUE KEY `uk_client_ice`     (`ice`),
  ADD UNIQUE KEY `uk_client_raison`  (`raison_sociale`),
  ADD UNIQUE KEY `uk_client_rc`      (`rc`),
  ADD UNIQUE KEY `uk_client_fiscale` (`identite_fiscale`),
  ADD UNIQUE KEY `uk_client_id_dept` (`id`, `departement_id`);

-- --- creance ---------------------------------------------------------------
ALTER TABLE `creance`
  DROP FOREIGN KEY `fk_creance_org`,
  DROP INDEX `idx_creance_org`,
  DROP INDEX `idx_creance_org_supprimee_statut`,
  DROP INDEX `uk_creance_org_num_facture`,
  DROP COLUMN `organisation_id`,
  ADD COLUMN `departement_id` BIGINT NULL AFTER `client_id`;
UPDATE `creance` c JOIN `client` cl ON cl.`id` = c.`client_id`
   SET c.`departement_id` = cl.`departement_id`;
UPDATE `creance` SET `departement_id` = @dep WHERE `departement_id` IS NULL;
ALTER TABLE `creance`
  MODIFY COLUMN `departement_id` BIGINT NOT NULL,
  ADD KEY `idx_creance_dept_supprimee_statut` (`departement_id`, `supprimee`, `statut`),
  ADD CONSTRAINT `fk_creance_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`),
  ADD UNIQUE KEY `uk_creance_num_facture` (`num_facture`),
  ADD UNIQUE KEY `uk_creance_id_dept` (`id`, `departement_id`),
  ADD CONSTRAINT `fk_creance_client_dept` FOREIGN KEY (`client_id`, `departement_id`)
      REFERENCES `client` (`id`, `departement_id`);

-- --- reglement -------------------------------------------------------------
ALTER TABLE `reglement`
  DROP FOREIGN KEY `fk_reglement_org`,
  DROP INDEX `idx_reglement_org`,
  DROP COLUMN `organisation_id`,
  ADD COLUMN `departement_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `reglement` r JOIN `creance` c ON c.`id` = r.`creance_id`
   SET r.`departement_id` = c.`departement_id`;
UPDATE `reglement` SET `departement_id` = @dep WHERE `departement_id` IS NULL;
ALTER TABLE `reglement`
  MODIFY COLUMN `departement_id` BIGINT NOT NULL,
  ADD KEY `idx_reglement_dept` (`departement_id`),
  ADD CONSTRAINT `fk_reglement_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`),
  ADD CONSTRAINT `fk_reglement_creance_dept` FOREIGN KEY (`creance_id`, `departement_id`)
      REFERENCES `creance` (`id`, `departement_id`);

-- --- relance ---------------------------------------------------------------
ALTER TABLE `relance`
  DROP FOREIGN KEY `fk_relance_org`,
  DROP INDEX `idx_relance_org`,
  DROP COLUMN `organisation_id`,
  ADD COLUMN `departement_id` BIGINT NULL AFTER `id_agent_recouv`;
UPDATE `relance` r JOIN `creance` c ON c.`id` = r.`creance_id`
   SET r.`departement_id` = c.`departement_id`;
UPDATE `relance` SET `departement_id` = @dep WHERE `departement_id` IS NULL;
ALTER TABLE `relance`
  MODIFY COLUMN `departement_id` BIGINT NOT NULL,
  ADD KEY `idx_relance_dept` (`departement_id`),
  ADD CONSTRAINT `fk_relance_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`),
  ADD CONSTRAINT `fk_relance_creance_dept` FOREIGN KEY (`creance_id`, `departement_id`)
      REFERENCES `creance` (`id`, `departement_id`);

-- --- plus d'organisation ---------------------------------------------------
DROP TABLE `organisation`;
