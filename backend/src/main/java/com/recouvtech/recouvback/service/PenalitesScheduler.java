package com.recouvtech.recouvback.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcule chaque nuit les penalites et le statut de toutes les creances de l'entreprise,
 * tous departements confondus (tache systeme : aucun filtre de departement).
 *
 * Retard et penalites dependent de la date du jour, mais n'etaient recalcules qu'a l'ECRITURE
 * (ou a la demande d'un administrateur) : le statut stocke en base, sur lequel portent le
 * filtre par statut des listes et les statistiques du tableau de bord, prenait du retard sur la
 * realite (une creance passee en retard hier restait "IMPAYEE" en base). Cette tache le remet
 * d'aplomb.
 *
 * Instance unique : si le backend est un jour replique, il faudra un verrou (ShedLock) pour
 * qu'une seule instance execute la tache.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PenalitesScheduler {

    private final CreanceService creanceService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${app.penalties.cron:0 30 1 * * *}", zone = "${app.penalties.zone:Africa/Casablanca}")
    public void recalculerChaqueNuit() {
        int echecs = 0;
        try {
            creanceService.recalculerToutesPenalites();
        } catch (Exception e) {
            echecs++;
            log.error("Recalcul nocturne des pénalités impossible", e);
        }
        meterRegistry.counter("smartcdc.penalties.nightly.failures").increment(echecs);
        log.info("Recalcul nocturne des pénalités terminé ({})", echecs == 0 ? "succès" : "échec");
    }
}
