package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelanceAutomatiqueService {

    @Autowired
    private RelanceService relanceService;

    @Autowired
    private EmailService emailService;

    public void creerRelancesAutomatiques(Creance creance, Utilisateur agent) {
        // 1. Relance immédiate (créée et envoyée automatiquement)
        creerEtEnvoyerRelanceImmediate(creance, agent);

        // 2. Relance jour 30 (créée avec date programmée, en attente d'envoi manuel)
        creerRelanceJour30(creance, agent);

        // 3. Relance jour 60 (créée avec date programmée, en attente d'envoi manuel)
        creerRelanceJour60(creance, agent);
    }

    private void creerEtEnvoyerRelanceImmediate(Creance creance, Utilisateur agent) {
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setTypeRelance(TypeRelance.EMAIL);
        relance.setStatutRelance(StatutRelance.ENVOYEE); // Envoyée immédiatement
        relance.setDateRelance(LocalDate.now()); // Date d'envoi = aujourd'hui
        relance.setDateCreation(LocalDateTime.now());
        relance.setDateEnvoi(LocalDateTime.now()); // Envoyée maintenant
        relance.setMessage("Votre facture N°" + creance.getNumFacture() +
                          " d'un montant de " + creance.getMontantFacture() +
                          " MAD est due le " + creance.getEcheance() +
                          ". Veuillez procéder au règlement dans les délais.");

        relanceService.save(relance);

        // Envoi automatique immédiat
        envoyerRelanceImmediate(relance);
    }

    private void creerRelanceJour30(Creance creance, Utilisateur agent) {
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setTypeRelance(TypeRelance.EMAIL);
        relance.setStatutRelance(StatutRelance.EN_ATTENTE); // Créée, en attente d'envoi manuel
        relance.setDateRelance(creance.getEcheance().plusDays(30)); // Date programmée = 30 jours après échéance
        relance.setDateCreation(LocalDateTime.now()); // Date de création = maintenant
        relance.setDateProgrammee(LocalDateTime.now().plusDays(30)); // Date suggérée pour envoi
        relance.setMessage("RAPPEL IMPORTANT : Votre facture N°" + creance.getNumFacture() +
                          " est en retard depuis 30 jours (échéance : " + creance.getEcheance() + "). " +
                          "Évitez les pénalités en réglant rapidement. " +
                          "Montant à régler : " + creance.getMontantFacture() + " MAD.");

        relanceService.save(relance);
    }

    private void creerRelanceJour60(Creance creance, Utilisateur agent) {
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setTypeRelance(TypeRelance.EMAIL);
        relance.setStatutRelance(StatutRelance.EN_ATTENTE); // Créée, en attente d'envoi manuel
        relance.setDateRelance(creance.getEcheance().plusDays(60)); // Date programmée = 60 jours après échéance
        relance.setDateCreation(LocalDateTime.now()); // Date de création = maintenant
        relance.setDateProgrammee(LocalDateTime.now().plusDays(60)); // Date suggérée pour envoi
        // Le taux provient de PenaliteService : la valeur 0.83% ecrite en dur ici
        // ne correspondait pas au 0.85% reellement facture, et le message annoncait
        // des penalites deja appliquees alors qu'elles restent nulles jusqu'a J+90.
        relance.setMessage("ATTENTION - ÉCHÉANCE DÉPASSÉE DE 60 JOURS : Votre facture N°" + creance.getNumFacture() +
                          " est en retard depuis 60 jours. " +
                          "À compter du 90e jour de retard, des pénalités de "
                          + PenaliteService.tauxMensuelPourcent() +
                          "% par mois seront appliquées sur le montant initial (" +
                          creance.getMontantFacture() + " MAD). " +
                          "Veuillez régulariser votre situation rapidement pour les éviter.");

        relanceService.save(relance);
    }

    private void envoyerRelanceImmediate(Relance relance) {
        try {
            emailService.envoyerRelance(relance);
            log.info("Relance immédiate envoyée automatiquement pour la créance: " +
                    relance.getCreance().getNumFacture());
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi automatique de la relance: " + e.getMessage());
            relance.setStatutRelance(StatutRelance.ECHEC);
            relance.setCommentaire("Échec de l'envoi automatique: " + e.getMessage());
            relanceService.save(relance);
        }
    }
}
