package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.recouvtech.recouvback.event.RelanceCreeeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelanceAutomatiqueService {

    private final RelanceService relanceService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Cree les trois relances d'une nouvelle creance. Appele DANS la transaction de
     * creation de la creance : rien n'est envoye ici. L'e-mail de la relance
     * immediate part apres le commit (RelanceEmailListener), donc jamais pour une
     * creance finalement annulee.
     */
    public void creerRelancesAutomatiques(Creance creance, Utilisateur agent) {
        // 1. Relance immédiate : créée maintenant, envoyée après validation de la créance
        Relance immediate = creerRelanceImmediate(creance, agent);

        // 2. Relance jour 30 (créée avec date programmée, en attente d'envoi manuel)
        creerRelanceJour30(creance, agent);

        // 3. Relance jour 60 (créée avec date programmée, en attente d'envoi manuel)
        creerRelanceJour60(creance, agent);

        eventPublisher.publishEvent(new RelanceCreeeEvent(immediate.getId()));
    }

    private Relance creerRelanceImmediate(Creance creance, Utilisateur agent) {
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setDepartement(creance.getDepartement());
        relance.setAgentRecouv(agent);
        relance.setTypeRelance(TypeRelance.EMAIL);
        // EN_ATTENTE tant que l'e-mail n'est pas parti : ENVOYEE etait auparavant
        // affiche avant meme la tentative d'envoi, meme si elle echouait ensuite.
        relance.setStatutRelance(StatutRelance.EN_ATTENTE);
        relance.setDateRelance(LocalDate.now());
        relance.setDateCreation(LocalDateTime.now());
        relance.setMessage("Votre facture N°" + creance.getNumFacture() +
                          " d'un montant de " + creance.getMontantFacture() +
                          " MAD est due le " + creance.getEcheance() +
                          ". Veuillez procéder au règlement dans les délais.");

        return relanceService.save(relance);
    }

    private void creerRelanceJour30(Creance creance, Utilisateur agent) {
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setDepartement(creance.getDepartement());
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
        relance.setDepartement(creance.getDepartement());
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
}
