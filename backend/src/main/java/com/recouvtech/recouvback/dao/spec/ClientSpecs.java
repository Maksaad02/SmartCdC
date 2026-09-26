package com.recouvtech.recouvback.dao.spec;

import com.recouvtech.recouvback.entity.Client;
import org.springframework.data.jpa.domain.Specification;

public final class ClientSpecs {

    private ClientSpecs() {
    }

    /** Portefeuille d'un agent. Le cloisonnement par departement est assure par le filtre Hibernate. */
    public static Specification<Client> ownedBy(String agentEmail) {
        return (root, query, cb) -> cb.equal(root.get("agentRecouv").get("email"), agentEmail);
    }

    public static Specification<Client> matches(String term) {
        return (root, query, cb) -> cb.or(
                Specs.containsIgnoreCase(cb, root.get("raisonSociale"), term),
                Specs.containsIgnoreCase(cb, root.get("email"), term),
                Specs.containsIgnoreCase(cb, root.get("telephone"), term),
                cb.equal(root.get("ice"), term));
    }
}
