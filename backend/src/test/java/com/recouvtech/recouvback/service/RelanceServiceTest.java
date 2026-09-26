package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.RelanceRepository;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceRequestDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import com.recouvtech.recouvback.service.RelanceEnvoiService.Courriel;
import com.recouvtech.recouvback.service.RelanceEnvoiService.Preparation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelanceServiceTest {

    private static final String AGENT_B = "agent.b@cabinet.test";

    @Mock private RelanceRepository relanceRepository;
    @Mock private CreanceRepository creanceRepository;
    @Mock private AgentResolver agentResolver;
    @Mock private RelanceEnvoiService envoiService;
    @Mock private EmailService emailService;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private RelanceService relanceService;

    private Creance creanceB;

    @BeforeEach
    void setUp() {
        Utilisateur agentB = new Utilisateur();
        agentB.setEmail(AGENT_B);
        agentB.setNom("Agent B");

        creanceB = new Creance();
        creanceB.setNumFacture("FB-001");
        creanceB.setAgentRecouv(agentB);
        Departement casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);
        creanceB.setDepartement(casa);
    }

    private RelanceRequestDTO dtoPour(String numFacture) {
        RelanceRequestDTO dto = new RelanceRequestDTO();
        dto.setNumFacture(numFacture);
        dto.setAgentName("Agent A");
        return dto;
    }

    @Test
    void creerUneRelanceSurLaCreanceDUnAutreAgentEstRefuse() {
        when(creanceRepository.findByNumFacture("FB-001")).thenReturn(creanceB);
        when(currentUser.canAccess(1L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> relanceService.create(dtoPour("FB-001")));

        verify(relanceRepository, never()).save(any());
    }

    @Test
    void creerUneRelanceSurUneCreanceInconnueRenvoieIntrouvableEtNonUneNpe() {
        when(creanceRepository.findByNumFacture("INCONNUE")).thenReturn(null);

        assertThrows(RessourceIntrouvableException.class,
                () -> relanceService.create(dtoPour("INCONNUE")));
    }

    @Test
    void unRefusDePreparationEstRenvoyeAlAppelantSansEnvoyerDEmail() {
        when(envoiService.preparerEnvoiManuel(5L))
                .thenReturn(new Preparation(null, "Impossible d'envoyer la relance : créance déjà payée"));

        assertThrows(IllegalArgumentException.class,
                () -> relanceService.envoyerRelanceManuellement(5L, "agent"));

        verify(emailService, never()).envoyer(anyString(), anyString(), anyString());
    }

    @Test
    void unEchecSmtpMarqueLaRelanceEnEchecEtNonAnnulee() {
        Courriel courriel = new Courriel(5L, "client@debiteur.test", "Relance", "Bonjour");
        when(envoiService.preparerEnvoiManuel(5L)).thenReturn(new Preparation(courriel, null));
        doThrow(new RuntimeException("SMTP indisponible"))
                .when(emailService).envoyer("client@debiteur.test", "Relance", "Bonjour");

        boolean envoyee = relanceService.envoyerRelanceManuellement(5L, "agent");

        assertFalse(envoyee);
        verify(envoiService).marquerEchec(eq(5L), anyString());
        verify(envoiService, never()).marquerEnvoyee(any(), any(), any(), any());
    }

    @Test
    void unEnvoiReussiMarqueLaRelanceEffectuee() {
        Courriel courriel = new Courriel(5L, "client@debiteur.test", "Relance", "Bonjour");
        when(envoiService.preparerEnvoiManuel(5L)).thenReturn(new Preparation(courriel, null));

        boolean envoyee = relanceService.envoyerRelanceManuellement(5L, "agent");

        assertTrue(envoyee);
        verify(envoiService).marquerEnvoyee(eq(5L), eq(StatutRelance.EFFECTUEE), eq("agent"), anyString());
    }
}
