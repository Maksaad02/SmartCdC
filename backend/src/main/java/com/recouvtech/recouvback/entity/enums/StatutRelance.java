package com.recouvtech.recouvback.entity.enums;

public enum StatutRelance {
    EN_ATTENTE,     // Relance créée mais pas encore effectuée
    ENVOYEE,        // Relance envoyée
    EFFECTUEE,      // Relance effectuée
    ANNULEE,        // Relance annulée
    ECHEC,          // Relance en échec
    REPORTEE        // Relance reportée à plus tard
}

