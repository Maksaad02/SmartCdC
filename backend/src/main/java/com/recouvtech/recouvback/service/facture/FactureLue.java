package com.recouvtech.recouvback.service.facture;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Reponse structuree demandee au modele (schema JSON derive de ce record). Tout est texte, et une
 * valeur absente est une chaine vide : le serveur convertit et controle lui-meme dates et montant.
 */
public record FactureLue(
        @JsonPropertyDescription("Numéro de la facture tel qu'imprimé. Chaîne vide s'il n'apparaît pas.")
        String numFacture,
        @JsonPropertyDescription("Date d'émission de la facture, au format AAAA-MM-JJ. Chaîne vide si absente.")
        String dateEmission,
        @JsonPropertyDescription("Date d'échéance (date limite de paiement), au format AAAA-MM-JJ. Si seule une condition "
                + "de délai est indiquée (ex. « 30 jours date de facture »), calcule-la et signale-le dans remarques. "
                + "Chaîne vide si rien ne permet de la déterminer.")
        String echeance,
        @JsonPropertyDescription("Montant TOTAL TTC à payer, nombre décimal avec un point (ex. 12500.00), sans devise ni "
                + "séparateur de milliers. Chaîne vide si absent.")
        String montantTTC,
        @JsonPropertyDescription("Raison sociale du CLIENT facturé (le débiteur), pas de l'entreprise qui émet la facture. "
                + "Chaîne vide si absente.")
        String clientRaisonSociale,
        @JsonPropertyDescription("ICE du CLIENT facturé (pas celui de l'émetteur), chiffres uniquement. Chaîne vide si absent.")
        String clientIce,
        @JsonPropertyDescription("Points que l'utilisateur doit vérifier, en français, phrases courtes (valeur ambiguë, "
                + "calculée ou illisible). Liste vide si tout est clair.")
        List<String> remarques) {
}
