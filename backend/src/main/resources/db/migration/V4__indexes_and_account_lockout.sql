-- Index sur les colonnes filtrees ou triees par les listes paginees et les
-- statistiques (jusqu'ici seules les cles etrangeres etaient indexees).
--
-- creance : (organisation, supprimee, statut) sert le filtre @SQLRestriction
-- "supprimee = false" applique a TOUTES les requetes, le cloisonnement par
-- organisation et le filtre par statut ; echeance sert les calculs de retard.
CREATE INDEX idx_creance_org_supprimee_statut ON creance (organisation_id, supprimee, statut);
CREATE INDEX idx_creance_echeance ON creance (echeance);
CREATE INDEX idx_relance_statut ON relance (statut_relance);
CREATE INDEX idx_relance_date ON relance (date_relance);
CREATE INDEX idx_reglement_statut ON reglement (statut);

-- Verrouillage de compte apres echecs de connexion repetes (anti brute-force).
ALTER TABLE utilisateur
  ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN locked_until DATETIME NULL;
