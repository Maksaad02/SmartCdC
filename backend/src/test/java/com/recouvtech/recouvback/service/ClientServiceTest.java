package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regles d'acces d'un client. Le choix du departement et de l'agent est delegue a DepartementScope
 * et AgentResolver (testes separement) ; le cloisonnement SQL est verifie par DepartementIsolationTest.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final String AGENT_A = "agent.a@entreprise.test";
    private static final String AGENT_B = "agent.b@entreprise.test";

    @Mock private ClientRepository clientRepository;
    @Mock private AgentResolver agentResolver;
    @Mock private DepartementScope departementScope;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private ClientService clientService;

    private Departement casa;
    private Departement rabat;
    private Utilisateur agentA;
    private Utilisateur agentB;
    private ClientRequestDTO dto;

    @BeforeEach
    void setUp() {
        casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);
        rabat = new Departement("Rabat", "RABAT");
        rabat.setId(2L);

        agentA = new Utilisateur();
        agentA.setEmail(AGENT_A);
        agentA.setNom("Agent A");
        agentB = new Utilisateur();
        agentB.setEmail(AGENT_B);
        agentB.setNom("Agent B");

        dto = new ClientRequestDTO();
        dto.setRaisonSociale("Acme SARL");
        dto.setEmail("contact@acme.test");
        dto.setTelephone("0522000000");
        dto.setAdresse("1 rue Test");
    }

    @Test
    void laCreationPrendLeDepartementEtLAgentResolusParLesComposantsDedies() {
        dto.setDepartementId(1L);
        dto.setAgentName("Agent A");
        when(departementScope.pourCreation(1L)).thenReturn(casa);
        when(agentResolver.resolve("Agent A", 1L)).thenReturn(agentA);
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        clientService.createClient(dto);

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertEquals(1L, saved.getValue().getDepartement().getId());
        assertEquals(AGENT_A, saved.getValue().getAgentRecouv().getEmail());
    }

    @Test
    void lAgentEstResoluDansLeDepartementDuClientEtPasAilleurs() {
        dto.setAgentName("Agent B");
        when(departementScope.pourCreation(null)).thenReturn(casa);
        when(agentResolver.resolve("Agent B", 1L))
                .thenThrow(new IllegalArgumentException("Aucun agent trouvé dans ce département"));

        assertThrows(IllegalArgumentException.class, () -> clientService.createClient(dto));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void lireLeClientDUnAutrePerimetreEstRefuse() {
        Client client = clientDe(agentB, rabat);
        when(clientRepository.findById(9L)).thenReturn(Optional.of(client));
        when(currentUser.canAccess(2L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> clientService.getClientById(9L));
    }

    @Test
    void unClientInexistantEstIntrouvable() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RessourceIntrouvableException.class, () -> clientService.getClientById(9L));
    }

    @Test
    void modifierOuSupprimerUnClientHorsPerimetreEstRefuse() {
        Client client = clientDe(agentB, rabat);
        when(clientRepository.findById(9L)).thenReturn(Optional.of(client));
        when(currentUser.canAccess(2L, AGENT_B)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> clientService.updateClient(9L, dto));
        assertThrows(AccessDeniedException.class, () -> clientService.deleteClient(9L));
        verify(clientRepository, never()).save(any());
        verify(clientRepository, never()).delete(any(Client.class));
    }

    /** Ses creances, reglements et relances portent le meme departement (cles etrangeres composites). */
    @Test
    void deplacerUnClientVersUnAutreDepartementEstRefuse() {
        Client client = clientDe(agentA, casa);
        dto.setDepartementId(2L);
        when(clientRepository.findById(9L)).thenReturn(Optional.of(client));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> clientService.updateClient(9L, dto));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void modifierSansToucherAuDepartementConserveLeResponsable() {
        Client client = clientDe(agentA, casa);
        dto.setDepartementId(1L); // identique : accepte
        when(clientRepository.findById(9L)).thenReturn(Optional.of(client));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        clientService.updateClient(9L, dto);

        assertEquals(AGENT_A, client.getAgentRecouv().getEmail(), "sans agentName, le responsable ne change pas");
        assertEquals(1L, client.getDepartement().getId());
    }

    @Test
    void supprimerUnClientDeSonPerimetreFonctionne() {
        Client client = clientDe(agentA, casa);
        when(clientRepository.findById(9L)).thenReturn(Optional.of(client));
        when(currentUser.canAccess(1L, AGENT_A)).thenReturn(true);

        clientService.deleteClient(9L);

        verify(clientRepository).delete(client);
    }

    private Client clientDe(Utilisateur agent, Departement departement) {
        Client c = new Client();
        c.setId(9L);
        c.setRaisonSociale("Acme SARL");
        c.setAgentRecouv(agent);
        c.setDepartement(departement);
        return c;
    }
}
