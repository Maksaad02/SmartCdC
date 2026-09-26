package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.service.ReglementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reglements")
@RequiredArgsConstructor
public class ReglementController {

    private final ReglementService reglementService;

    @PostMapping
    public ReglementResponseDTO create(@Valid @RequestBody ReglementRequestDTO dto) {
        return reglementService.create(dto);
    }

    /** Liste paginee. Parametres : page, size (max 200), sort, q, statut, numFacture. */
    @GetMapping()
    public Page<ReglementResponseDTO> getAll(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) StatutReglement statut,
                                             @RequestParam(required = false) String numFacture,
                                             Pageable pageable) {
        return reglementService.list(q, statut, numFacture, pageable);
    }

    @GetMapping("/{id}")
    public ReglementResponseDTO getById(@PathVariable Long id) {
        return reglementService.getById(id);
    }

    @PutMapping("/{id}")
    public ReglementResponseDTO update(@PathVariable Long id, @Valid @RequestBody ReglementRequestDTO dto) {
        return reglementService.update(id, dto);
    }

    @PatchMapping("/{id}/status")
    public ReglementResponseDTO updateStatus(@PathVariable Long id, @RequestBody StatutReglement newStatus) {
        return reglementService.updateStatus(id, newStatus);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reglementService.delete(id);
    }
}
