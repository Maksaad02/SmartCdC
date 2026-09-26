package com.recouvtech.recouvback.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PenalitesSchedulerTest {

    @Test
    void leRecalculNocturneCouvreToutesLesCreancesEnUnSeulPassage() {
        CreanceService creanceService = mock(CreanceService.class);

        new PenalitesScheduler(creanceService, new SimpleMeterRegistry()).recalculerChaqueNuit();

        // Tache systeme : aucun utilisateur authentifie, donc aucun filtre de departement,
        // tous les departements sont traites d'un coup.
        verify(creanceService).recalculerToutesPenalites();
    }

    @Test
    void unEchecEstComptabiliseSansFairePlanterLaTache() {
        CreanceService creanceService = mock(CreanceService.class);
        doThrow(new RuntimeException("base indisponible")).when(creanceService).recalculerToutesPenalites();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();

        new PenalitesScheduler(creanceService, metrics).recalculerChaqueNuit();

        assertEquals(1.0, metrics.counter("smartcdc.penalties.nightly.failures").count());
    }
}
