package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.service.ReglementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reglements")
@RequiredArgsConstructor
public class ReglementController {

    private final ReglementService reglementService;

    @PostMapping
    public ReglementResponseDTO create(@RequestBody ReglementRequestDTO dto) {
        return reglementService.create(dto);
    }

    @GetMapping()
    public List<ReglementResponseDTO> getAll() {
        return reglementService.getAll();
    }

    @GetMapping("/{id}")
    public ReglementResponseDTO getById(@PathVariable Long id) {
        return reglementService.getById(id);
    }

    @PutMapping("/{id}")
    public ReglementResponseDTO update(@PathVariable Long id, @RequestBody ReglementRequestDTO dto) {
        return reglementService.update(id, dto);
    }

    @PatchMapping("/{id}/status")
    public ReglementResponseDTO updateStatus(@PathVariable Long id, @RequestBody StatutReglement newStatus) {
        return reglementService.updateStatus(id, newStatus);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reglementService.delete(id);
    }
}
