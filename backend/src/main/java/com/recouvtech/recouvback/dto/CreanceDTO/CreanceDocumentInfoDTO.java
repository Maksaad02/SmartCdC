package com.recouvtech.recouvback.dto.CreanceDTO;

import java.time.LocalDateTime;

/** Metadonnees de la facture PDF jointe a une creance (sans son contenu). */
public record CreanceDocumentInfoDTO(String nomFichier, long taille, LocalDateTime dateAjout) {
}
