package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceStatsDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.DashboardDepartementsDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import com.recouvtech.recouvback.service.CreanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CreanceService creanceService;

    /**
     * Agregats des creances de l'appelant : toute l'entreprise pour un ADMIN, son departement pour un
     * MANAGER, son portefeuille pour un AGENT. Calcules en base. {@code departementId} (ADMIN
     * uniquement) restreint l'agregat a un departement.
     */
    @GetMapping("/stats")
    public CreanceStatsDTO stats(@RequestParam(required = false) Long departementId) {
        return creanceService.stats(departementId);
    }

    /** Comparatif consolide des departements : reserve aux ADMIN. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/departements")
    public DashboardDepartementsDTO statsParDepartement() {
        return creanceService.statsParDepartement();
    }
}
