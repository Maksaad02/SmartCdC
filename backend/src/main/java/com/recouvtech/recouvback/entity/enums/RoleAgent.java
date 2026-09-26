package com.recouvtech.recouvback.entity.enums;

public enum RoleAgent {

    /** Administrateur d'entreprise : vision globale, gere tous les departements. */
    ADMIN,

    /** Gestionnaire de departement : voit et gere tout son departement, rien d'un autre. */
    MANAGER,

    /** Agent de recouvrement : limite a son propre portefeuille, dans son departement. */
    AGENT,
}
