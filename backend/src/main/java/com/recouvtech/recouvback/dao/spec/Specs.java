package com.recouvtech.recouvback.dao.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/** Aides communes aux Specifications de recherche. */
final class Specs {

    private Specs() {
    }

    /**
     * Recherche "contient", insensible a la casse. Les jokers LIKE saisis par
     * l'utilisateur (% et _) sont echappes : sans cela, "%" ramenait toute la
     * table et "_" faussait la recherche.
     */
    static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> column, String term) {
        String escaped = term.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(column), "%" + escaped + "%", '\\');
    }
}
