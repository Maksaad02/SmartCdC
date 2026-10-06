package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.FactureDTO.FactureExtractionDTO;
import com.recouvtech.recouvback.service.facture.FactureExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Lecture automatique d'une facture PDF pour pre-remplir une creance (tout role authentifie : un
 * AGENT cree aussi des creances). Ne cree rien : le formulaire est relu puis enregistre normalement.
 */
@RestController
@RequestMapping("/api/factures/extraction")
@RequiredArgsConstructor
public class FactureExtractionController {

    private final FactureExtractionService extractionService;

    /** Le formulaire n'affiche le bouton d'import que si la fonctionnalite est configuree. */
    @GetMapping("/statut")
    public Map<String, Boolean> statut() {
        return Map.of("actif", extractionService.isActif());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FactureExtractionDTO extraire(@RequestPart("fichier") MultipartFile fichier) throws IOException {
        return extractionService.extraire(fichier.getBytes());
    }
}
