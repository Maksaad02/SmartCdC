-- RC, ICE et identite fiscale sont facultatifs dans l'interface, mais les colonnes etaient NOT NULL
-- et soumises a une contrainte d'unicite par organisation. Un client sans ces valeurs etait donc
-- enregistre avec une chaine VIDE, et le deuxieme client sans RC de la meme organisation echouait
-- ("Duplicate entry '1-' for key uk_client_org_ice", erreur 500).
--
-- En NULL, MySQL n'applique pas l'unicite entre valeurs absentes : plusieurs clients peuvent ne pas
-- avoir d'ICE, mais deux clients ne peuvent toujours pas partager le meme ICE renseigne.
ALTER TABLE client
  MODIFY rc               VARCHAR(255) NULL,
  MODIFY ice              VARCHAR(255) NULL,
  MODIFY identite_fiscale VARCHAR(255) NULL;

UPDATE client SET rc               = NULL WHERE rc               IS NOT NULL AND TRIM(rc)               = '';
UPDATE client SET ice              = NULL WHERE ice              IS NOT NULL AND TRIM(ice)              = '';
UPDATE client SET identite_fiscale = NULL WHERE identite_fiscale IS NOT NULL AND TRIM(identite_fiscale) = '';
