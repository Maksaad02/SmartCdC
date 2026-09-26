package com.recouvtech.recouvback.dto.CreanceDTO;

import java.math.BigDecimal;

/** Performance d'un departement (comparatif du tableau de bord ADMIN). */
public record DepartementStatsDTO(
        Long departementId,
        String nom,
        long nbCreances,
        long nbEnRetard,
        BigDecimal montantFacture,
        BigDecimal montantPenalites,
        BigDecimal montantEncaisse,
        BigDecimal solde,
        BigDecimal tauxRecouvrement) {
}
