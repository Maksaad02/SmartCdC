package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.event.RelanceCreeeEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie l'e-mail d'une relance creee, une fois la creance reellement enregistree.
 *
 * Avant : l'envoi SMTP se faisait DANS la transaction de creation. Si celle-ci
 * etait annulee ensuite, le debiteur avait deja recu une relance pour une creance
 * qui n'existait pas, et la connexion MySQL restait prise pendant tout l'aller-
 * retour avec le serveur de messagerie. Ici :
 *  - AFTER_COMMIT : rien ne part si la transaction est annulee ;
 *  - @Async : la requete HTTP ne attend pas le SMTP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RelanceEmailListener {

    private final RelanceEnvoiService envoiService;
    private final EmailService emailService;
    private final MeterRegistry meterRegistry;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRelanceCreee(RelanceCreeeEvent event) {
        // Thread du pool, sans utilisateur authentifie : tache systeme, donc aucun filtre de departement.
        try {
            var courriel = envoiService.preparerEnvoiAutomatique(event.relanceId());
            if (courriel == null) {
                return;
            }
            try {
                emailService.envoyer(courriel.destinataire(), courriel.sujet(), courriel.corps());
                envoiService.marquerEnvoyee(event.relanceId(),
                        StatutRelance.ENVOYEE, null, null);
                meterRegistry.counter("smartcdc.emails", "result", "sent").increment();
                log.info("Relance {} envoyée automatiquement", event.relanceId());
            } catch (Exception e) {
                meterRegistry.counter("smartcdc.emails", "result", "failed").increment();
                log.error("Échec de l'envoi automatique de la relance {}", event.relanceId(), e);
                envoiService.marquerEchec(event.relanceId(), "Échec de l'envoi automatique : " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Traitement de la relance {} impossible", event.relanceId(), e);
        }
    }
}
