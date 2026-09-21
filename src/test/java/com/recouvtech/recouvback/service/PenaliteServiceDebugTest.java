package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;

class PenaliteServiceDebugTest {

    @Test
    void debugPenaltyCalculation() {
        PenaliteService penaliteService = new PenaliteService();
        
        // Create a creance similar to the one in Postman (91 days overdue, 300,000 DH)
        Creance creance = new Creance();
        creance.setId(3L);
        creance.setNumFacture("F2025-603");
        creance.setEcheance(LocalDate.now().minusDays(91)); // 91 days ago
        creance.setMontantFacture(BigDecimal.valueOf(300000.0));
        creance.setMontantEncaisse(BigDecimal.valueOf(0.0));
        
        // Test the calculation
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        System.out.println("Days overdue: " + joursRetard);
        System.out.println("Penalties calculated: " + penalites);
        System.out.println("Expected penalties for 300,000 DH with 91 days: " + (300000.0 * 0.0085 * 1));
        
        // Should be 91 days overdue
        assertEquals(91, joursRetard);
        
        // Should have penalties: 300,000 * 0.0085 * 1 = 2,550 DH
        assertEquals(0, BigDecimal.valueOf(2550.0).compareTo(penalites));
    }
    
    @Test
    void debugPenaltyCalculationWithCurrentDate() {
        PenaliteService penaliteService = new PenaliteService();
        
        // Test with the exact date from Postman: echeance = "2025-06-21"
        Creance creance = new Creance();
        creance.setId(3L);
        creance.setNumFacture("F2025-603");
        creance.setEcheance(LocalDate.of(2025, 6, 21)); // Future date!
        creance.setMontantFacture(BigDecimal.valueOf(300000.0));
        creance.setMontantEncaisse(BigDecimal.valueOf(0.0));
        
        // Test the calculation
        int joursRetard = penaliteService.calculerJoursRetard(creance);
        BigDecimal penalites = penaliteService.calculerPenalites(creance);
        
        System.out.println("Due date: " + creance.getEcheance());
        System.out.println("Current date: " + LocalDate.now());
        System.out.println("Days overdue: " + joursRetard);
        System.out.println("Penalties calculated: " + penalites);
        
        // If the due date is in the future, there should be no penalties
        if (LocalDate.now().isBefore(creance.getEcheance())) {
            assertEquals(0, joursRetard);
            assertEquals(0, BigDecimal.valueOf(0.0).compareTo(penalites));
        }
    }
}
