package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Determine le departement d'une ressource RACINE creee par l'appelant (un client). Les creances,
 * reglements et relances n'en choisissent jamais : ils heritent de leur parent.
 *
 * - ADMIN : choisit le departement (obligatoire), qui doit exister et etre actif.
 * - MANAGER / AGENT : le departement est celui de leur compte. Un departement different fourni dans la
 *   requete est refuse (403) plutot que ignore en silence : c'est une tentative de sortie de perimetre.
 */
@Component
@RequiredArgsConstructor
public class DepartementScope {

    private final DepartementRepository departementRepository;
    private final CurrentUser currentUser;

    public Departement pourCreation(Long departementDemande) {
        if (currentUser.isAdmin()) {
            if (departementDemande == null) {
                throw new IllegalArgumentException("Le département est obligatoire");
            }
            Departement departement = departementRepository.findById(departementDemande)
                    .orElseThrow(() -> new IllegalArgumentException("Département introuvable"));
            if (!departement.isActif()) {
                throw new IllegalArgumentException("Ce département est désactivé");
            }
            return departement;
        }
        Long mien = currentUser.departementId();
        if (mien == null) {
            throw new AccessDeniedException("Aucun département n'est rattaché à votre compte");
        }
        if (departementDemande != null && !departementDemande.equals(mien)) {
            throw new AccessDeniedException("Vous ne pouvez agir que dans votre département");
        }
        return departementRepository.findById(mien)
                .orElseThrow(() -> new AccessDeniedException("Département introuvable"));
    }
}
