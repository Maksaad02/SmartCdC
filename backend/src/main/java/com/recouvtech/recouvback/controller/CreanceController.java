package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceDocumentInfoDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.service.CreanceDocumentService;
import com.recouvtech.recouvback.service.CreanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/creances")
@RequiredArgsConstructor
public class CreanceController {

    private final CreanceService creanceService;
    private final CreanceDocumentService documentService;

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

    /** Joint (ou remplace) la facture PDF d'origine. Memes droits que la creance. */
    @PostMapping(path = "/{numFacture}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreanceDocumentInfoDTO joindreDocument(@PathVariable String numFacture,
                                                  @RequestPart("fichier") MultipartFile fichier) throws IOException {
        return documentService.enregistrer(numFacture, fichier.getOriginalFilename(), fichier.getBytes());
    }

    @GetMapping("/{numFacture}/document/info")
    public CreanceDocumentInfoDTO infoDocument(@PathVariable String numFacture) {
        return documentService.info(numFacture);
    }

    /** Telechargement (jamais affiche en ligne dans l'origine de l'application). */
    @GetMapping("/{numFacture}/document")
    public ResponseEntity<byte[]> telechargerDocument(@PathVariable String numFacture) {
        CreanceDocumentService.Fichier fichier = documentService.telecharger(numFacture);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fichier.nom(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(fichier.contenu());
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
