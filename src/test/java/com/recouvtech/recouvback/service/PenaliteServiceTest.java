package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class PenaliteServiceTest {

    private PenaliteService penaliteService;
    private Creance creance;
    private Client client;
    private Utilisateur agent;

    @BeforeEach
    void setUp() {
        penaliteService = new PenaliteService();
        
        // Create test client
        client = new Client();
        client.setId(1L);
        client.setRaisonSociale("Test Client");
        
        // Create test agent
        agent = new Utilisateur();
        agent.setIdAgentRecouv(1L);
        agent.setNom("Test Agent");
        
        // Create test creance
        creance = new Creance();
        creance.setId(1L);
        creance.setNumFacture("FACT-001");
        creance.setMontantFacture(BigDecimal.valueOf(10000.0));
        creance.setMontantEncaisse(BigDecimal.valueOf(0.0));
        creance.setClient(client);
        creance.setAgentRecouv(agent);
    }

    @Test
    void testCalculerJoursRetard_NoEcheance() {
        // Given: creance without echeance
        creance.setEcheance(null);
        
        // When: calculating days of delay
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        
        // Then: should return 0
        assertEquals(0, joursRetard);
    }

    @Test
    void testCalculerJoursRetard_NotYetDue() {
        // Given: creance with future echeance
        creance.setEcheance(LocalDate.now().plusDays(10));
        
        // When: calculating days of delay
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        
        // Then: should return 0
        assertEquals(0, joursRetard);
    }

    @Test
    void testCalculerJoursRetard_ExactlyDue() {
        // Given: creance due today
        creance.setEcheance(LocalDate.now());
        
        // When: calculating days of delay
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        
        // Then: should return 0
        assertEquals(0, joursRetard);
    }

    @Test
    void testCalculerJoursRetard_30DaysLate() {
        // Given: creance 30 days late
        creance.setEcheance(LocalDate.now().minusDays(30));
        
        // When: calculating days of delay
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        
        // Then: should return 30
        assertEquals(30, joursRetard);
    }

    @Test
    void testCalculerPenalites_NoEcheance() {
        // Given: creance without echeance
        creance.setEcheance(null);
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 0
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_30DaysLate() {
        // Given: creance 30 days late (below threshold)
        creance.setEcheance(LocalDate.now().minusDays(30));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 0 (below 60 days threshold)
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_60DaysLate() {
        // Given: creance exactly 60 days late
        creance.setEcheance(LocalDate.now().minusDays(60));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 0 (exactly at threshold, no complete months after)
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_90DaysLate() {
        // Given: creance 90 days late (1 complete month after 60 days)
        creance.setEcheance(LocalDate.now().minusDays(90));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 85 DH (10000 * 0.0085 * 1)
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_120DaysLate() {
        // Given: creance 120 days late (2 complete months after 60 days)
        creance.setEcheance(LocalDate.now().minusDays(120));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 170 DH (10000 * 0.0085 * 2)
        assertEquals(0, BigDecimal.valueOf(170.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_150DaysLate() {
        // Given: creance 150 days late (3 complete months after 60 days)
        creance.setEcheance(LocalDate.now().minusDays(150));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 255 DH (10000 * 0.0085 * 3)
        assertEquals(0, BigDecimal.valueOf(255.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_89DaysLate() {
        // Given: creance 89 days late (less than 1 complete month after 60 days)
        creance.setEcheance(LocalDate.now().minusDays(89));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 0 (29 days after threshold, not a complete month)
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_91DaysLate() {
        // Given: creance 91 days late (1 complete month + 1 day after 60 days)
        creance.setEcheance(LocalDate.now().minusDays(91));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 85 DH (10000 * 0.0085 * 1)
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_DifferentAmounts() {
        // Given: creance with different amounts
        creance.setMontantFacture(BigDecimal.valueOf(5000.0));
        creance.setEcheance(LocalDate.now().minusDays(90));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 42.5 DH (5000 * 0.0085 * 1)
        assertEquals(0, BigDecimal.valueOf(42.5).compareTo(penalites));
    }

    @Test
    void testCalculerPenalites_Rounding() {
        // Given: creance with amount that would result in decimal places
        creance.setMontantFacture(BigDecimal.valueOf(1000.0));
        creance.setEcheance(LocalDate.now().minusDays(90));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 8.5 DH (1000 * 0.0085 * 1) and be properly rounded
        assertEquals(0, BigDecimal.valueOf(8.5).compareTo(penalites));
    }

    @Test
    void testMettreAJourPenalites_FirstTime() {
        // Given: creance with no previous penalty calculation
        creance.setEcheance(LocalDate.now().minusDays(90));
        creance.setDateCalculPenalites(null);
        
        // When: updating penalties
        penaliteService.mettreAJourPenalites(creance);
        
        // Then: penalties should be calculated and date set
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(creance.getMontantPenalites()));
        assertEquals(LocalDate.now(), creance.getDateCalculPenalites());
    }

    @Test
    void testMettreAJourPenalites_AlreadyCalculatedToday() {
        // Given: creance already calculated today
        creance.setEcheance(LocalDate.now().minusDays(90));
        creance.setMontantPenalites(BigDecimal.valueOf(50.0));
        creance.setDateCalculPenalites(LocalDate.now());
        
        // When: updating penalties
        penaliteService.mettreAJourPenalites(creance);
        
        // Then: penalties should not be recalculated
        assertEquals(0, BigDecimal.valueOf(50.0).compareTo(creance.getMontantPenalites()));
    }

    @Test
    void testForcerRecalculPenalites() {
        // Given: creance with existing penalties
        creance.setEcheance(LocalDate.now().minusDays(90));
        creance.setMontantPenalites(BigDecimal.valueOf(50.0));
        creance.setDateCalculPenalites(LocalDate.now().minusDays(1));
        
        // When: forcing recalculation
        penaliteService.forcerRecalculPenalites(creance);
        
        // Then: penalties should be recalculated
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(creance.getMontantPenalites()));
        assertEquals(LocalDate.now(), creance.getDateCalculPenalites());
    }

    @Test
    void testEdgeCase_Exactly30DaysAfterThreshold() {
        // Given: creance exactly 30 days after the 60-day threshold (90 days total)
        creance.setEcheance(LocalDate.now().minusDays(90));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 1 month penalty
        assertEquals(0, BigDecimal.valueOf(85.0).compareTo(penalites));
    }

    @Test
    void testEdgeCase_29DaysAfterThreshold() {
        // Given: creance 29 days after the 60-day threshold (89 days total)
        creance.setEcheance(LocalDate.now().minusDays(89));
        
        // When: calculating penalties
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        // Then: should return 0 (not a complete month)
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
    }
}
