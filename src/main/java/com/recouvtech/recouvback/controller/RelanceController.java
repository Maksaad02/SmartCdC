package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.RelanceDTO.RelanceRequestDTO;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceResponseDTO;
import com.recouvtech.recouvback.service.RelanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public RelanceResponseDTO create(@RequestBody RelanceRequestDTO dto) {
        return relanceService.create(dto);
    }

    @GetMapping
    public List<RelanceResponseDTO> getAll() {
        return relanceService.getAll();
    }

    @GetMapping("/{id}")
    public RelanceResponseDTO getById(@PathVariable Long id) {
        return relanceService.getById(id);
    }

    @PutMapping("/{id}")
    public RelanceResponseDTO update(@PathVariable Long id, @RequestBody RelanceRequestDTO dto) {
        return relanceService.update(id, dto);
    }

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
