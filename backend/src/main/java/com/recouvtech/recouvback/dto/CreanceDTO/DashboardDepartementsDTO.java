package com.recouvtech.recouvback.dto.CreanceDTO;

import java.util.List;

/**
 * Vue consolidee ADMIN : indicateurs globaux de l'entreprise et detail par departement.
 * Le global est la somme des departements (calculee sur les memes lignes, donc toujours coherente).
 */
public record DashboardDepartementsDTO(
        DepartementStatsDTO global,
        List<DepartementStatsDTO> departements) {
}
