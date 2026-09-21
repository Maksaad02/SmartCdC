package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UtilisateurResponseDTO> create(@RequestBody UtilisateurRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(utilisateurService.create(dto));
    }


    @GetMapping("/me")
    public ResponseEntity<UtilisateurResponseDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(utilisateurService.getByEmail(userDetails.getUsername()));
    }

    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UtilisateurResponseDTO> getAll() {
        return utilisateurService.getAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public UtilisateurResponseDTO getById(@PathVariable Long id) {
        return utilisateurService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UtilisateurResponseDTO update(@PathVariable Long id, @RequestBody UtilisateurRequestDTO dto) {
        return utilisateurService.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        utilisateurService.delete(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/role") /**/
    public UtilisateurResponseDTO updateRole(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return utilisateurService.updateRole(id, payload.get("role"));
    }
}