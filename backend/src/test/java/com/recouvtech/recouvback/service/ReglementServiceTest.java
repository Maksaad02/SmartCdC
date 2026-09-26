package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.ReglementRepository;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le cloisonnement par departement est assure par le filtre Hibernate (DepartementIsolationTest). Ces
 * tests couvrent le controle applicatif : perimetre (departement, portefeuille), heritage du
 * departement de la creance, et recalcul du montant encaisse.
 */
@ExtendWith(MockitoExtension.class)
class ReglementServiceTest {

    private static final String AGENT_A = "agent.a@cabinet.test";
    private static final String AGENT_B = "agent.b@cabinet.test";

    @Mock private ReglementRepository reglementRepository;
    @Mock private AgentResolver agentResolver;
    @Mock private CreanceRepository creanceRepository;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private ReglementService reglementService;

    private Departement casa;
    private Departement rabat;
    private Creance creanceA;
    private Creance creanceB;
    private Utilisateur agentA;
    private ReglementRequestDTO dto;

    @BeforeEach
    void setUp() {
        casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);
        rabat = new Departement("Rabat", "RABAT");
        rabat.setId(2L);

        agentA = new Utilisateur();
        agentA.setEmail(AGENT_A);
        agentA.setNom("Agent A");

        Utilisateur agentB = new Utilisateur();
        agentB.setEmail(AGENT_B);
        agentB.setNom("Agent B");

        creanceA = new Creance();
        creanceA.setNumFacture("FA-001");
        creanceA.setAgentRecouv(agentA);
        creanceA.setDepartement(casa);

        creanceB = new Creance();
        creanceB.setNumFacture("FB-001");
        creanceB.setAgentRecouv(agentB);
        creanceB.setDepartement(casa);

        dto = new ReglementRequestDTO();
        dto.setMontant(new BigDecimal("100.00"));
        dto.setStatut(StatutReglement.NON_EFFECTUE);
        dto.setAgentName("Agent A");
    }

    @Test
    void enregistrerUnPaiementSurLaCreanceDUnAutreAgentEstRefuse() {
        dto.setNumFacture("FB-001");
        when(creanceRepository.findByNumFacture("FB-001")).thenReturn(creanceB);
        when(currentUser.canAccess(1L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> reglementService.create(dto));

        verify(reglementRepository, never()).save(any());
    }

    @Test
    void enregistrerUnPaiementSurSaPropreCreanceFonctionne() {
        dto.setNumFacture("FA-001");
        when(creanceRepository.findByNumFacture("FA-001")).thenReturn(creanceA);
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(agentResolver.resolve("Agent A", 1L)).thenReturn(agentA);
        when(reglementRepository.save(any(Reglement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(reglementService.create(dto));

        // Le departement du reglement est herite de sa creance, jamais de la requete.
        verify(reglementRepository).save(org.mockito.ArgumentMatchers.argThat(r -> r.getDepartement() == casa));
    }

    // ------------------------------------------------------------ montant encaisse

    private Reglement reglement(long id, String montant, StatutReglement statut, Creance creance) {
        Reglement r = new Reglement();
        r.setId(id);
        r.setMontant(new BigDecimal(montant));
        r.setStatut(statut);
        r.setCreance(creance);
        r.setDepartement(creance.getDepartement());
        r.setAgentRecouv(agentA);
        return r;
    }

    /** Le montant encaisse est recalcule a partir des seuls reglements EFFECTUES. */
    @Test
    void marquerUnReglementEffectueRecalculeLeMontantEncaisseDeLaCreance() {
        Reglement bascule = reglement(7L, "100.00", StatutReglement.NON_EFFECTUE, creanceA);
        Reglement dejaEffectue = reglement(8L, "50.00", StatutReglement.EFFECTUE, creanceA);
        Reglement annule = reglement(9L, "999.00", StatutReglement.NON_EFFECTUE, creanceA);
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(bascule));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(reglementRepository.save(bascule)).thenReturn(bascule);
        when(reglementRepository.findByCreance(creanceA)).thenReturn(List.of(bascule, dejaEffectue, annule));

        reglementService.updateStatus(7L, StatutReglement.EFFECTUE);

        assertEquals(0, new BigDecimal("150.00").compareTo(creanceA.getMontantEncaisse()),
                "100 + 50 : le reglement non effectue de 999 ne compte pas");
        verify(creanceRepository).save(creanceA);
    }

    @Test
    void annulerUnReglementEffectueRetireSonMontantDeLEncaisse() {
        Reglement r = reglement(7L, "100.00", StatutReglement.EFFECTUE, creanceA);
        creanceA.setMontantEncaisse(new BigDecimal("100.00"));
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(r));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(reglementRepository.save(r)).thenReturn(r);
        when(reglementRepository.findByCreance(creanceA)).thenReturn(List.of(r));

        reglementService.updateStatus(7L, StatutReglement.NON_EFFECTUE);

        assertEquals(0, BigDecimal.ZERO.compareTo(creanceA.getMontantEncaisse()),
                "un paiement annule ne doit plus etre compte comme encaisse");
    }

    @Test
    void reglerDeNouveauLeMemeStatutNeRecalculeRien() {
        Reglement r = reglement(7L, "100.00", StatutReglement.NON_EFFECTUE, creanceA);
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(r));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(reglementRepository.save(r)).thenReturn(r);

        reglementService.updateStatus(7L, StatutReglement.NON_EFFECTUE);

        verify(creanceRepository, never()).save(any());
    }

    @Test
    void supprimerUnReglementEffectueRecalculeLEncaisse() {
        Reglement r = reglement(7L, "100.00", StatutReglement.EFFECTUE, creanceA);
        creanceA.setMontantEncaisse(new BigDecimal("100.00"));
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(r));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(reglementRepository.findByCreance(creanceA)).thenReturn(List.of());

        reglementService.delete(7L);

        verify(reglementRepository).deleteById(7L);
        assertEquals(0, BigDecimal.ZERO.compareTo(creanceA.getMontantEncaisse()));
    }

    @Test
    void supprimerLeReglementDUnAutreAgentEstRefuse() {
        Reglement r = reglement(7L, "100.00", StatutReglement.EFFECTUE, creanceB);
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(r));
        when(currentUser.canAccess(1L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> reglementService.delete(7L));
        verify(reglementRepository, never()).deleteById(any());
    }

    /**
     * Le reglement existant appartient bien a l'appelant, mais changer numFacture
     * le rattacherait a la creance d'un autre agent : la NOUVELLE creance doit
     * etre controlee elle aussi.
     */
    @Test
    void deplacerUnReglementVersLaCreanceDUnAutreAgentEstRefuse() {
        Reglement existant = new Reglement();
        existant.setId(7L);
        existant.setCreance(creanceA);
        existant.setDepartement(casa);
        existant.setAgentRecouv(agentA);
        existant.setStatut(StatutReglement.NON_EFFECTUE);

        dto.setNumFacture("FB-001");
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(existant));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(creanceRepository.findByNumFacture("FB-001")).thenReturn(creanceB);
        when(currentUser.canAccess(1L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> reglementService.update(7L, dto));

        verify(reglementRepository, never()).save(any());
    }

    /**
     * Un reglement reste dans le departement de sa creance (cle etrangere composite en base) : le
     * deplacer vers la creance d'un AUTRE departement est refuse, meme pour un ADMIN.
     */
    @Test
    void deplacerUnReglementVersUneCreanceDUnAutreDepartementEstRefuse() {
        Reglement existant = reglement(7L, "100.00", StatutReglement.NON_EFFECTUE, creanceA);
        Creance creanceRabat = new Creance();
        creanceRabat.setNumFacture("FR-001");
        creanceRabat.setAgentRecouv(agentA);
        creanceRabat.setDepartement(rabat);

        dto.setNumFacture("FR-001");
        when(reglementRepository.findById(7L)).thenReturn(Optional.of(existant));
        when(creanceRepository.findByNumFacture("FR-001")).thenReturn(creanceRabat);
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(currentUser.canAccess(2L, AGENT_A)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> reglementService.update(7L, dto));
        verify(reglementRepository, never()).save(any());
    }
}
