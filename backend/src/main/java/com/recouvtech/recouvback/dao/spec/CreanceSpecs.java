package com.recouvtech.recouvback.dao.spec;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class CreanceSpecs {

    private CreanceSpecs() {
    }

    public static Specification<Creance> ownedBy(String agentEmail) {
        return (root, query, cb) -> cb.equal(root.get("agentRecouv").get("email"), agentEmail);
    }

    public static Specification<Creance> forClient(Long clientId) {
        return (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
    }

    public static Specification<Creance> hasStatut(StatutCreance statut) {
        return (root, query, cb) -> cb.equal(root.get("statut"), statut);
    }

    /** Numero de facture ou raison sociale du client. */
    public static Specification<Creance> matches(String term) {
        return (root, query, cb) -> cb.or(
                Specs.containsIgnoreCase(cb, root.get("numFacture"), term),
                Specs.containsIgnoreCase(cb, root.join("client", JoinType.LEFT).get("raisonSociale"), term));
    }
}
