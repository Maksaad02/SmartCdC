package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.DepartementDTO.DepartementRequestDTO;
import com.recouvtech.recouvback.dto.DepartementDTO.DepartementResponseDTO;
import com.recouvtech.recouvback.service.DepartementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departements")
@RequiredArgsConstructor
public class DepartementController {

    private final DepartementService departementService;

    /** Tout utilisateur authentifie ; un MANAGER ou un AGENT ne recoit que son departement. */
    @GetMapping
    public List<DepartementResponseDTO> list() {
        return departementService.list();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DepartementResponseDTO> create(@Valid @RequestBody DepartementRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departementService.create(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public DepartementResponseDTO update(@PathVariable Long id, @Valid @RequestBody DepartementRequestDTO dto) {
        return departementService.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
