package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceStatsDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.DashboardDepartementsDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreanceServiceTest {

    private static final String AGENT = "agent.a@cabinet.test";

    @Mock private CreanceRepository creanceRepository;
    @Mock private AgentResolver agentResolver;
    @Mock private ClientRepository clientRepository;
    @Mock private PenaliteService penaliteService;
    @Mock private RelanceAutomatiqueService relanceAutomatiqueService;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private CreanceService creanceService;

    private Departement casa;
    private Utilisateur agent;
    private Client client;
    private CreanceRequestDTO dto;

    @BeforeEach
    void setUp() {
        agent = new Utilisateur();
        agent.setEmail(AGENT);
        agent.setNom("Agent A");

        casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);

        client = new Client();
        client.setRaisonSociale("Acme SARL");
        client.setDepartement(casa);

        dto = new CreanceRequestDTO();
        dto.setNumFacture("F-001");
        dto.setEcheance(LocalDate.now().plusDays(30));
        dto.setMontantFacture(new BigDecimal("1000.00"));
        dto.setClientName("Acme SARL");
    }

    // ------------------------------------------------------------ creation

    @Test
    void creerUneCreanceEnregistreLaCreanceEtSesRelancesAutomatiques() {
        when(creanceRepository.existsByNumFacture("F-001")).thenReturn(false);
        when(clientRepository.findByRaisonSociale("Acme SARL")).thenReturn(client);
        when(agentResolver.resolve(null, 1L)).thenReturn(agent);
        when(creanceRepository.save(any(Creance.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = creanceService.createCreance(dto);

        assertEquals("F-001", result.getNumFacture());
        ArgumentCaptor<Creance> saved = ArgumentCaptor.forClass(Creance.class);
        verify(creanceRepository).save(saved.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getValue().getMontantEncaisse()),
                "une creance neuve n'a rien encaisse, quoi que dise la requete");
        assertSame(casa, saved.getValue().getDepartement(), "le departement d'une creance est celui de son client");
        verify(relanceAutomatiqueService).creerRelancesAutomatiques(any(Creance.class), eq(agent));
    }

    @Test
    void uneCreanceSansEcheanceEstRefusee() {
        dto.setEcheance(null);

        assertThrows(IllegalArgumentException.class, () -> creanceService.createCreance(dto));
        verify(creanceRepository, never()).save(any());
    }

    @Test
    void unNumeroDeFactureDejaUtiliseEstRefuse() {
        when(creanceRepository.existsByNumFacture("F-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> creanceService.createCreance(dto));
        verify(creanceRepository, never()).save(any());
    }

    @Test
    void unClientInconnuEstRefuse() {
        when(creanceRepository.existsByNumFacture("F-001")).thenReturn(false);
        when(clientRepository.findByRaisonSociale("Acme SARL")).thenReturn(null);

        assertThrows(RessourceIntrouvableException.class, () -> creanceService.createCreance(dto));
        verify(relanceAutomatiqueService, never()).creerRelancesAutomatiques(any(), any());
    }

    // ------------------------------------------------------------ lecture et droits

    @Test
    void uneCreanceInconnueEstIntrouvable() {
        when(creanceRepository.findByNumFacture("X")).thenReturn(null);

        assertThrows(RessourceIntrouvableException.class, () -> creanceService.getByNumFacture("X"));
    }

    @Test
    void uneCreanceDUnAutreAgentEstRefusee() {
        Creance autre = creanceDe("autre.agent@cabinet.test");
        when(creanceRepository.findByNumFacture("F-002")).thenReturn(autre);
        when(currentUser.canAccess(1L, "autre.agent@cabinet.test")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> creanceService.getByNumFacture("F-002"));
    }

    @Test
    void modifierUneCreanceDUnAutreAgentEstRefuse() {
        Creance autre = creanceDe("autre.agent@cabinet.test");
        when(creanceRepository.findByNumFacture("F-002")).thenReturn(autre);
        when(currentUser.canAccess(1L, "autre.agent@cabinet.test")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> creanceService.updateCreance("F-002", dto));
        verify(creanceRepository, never()).save(any());
    }

    @Test
    void supprimerUneCreanceLaMarqueSupprimeeSansLaDetruire() {
        Creance c = creanceDe(AGENT);
        when(creanceRepository.findByNumFacture("F-001")).thenReturn(c);
        when(currentUser.canAccess(1L, AGENT)).thenReturn(true);

        creanceService.deleteCreance("F-001");

        assertTrue(c.isSupprimee(), "suppression logique : l'historique des reglements est preserve");
        verify(creanceRepository).save(c);
        verify(creanceRepository, never()).delete(any(Creance.class));
    }

    // ------------------------------------------------------------ API chatbot : reponses bornees

    @Test
    void lesImpayesDuChatbotSontBornesA200MemeSiLOnEnDemandeBeaucoup() {
        when(currentUser.isAgent()).thenReturn(true);
        when(currentUser.email()).thenReturn(AGENT);
        when(creanceRepository.findUnpaidByAgent(eq(AGENT), any(Pageable.class))).thenReturn(List.of());

        creanceService.getAllUnpaidCreances(50_000);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(creanceRepository).findUnpaidByAgent(eq(AGENT), page.capture());
        assertEquals(200, page.getValue().getPageSize());
    }

    @Test
    void sansLimiteLesImpayesSontBornesA50EtUnNonAgentNEstPasLimiteAUnPortefeuille() {
        when(currentUser.isAgent()).thenReturn(false);
        when(creanceRepository.findUnpaid(any(Pageable.class))).thenReturn(List.of());

        creanceService.getAllUnpaidCreances(null);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(creanceRepository).findUnpaid(page.capture());
        assertEquals(50, page.getValue().getPageSize());
        verify(creanceRepository, never()).findUnpaidByAgent(any(), any());
    }

    // ------------------------------------------------------------ tableau de bord

    @Test
    void lesStatistiquesAgregentLesLignesRenvoyeesParLaBase() {
        when(currentUser.isAgent()).thenReturn(false);
        when(creanceRepository.statsByStatut()).thenReturn(List.of(
                new Object[]{StatutCreance.PAYEE, 2L, new BigDecimal("300.00"), new BigDecimal("300.00"), BigDecimal.ZERO},
                new Object[]{StatutCreance.EN_RETARD, 3L, new BigDecimal("700.00"), new BigDecimal("50.00"), new BigDecimal("50.00")}));

        CreanceStatsDTO stats = creanceService.stats();

        assertEquals(5, stats.totalCreances());
        assertEquals(2L, stats.parStatut().get(StatutCreance.PAYEE));
        assertEquals(0L, stats.parStatut().get(StatutCreance.IMPAYEE), "un statut absent vaut 0, pas null");
        assertEquals(0, new BigDecimal("1000.00").compareTo(stats.montantTotal()));
        assertEquals(0, new BigDecimal("350.00").compareTo(stats.montantEncaisse()));
        assertEquals(0, new BigDecimal("50.00").compareTo(stats.montantPenalites()));
        // 350 encaisses sur (1000 factures + 50 penalites) = 33,33 %
        assertEquals(0, new BigDecimal("33.33").compareTo(stats.tauxRecouvrement()));
    }

    @Test
    void leTauxDeRecouvrementDUnePeriodeSansCreanceEstNul() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                CreanceService.tauxRecouvrement(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    /** Une requete agregee ; le global est la somme des departements, donc toujours coherent avec eux. */
    @Test
    void leComparatifDesDepartementsAgregeLesLignesEtCalculeLeGlobal() {
        when(creanceRepository.statsByDepartement()).thenReturn(List.of(
                new Object[]{1L, "Casablanca", 4L, new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("550.00"), 1L},
                new Object[]{2L, "Rabat", 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L}));

        DashboardDepartementsDTO dash = creanceService.statsParDepartement();

        assertEquals(2, dash.departements().size());
        var casaStats = dash.departements().get(0);
        assertEquals(0, new BigDecimal("50.00").compareTo(casaStats.tauxRecouvrement()), "550 / 1100");
        assertEquals(0, new BigDecimal("550.00").compareTo(casaStats.solde()), "solde = 1100 - 550");
        assertEquals(0, BigDecimal.ZERO.compareTo(dash.departements().get(1).tauxRecouvrement()),
                "un departement sans creance apparait, avec un taux nul");
        assertEquals(4, dash.global().nbCreances());
        assertEquals(1, dash.global().nbEnRetard());
        assertEquals(0, new BigDecimal("50.00").compareTo(dash.global().tauxRecouvrement()));
    }

    private Creance creanceDe(String emailAgent) {
        Utilisateur proprietaire = new Utilisateur();
        proprietaire.setEmail(emailAgent);
        Creance c = new Creance();
        c.setNumFacture("F-002");
        c.setAgentRecouv(proprietaire);
        c.setClient(client);
        c.setDepartement(casa);
        return c;
    }
}
