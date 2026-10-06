package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.CreanceDocumentRepository;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceDocumentInfoDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.CreanceDocument;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.service.facture.PdfFacture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Facture PDF jointe a une creance. Memes droits que la creance elle-meme : quiconque peut la lire
 * (ADMIN, MANAGER du departement, AGENT responsable) peut joindre ou telecharger sa facture.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreanceDocumentService {

    private static final int NOM_MAX = 200;

    private final CreanceDocumentRepository documentRepository;
    private final CreanceService creanceService;

    public record Fichier(String nom, byte[] contenu) {
    }

    /** Joint (ou remplace) la facture PDF de la creance. */
    @Transactional
    public CreanceDocumentInfoDTO enregistrer(String numFacture, String nomFichier, byte[] pdf) {
        PdfFacture.verifier(pdf);
        Creance creance = creanceService.chargerAccessible(numFacture);
        CreanceDocument document = documentRepository.findByCreanceId(creance.getId()).orElseGet(() -> {
            CreanceDocument nouveau = new CreanceDocument();
            nouveau.setCreance(creance);
            nouveau.setDepartement(creance.getDepartement());
            return nouveau;
        });
        document.setNomFichier(nomPropre(nomFichier, numFacture));
        document.setTaille(pdf.length);
        document.setSha256(sha256(pdf));
        document.setContenu(pdf);
        document.setDateAjout(LocalDateTime.now());
        CreanceDocument enregistre = documentRepository.save(document);
        return new CreanceDocumentInfoDTO(enregistre.getNomFichier(), enregistre.getTaille(), enregistre.getDateAjout());
    }

    public CreanceDocumentInfoDTO info(String numFacture) {
        Creance creance = creanceService.chargerAccessible(numFacture);
        return documentRepository.findInfoByCreanceId(creance.getId())
                .orElseThrow(() -> new RessourceIntrouvableException("Aucune facture PDF pour cette créance"));
    }

    public Fichier telecharger(String numFacture) {
        Creance creance = creanceService.chargerAccessible(numFacture);
        CreanceDocument document = documentRepository.findByCreanceId(creance.getId())
                .orElseThrow(() -> new RessourceIntrouvableException("Aucune facture PDF pour cette créance"));
        return new Fichier(document.getNomFichier(), document.getContenu());
    }

    /** Nom affiche au telechargement : sans chemin ni caractere de controle, toujours en .pdf. */
    static String nomPropre(String nomFichier, String numFacture) {
        String nom = nomFichier == null ? "" : nomFichier.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").trim();
        if (nom.isEmpty()) {
            nom = "facture-" + numFacture.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        }
        if (!nom.toLowerCase().endsWith(".pdf")) {
            nom = nom + ".pdf";
        }
        return nom.length() > NOM_MAX ? nom.substring(nom.length() - NOM_MAX) : nom;
    }

    private static String sha256(byte[] donnees) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(donnees));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
