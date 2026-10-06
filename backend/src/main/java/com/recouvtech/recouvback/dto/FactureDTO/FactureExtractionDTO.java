package com.recouvtech.recouvback.dto.FactureDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Champs lus sur une facture PDF, pour PRE-REMPLIR le formulaire de creance : rien n'est enregistre.
 * Les valeurs absentes ou invalides sont null ; avertissements liste ce que l'utilisateur doit verifier.
 *
 * clientTrouve : raison sociale d'un client existant (dans le perimetre de l'appelant) correspondant
 * a l'ICE ou au nom lus, sinon null. source : TEXTE (couche texte du PDF) ou SCAN (lecture visuelle).
 */
public record FactureExtractionDTO(
        String numFacture,
        LocalDate dateEmission,
        LocalDate echeance,
        BigDecimal montantFacture,
        String clientRaisonSociale,
        String clientIce,
        String clientTrouve,
        String source,
        List<String> avertissements) {
}
