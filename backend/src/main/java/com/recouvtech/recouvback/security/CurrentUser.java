package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.entity.Utilisateur;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acces a l'identite de l'appelant, lue uniquement depuis le SecurityContext.
 *
 * L'identite et le departement ne doivent jamais provenir du corps ou des parametres de la requete :
 * un champ fourni par le client permettrait d'agir au nom d'un autre agent ou dans un autre
 * departement.
 *
 * Les roles sont exacts (ROLE_ADMIN, ROLE_MANAGER, ROLE_AGENT) : la hierarchie ADMIN > MANAGER > AGENT
 * ne joue qu'a l'autorisation (hasRole), pas dans les autorites portees par l'authentification.
 */
@Component
public class CurrentUser {

    /**
     * Departement qui ne correspond a rien : utilise si un MANAGER ou un AGENT n'a (anormalement) pas
     * de departement. Le filtre ne ramene alors aucune ligne, plutot que tout (echec ferme).
     */
    public static final Long AUCUN_DEPARTEMENT = -1L;

    /** Email (username) de l'appelant, ou null si non authentifie. */
    public String email() {
        Authentication auth = authentication();
        return auth != null ? auth.getName() : null;
    }

    /** Departement de l'appelant, ou null (ADMIN, ou hors requete authentifiee). */
    public Long departementId() {
        Authentication auth = authentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u && u.getDepartement() != null) {
            return u.getDepartement().getId();
        }
        return null;
    }

    /** Administrateur d'entreprise : vision globale de tous les departements. */
    public boolean isAdmin() {
        return hasAuthority("ROLE_ADMIN");
    }

    /** Gestionnaire de departement (role exact, hors ADMIN). */
    public boolean isManager() {
        return hasAuthority("ROLE_MANAGER");
    }

    /** Agent : limite a son portefeuille, dans son departement. */
    public boolean isAgent() {
        return hasAuthority("ROLE_AGENT");
    }

    /** ADMIN ou MANAGER : voit tout ce qui releve de son perimetre, pas seulement son portefeuille. */
    public boolean isManagerOrAbove() {
        return isAdmin() || isManager();
    }

    /**
     * Departement a imposer comme filtre SQL, ou null pour ne pas filtrer.
     *
     * null pour un ADMIN et pour tout contexte non authentifie (taches planifiees, ecouteurs
     * asynchrones : ils travaillent sur toute l'entreprise). Pour un MANAGER ou un AGENT, son
     * departement -- ou AUCUN_DEPARTEMENT s'il n'en a pas, pour echouer ferme.
     */
    public Long departementFiltre() {
        if (isAdmin()) {
            return null;
        }
        if (isManager() || isAgent()) {
            Long departement = departementId();
            return departement != null ? departement : AUCUN_DEPARTEMENT;
        }
        return null;
    }

    /**
     * true si l'appelant peut acceder a une ressource du departement donne, dont le responsable de
     * dossier est ownerEmail.
     *
     * ADMIN : tout. MANAGER : tout son departement. AGENT : son portefeuille dans son departement.
     * Une ressource orpheline (ownerEmail null) est reservee aux ADMIN et MANAGER.
     * Defense en profondeur : le filtre SQL "departement" ecarte deja les autres departements.
     */
    public boolean canAccess(Long ressourceDepartementId, String ownerEmail) {
        if (isAdmin()) {
            return true;
        }
        Long monDepartement = departementId();
        boolean memeDepartement = monDepartement != null && monDepartement.equals(ressourceDepartementId);
        if (isManager()) {
            return memeDepartement;
        }
        return isAgent() && memeDepartement && ownerEmail != null && ownerEmail.equals(email());
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
