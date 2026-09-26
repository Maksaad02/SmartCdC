package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.service.CreanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/creances")
@RequiredArgsConstructor
public class CreanceController {

    private final CreanceService creanceService;

    @PostMapping
    public CreanceResponseDTO create(@Valid @RequestBody CreanceRequestDTO dto) {
        return creanceService.createCreance(dto);
    }

    /** Liste paginee. Parametres : page, size (max 200), sort, q (n° facture ou client), statut, clientId. */
    @GetMapping
    public Page<CreanceResponseDTO> getAll(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) StatutCreance statut,
                                           @RequestParam(required = false) Long clientId,
                                           Pageable pageable) {
        return creanceService.list(q, statut, clientId, pageable);
    }

    @GetMapping("/{numFacture}")
    public CreanceResponseDTO getByNumFacture(@PathVariable String numFacture) {
        return creanceService.getByNumFacture(numFacture);
    }

    @PutMapping("/{numFacture}")
    public CreanceResponseDTO update(@PathVariable String numFacture, @Valid @RequestBody CreanceRequestDTO dto) {
        return creanceService.updateCreance(numFacture, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{numFacture}")
    public void delete(@PathVariable String numFacture) {
        creanceService.deleteCreance(numFacture);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recalculer-penalites")
    public ResponseEntity<String> recalculerPenalites() {
        creanceService.recalculerToutesPenalites();
        return ResponseEntity.ok("Recalcul des pénalités terminé avec succès");
    }
}
