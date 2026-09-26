package com.recouvtech.recouvback.dto.CreanceDTO;

import com.recouvtech.recouvback.entity.enums.StatutCreance;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agregats du tableau de bord, calcules en base, sur le perimetre de l'appelant (ADMIN : toute
 * l'entreprise ; MANAGER : son departement ; AGENT : son portefeuille).
 *
 * tauxRecouvrement : pourcentage encaisse / (facture + penalites).
 */
public record CreanceStatsDTO(
        long totalCreances,
        BigDecimal montantTotal,
        BigDecimal montantEncaisse,
        BigDecimal montantPenalites,
        BigDecimal tauxRecouvrement,
        Map<StatutCreance, Long> parStatut) {
}
