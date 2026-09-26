package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Determine l'agent (responsable de dossier) d'une ressource creee ou modifiee.
 *
 * Regle unique, partagee par les services Client, Creance, Reglement et Relance :
 * - un AGENT est toujours force a lui-meme (sinon il pourrait agir au nom d'un autre, ou attribuer
 *   ses dossiers a autrui via un champ fourni par le client) ;
 * - un ADMIN ou un MANAGER peut designer un agent par son nom, mais UNIQUEMENT parmi les utilisateurs
 *   du departement de la ressource : jamais un agent d'un autre departement.
 */
@Component
@RequiredArgsConstructor
public class AgentResolver {

    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUser currentUser;

    public Utilisateur resolve(String requestedName, Long departementId) {
        Utilisateur moi = utilisateurRepository.findByEmail(currentUser.email())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur courant introuvable"));
        if (requestedName == null || requestedName.isBlank()
                || !currentUser.isManagerOrAbove()
                || requestedName.equals(moi.getNom())) {
            return moi;
        }
        Utilisateur designe = utilisateurRepository
                .findFirstByNomAndDepartement_IdOrderByIdAgentRecouv(requestedName, departementId);
        if (designe == null) {
            throw new IllegalArgumentException(
                    "Aucun agent trouvé dans ce département avec le nom : " + requestedName);
        }
        return designe;
    }
}
