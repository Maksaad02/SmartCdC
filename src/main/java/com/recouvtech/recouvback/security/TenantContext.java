package com.recouvtech.recouvback.security;

/**
 * Organisation de la requete en cours, portee par le thread.
 *
 * Alimentee depuis l'utilisateur authentifie (JwtAuthenticationFilter), jamais
 * depuis un parametre de requete : une organisation fournie par l'appelant
 * serait un cloisonnement contournable.
 */
public final class TenantContext {

    /**
     * Valeur utilisee tant qu'aucun utilisateur n'est authentifie (page de
     * login, erreurs). Ne correspond a aucune organisation : si une requete
     * cloisonnee s'executait malgre tout, elle ne ramenerait rien plutot que
     * tout.
     */
    public static final Long AUCUNE_ORGANISATION = -1L;

    private static final ThreadLocal<Long> COURANTE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long organisationId) {
        COURANTE.set(organisationId);
    }

    public static Long get() {
        Long valeur = COURANTE.get();
        return valeur != null ? valeur : AUCUNE_ORGANISATION;
    }

    public static void clear() {
        COURANTE.remove();
    }
}
