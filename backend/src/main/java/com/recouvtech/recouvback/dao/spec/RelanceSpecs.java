package com.recouvtech.recouvback.dao.spec;

import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class RelanceSpecs {

    private RelanceSpecs() {
    }

    public static Specification<Relance> ownedBy(String agentEmail) {
        return (root, query, cb) -> cb.equal(
                root.get("creance").get("agentRecouv").get("email"), agentEmail);
    }

    public static Specification<Relance> hasStatut(StatutRelance statut) {
        return (root, query, cb) -> cb.equal(root.get("statutRelance"), statut);
    }

    public static Specification<Relance> forFacture(String numFacture) {
        return (root, query, cb) -> cb.equal(root.get("creance").get("numFacture"), numFacture);
    }

    public static Specification<Relance> inDepartement(Long departementId) {
        return (root, query, cb) -> cb.equal(root.get("departement").get("id"), departementId);
    }

    public static Specification<Relance> onDate(LocalDate date) {
        return (root, query, cb) -> cb.equal(root.get("dateRelance"), date);
    }

    public static Specification<Relance> matches(String term) {
        return (root, query, cb) -> {
            var creance = root.join("creance", JoinType.LEFT);
            return cb.or(
                    Specs.containsIgnoreCase(cb, creance.get("numFacture"), term),
                    Specs.containsIgnoreCase(cb, root.get("commentaire"), term));
        };
    }
}
