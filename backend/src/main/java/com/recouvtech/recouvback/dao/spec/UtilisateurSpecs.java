package com.recouvtech.recouvback.dao.spec;

import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import org.springframework.data.jpa.domain.Specification;

public final class UtilisateurSpecs {

    private UtilisateurSpecs() {
    }

    /**
     * Utilisateur n'est pas cloisonne par le filtre de departement (voir l'entite) : le
     * cloisonnement d'un MANAGER est donc explicite ici.
     */
    public static Specification<Utilisateur> inDepartement(Long departementId) {
        return (root, query, cb) -> cb.equal(root.get("departement").get("id"), departementId);
    }

    public static Specification<Utilisateur> hasRole(RoleAgent role) {
        return (root, query, cb) -> cb.equal(root.get("role").get("nom"), role);
    }

    public static Specification<Utilisateur> matches(String term) {
        return (root, query, cb) -> cb.or(
                Specs.containsIgnoreCase(cb, root.get("nom"), term),
                Specs.containsIgnoreCase(cb, root.get("email"), term));
    }
}
