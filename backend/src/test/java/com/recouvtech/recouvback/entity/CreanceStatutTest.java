package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.StatutCreance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression sur le calcul de statut et de solde d'une creance.
 */
class CreanceStatutTest {

    private Creance creance(String facture, String penalites, String encaisse, int joursRetard) {
        Creance c = new Creance();
        c.setNumFacture("F-TEST");
        c.setMontantFacture(new BigDecimal(facture));
        c.setMontantPenalites(new BigDecimal(penalites));
        c.setEcheance(joursRetard > 0 ? LocalDate.now().minusDays(joursRetard) : LocalDate.now().plusDays(30));
        c.setMontantEncaisse(new BigDecimal(encaisse));
        return c;
    }

    @Test
    @DisplayName("Principal solde mais penalites dues : la creance n'est PAS payee")
    void penalitesRestantDuesNeMarquentPasPayee() {
        // Le test comparait montantEncaisse au seul montantFacture : la creance
        // passait PAYEE alors que 85 DH de penalites restaient dus.
        Creance c = creance("10000", "85", "10000", 90);

        assertNotEquals(StatutCreance.PAYEE, c.getStatut());
        assertEquals(0, new BigDecimal("85").compareTo(c.getSolde()));
    }

    @Test
    @DisplayName("Dette totale reglee, penalites incluses : la creance est payee")
    void detteTotaleRegleeMarquePayee() {
        Creance c = creance("10000", "85", "10085", 90);

        assertEquals(StatutCreance.PAYEE, c.getStatut());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getSolde()));
    }

    @Test
    @DisplayName("Paiement partiel et retard > 60 jours : la creance devient PENALISEE")
    void paiementPartielEnRetardBasculePenalisee() {
        // Un paiement partiel figeait le statut a PARTIELLEMENT_PAYEE
        // indefiniment, meme tres au-dela du seuil de penalisation.
        Creance c = creance("10000", "0", "2000", 120);

        assertEquals(StatutCreance.PENALISEE, c.getStatut());
    }

    @Test
    @DisplayName("Paiement partiel sans retard : PARTIELLEMENT_PAYEE")
    void paiementPartielSansRetard() {
        Creance c = creance("10000", "0", "2000", 0);

        assertEquals(StatutCreance.PARTIELLEMENT_PAYEE, c.getStatut());
    }

    @Test
    @DisplayName("Aucun montant renseigne : solde neutre, pas de NullPointerException")
    void montantsNulsNeCassentPasLeSolde() {
        Creance c = new Creance();
        c.setNumFacture("F-NULL");

        assertDoesNotThrow(c::getSolde);
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getSolde()));
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getMontantTotal()));
    }
}
