package com.recouvtech.recouvback.entity.enums;

public enum RoleAgent {

    /** Exploitant de la plateforme : seul role voyant au-dela d'une organisation. */
    SUPER_ADMIN,

    /** Administrateur DE SON organisation. Ne voit rien des autres. */
    ADMIN,

    /** Agent de recouvrement : limite a son propre portefeuille. */
    AGENT,
}
