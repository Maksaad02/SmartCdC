-- Facture PDF d'origine jointe a une creance (import automatique des factures).
--
-- Une facture au plus par creance. Le departement est celui de la creance, garanti par la cle
-- etrangere composite (meme principe que reglement et relance en V7) : le filtre de departement
-- s'applique aussi aux documents.
CREATE TABLE `creance_document` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `creance_id`     BIGINT        NOT NULL,
  `departement_id` BIGINT        NOT NULL,
  `nom_fichier`    VARCHAR(255)  NOT NULL,
  `taille`         BIGINT        NOT NULL,
  `sha256`         VARCHAR(64)   NOT NULL,
  `contenu`        LONGBLOB      NOT NULL,
  `date_ajout`     DATETIME(6)   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creance_document_creance` (`creance_id`),
  KEY `idx_creance_document_dept` (`departement_id`),
  CONSTRAINT `fk_creance_document_dept` FOREIGN KEY (`departement_id`) REFERENCES `departement` (`id`),
  CONSTRAINT `fk_creance_document_creance_dept` FOREIGN KEY (`creance_id`, `departement_id`)
      REFERENCES `creance` (`id`, `departement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
