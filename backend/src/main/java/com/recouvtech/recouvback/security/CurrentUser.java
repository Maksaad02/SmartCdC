package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.entity.Utilisateur;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acces a l'identite de l'appelant, lue uniquement depuis le SecurityContext.
 *
 * L'identite ne doit jamais provenir du corps ou des parametres de la requete :
 * un champ agentName fourni par le client permettait d'agir au nom de n'importe
 * quel agent.
 */
@Component
public class CurrentUser {

    /** Email (username) de l'appelant, ou null si non authentifie. */
    public String email() {
        Authentication auth = authentication();
        return auth != null ? auth.getName() : null;
    }

    /** Organisation de l'appelant, ou null hors requete authentifiee. */
    public Long organisationId() {
        Authentication auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u && u.getOrganisation() != null) {
            return u.getOrganisation().getId();
        }
        return null;
    }

    /** Exploitant de la plateforme : seul role voyant au-dela d'une organisation. */
    public boolean isSuperAdmin() {
        return hasAuthority("ROLE_SUPER_ADMIN");
    }

    /**
     * Administrateur de SON organisation.
     *
     * Le cloisonnement inter-organisations n'est pas assure ici : Hibernate
     * filtre deja sur @TenantId. Ce drapeau ne distingue que "tout voir dans mon
     * organisation" de "seulement mon portefeuille".
     */
    public boolean isAdmin() {
        return isSuperAdmin() || hasAuthority("ROLE_ADMIN");
    }

    /**
     * true si l'appelant est administrateur de son organisation, ou si la
     * ressource lui appartient. ownerEmail null (ressource orpheline) : reserve
     * aux administrateurs.
     */
    public boolean canAccess(String ownerEmail) {
        return isAdmin() || (ownerEmail != null && ownerEmail.equals(email()));
    }

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth : null;
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = authentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
