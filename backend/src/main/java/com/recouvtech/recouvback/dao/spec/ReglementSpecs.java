package com.recouvtech.recouvback.dao.spec;

import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class ReglementSpecs {

    private ReglementSpecs() {
    }

    /** Reglements des creances du portefeuille d'un agent. */
    public static Specification<Reglement> ownedBy(String agentEmail) {
        return (root, query, cb) -> cb.equal(
                root.get("creance").get("agentRecouv").get("email"), agentEmail);
    }

    public static Specification<Reglement> hasStatut(StatutReglement statut) {
        return (root, query, cb) -> cb.equal(root.get("statut"), statut);
    }

    public static Specification<Reglement> forFacture(String numFacture) {
        return (root, query, cb) -> cb.equal(root.get("creance").get("numFacture"), numFacture);
    }

    public static Specification<Reglement> matches(String term) {
        return (root, query, cb) -> {
            var creance = root.join("creance", JoinType.LEFT);
            return cb.or(
                    Specs.containsIgnoreCase(cb, creance.get("numFacture"), term),
                    Specs.containsIgnoreCase(cb, root.get("reference"), term),
                    Specs.containsIgnoreCase(cb, creance.join("client", JoinType.LEFT).get("raisonSociale"), term));
        };
    }
}
