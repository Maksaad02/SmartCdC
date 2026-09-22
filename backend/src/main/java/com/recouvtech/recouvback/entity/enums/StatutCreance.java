package com.recouvtech.recouvback.entity.enums;

public enum StatutCreance {
    PAYEE,          // Créance entièrement payée
    IMPAYEE,        // Créance non payée
    EN_RETARD,      // Créance en retard de paiement
    PENALISEE,      // Créance pénalisée (retard >= 60 jours)
    PARTIELLEMENT_PAYEE  // Créance partiellement payée
}
