package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RelanceRepository;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Les etapes en base de l'envoi d'une relance : preparer le courriel, puis noter
 * le resultat.
 *
 * L'envoi SMTP lui-meme n'est JAMAIS fait ici : il a lieu entre les deux, hors de
 * toute transaction. Un e-mail parti ne peut pas etre annule par un rollback, et
 * une connexion SMTP lente ne doit pas garder une connexion MySQL du pool.
 * Chaque methode est une transaction courte et independante.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelanceEnvoiService {

    /** Colonne relance.commentaire : VARCHAR(255). */
    private static final int COMMENTAIRE_MAX = 250;

    private final RelanceRepository relanceRepository;
    private final CurrentUser currentUser;

    /** Courriel pret a partir : destinataire, sujet et corps deja resolus. */
    public record Courriel(Long relanceId, String destinataire, String sujet, String corps) {}

    /** Soit un courriel a envoyer, soit un refus a signaler a l'appelant. */
    public record Preparation(Courriel courriel, String refus) {
        static Preparation envoyer(Courriel c) { return new Preparation(c, null); }
        static Preparation refuser(String motif) { return new Preparation(null, motif); }
    }

    /**
     * Envoi manuel demandé par un agent : verifie ses droits sur la relance et
     * l'etat de celle-ci. Les refus "metier" sont renvoyes (et non leves) pour
     * que l'annulation ou l'echec qu'ils entrainent soit bien enregistree.
     */
    @Transactional
    public Preparation preparerEnvoiManuel(Long relanceId) {
        Relance relance = relanceRepository.findById(relanceId)
                .orElseThrow(() -> new RessourceIntrouvableException("Relance non trouvée"));

        // Sans ce controle, un agent pouvait declencher un vrai e-mail de relance
        // vers les debiteurs d'un autre agent.
        String proprietaire = relance.getCreance().getAgentRecouv() != null
                ? relance.getCreance().getAgentRecouv().getEmail() : null;
        if (!currentUser.canAccess(relance.getDepartement().getId(), proprietaire)) {
            throw new AccessDeniedException("Cette relance n'appartient pas à votre périmètre");
        }

        if (relance.getStatutRelance() != StatutRelance.EN_ATTENTE) {
            throw new IllegalArgumentException("Cette relance ne peut pas être envoyée manuellement");
        }
        if (relance.getCreance().getStatut() == StatutCreance.PAYEE) {
            relance.setStatutRelance(StatutRelance.ANNULEE);
            relance.setCommentaire("Créance déjà payée - relance annulée");
            relanceRepository.save(relance);
            return Preparation.refuser("Impossible d'envoyer la relance : créance déjà payée");
        }
        return construire(relance, "Envoi manuel");
    }

    /**
     * Envoi automatique, declenche apres validation de la creance. Aucun
     * utilisateur n'est en cause : pas de controle d'acces. Renvoie null s'il n'y
     * a rien a envoyer.
     */
    @Transactional
    public Courriel preparerEnvoiAutomatique(Long relanceId) {
        Relance relance = relanceRepository.findById(relanceId).orElse(null);
        if (relance == null || relance.getStatutRelance() != StatutRelance.EN_ATTENTE) {
            return null;
        }
        Preparation preparation = construire(relance, "Envoi automatique");
        return preparation.courriel();
    }

    private Preparation construire(Relance relance, String origine) {
        var creance = relance.getCreance();
        String destinataire = creance.getClient() != null ? creance.getClient().getEmail() : null;
        if (destinataire == null || destinataire.isBlank()) {
            relance.setStatutRelance(StatutRelance.ECHEC);
            relance.setCommentaire(tronquer(origine + " impossible : e-mail du client non renseigné"));
            relanceRepository.save(relance);
            return Preparation.refuser("E-mail du client non disponible pour la facture " + creance.getNumFacture());
        }
        return Preparation.envoyer(new Courriel(
                relance.getId(),
                destinataire,
                "Relance - Facture N°" + creance.getNumFacture(),
                corps(relance)));
    }

    /**
     * Texte du courriel : le message de la relance, sinon son commentaire, sinon un rappel
     * standard. Une relance creee sans message partait auparavant avec un corps vide.
     */
    private static String corps(Relance relance) {
        if (relance.getMessage() != null && !relance.getMessage().isBlank()) {
            return relance.getMessage();
        }
        if (relance.getCommentaire() != null && !relance.getCommentaire().isBlank()) {
            return relance.getCommentaire();
        }
        return "Nous vous rappelons le règlement de la facture N°" + relance.getCreance().getNumFacture() + ".";
    }

    @Transactional
    public void marquerEnvoyee(Long relanceId, StatutRelance statut, String agent, String commentaire) {
        relanceRepository.findById(relanceId).ifPresent(relance -> {
            relance.setStatutRelance(statut);
            relance.setDateEnvoi(LocalDateTime.now());
            if (agent != null) {
                relance.setAgentEnvoi(agent);
            }
            if (commentaire != null) {
                relance.setCommentaire(tronquer(commentaire));
            }
            relanceRepository.save(relance);
        });
    }

    /**
     * ECHEC et non ANNULEE : une panne SMTP passagere ne doit pas faire disparaitre
     * la relance de la file, elle reste visible et a rejouer.
     */
    @Transactional
    public void marquerEchec(Long relanceId, String motif) {
        relanceRepository.findById(relanceId).ifPresent(relance -> {
            relance.setStatutRelance(StatutRelance.ECHEC);
            relance.setCommentaire(tronquer(motif));
            relanceRepository.save(relance);
        });
    }

    private static String tronquer(String texte) {
        if (texte == null) {
            return null;
        }
        return texte.length() <= COMMENTAIRE_MAX ? texte : texte.substring(0, COMMENTAIRE_MAX);
    }
}
