package com.recouvtech.recouvback.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acces a l'identite de l'appelant, lue uniquement depuis le SecurityContext.
 *
 * Point important : l'identite ne doit jamais provenir du corps ou des
 * parametres de la requete (un champ agentName fourni par le client permettait
 * d'agir au nom de n'importe quel agent).
 */
@Component
public class CurrentUser {

    /** Email (username) de l'appelant, ou null si non authentifie. */
    public String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * true si l'appelant est ADMIN ou si la ressource lui appartient.
     * ownerEmail null (ressource orpheline) : reserve aux ADMIN.
     */
    public boolean canAccess(String ownerEmail) {
        return isAdmin() || (ownerEmail != null && ownerEmail.equals(email()));
    }
}
