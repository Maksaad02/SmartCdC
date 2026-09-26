package com.recouvtech.recouvback.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Neutralise le tri fourni par le client.
 *
 * Spring Data accepte n'importe quel chemin de propriete dans ?sort=. Sans liste
 * blanche, on pouvait trier par un champ sensible (ex. agentRecouv.motDePasse : l'ordre
 * revele des informations sur les hash), ou provoquer une erreur 500 avec une
 * propriete inconnue. Toute propriete non autorisee est ignoree.
 */
public final class PageRequests {

    private PageRequests() {
    }

    public static Pageable sanitize(Pageable requested, Set<String> allowedSortProperties, Sort defaultSort) {
        List<Sort.Order> orders = requested.getSort().stream()
                .filter(o -> allowedSortProperties.contains(o.getProperty()))
                .toList();
        Sort sort = orders.isEmpty() ? defaultSort : Sort.by(orders);
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort);
    }
}
