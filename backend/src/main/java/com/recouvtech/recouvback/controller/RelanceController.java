package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.RelanceDTO.RelanceRequestDTO;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceResponseDTO;
import com.recouvtech.recouvback.service.RelanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relances")
@RequiredArgsConstructor
public class RelanceController {

    private final RelanceService relanceService;

    @PostMapping
    public RelanceResponseDTO create(@Valid @RequestBody RelanceRequestDTO dto) {
        return relanceService.create(dto);
    }

    /**
     * Liste paginee. Parametres : page, size (max 200), sort, q, statutRelance, numFacture, dateRelance,
     * departementId (ADMIN uniquement).
     */
    @GetMapping
    public Page<RelanceResponseDTO> getAll(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) StatutRelance statutRelance,
                                           @RequestParam(required = false) String numFacture,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateRelance,
                                           @RequestParam(required = false) Long departementId,
                                           Pageable pageable) {
        return relanceService.list(q, statutRelance, numFacture, dateRelance, departementId, pageable);
    }

    @GetMapping("/{id}")
    public RelanceResponseDTO getById(@PathVariable Long id) {
        return relanceService.getById(id);
    }

    @PutMapping("/{id}")
    public RelanceResponseDTO update(@PathVariable Long id, @Valid @RequestBody RelanceRequestDTO dto) {
        return relanceService.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        relanceService.delete(id);
    }

    /**
     * Envoyer une relance manuellement
     */
    @PostMapping("/{id}/envoyer")
    public ResponseEntity<String> envoyerRelance(@PathVariable Long id,
                                               Authentication authentication) {
        try {
            String agentName = authentication.getName();
            boolean success = relanceService.envoyerRelanceManuellement(id, agentName);

            if (success) {
                return ResponseEntity.ok("Relance envoyée avec succès");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'envoi de la relance");
            }

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    /**
     * Récupérer les relances en attente d'envoi
     */
    @GetMapping("/en-attente")
    public ResponseEntity<List<RelanceResponseDTO>> getRelancesEnAttente() {
        List<RelanceResponseDTO> relances = relanceService.getRelancesEnAttente();
        return ResponseEntity.ok(relances);
    }

    /**
     * Récupérer les relances en attente pour une créance spécifique
     */
    @GetMapping("/en-attente/creance/{numFacture}")
    public ResponseEntity<List<RelanceResponseDTO>> getRelancesEnAttenteByCreance(
            @PathVariable String numFacture) {
        List<RelanceResponseDTO> relances = relanceService.getRelancesEnAttenteByCreance(numFacture);
        return ResponseEntity.ok(relances);
    }
}
