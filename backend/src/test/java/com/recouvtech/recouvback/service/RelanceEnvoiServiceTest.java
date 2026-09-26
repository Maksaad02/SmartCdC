package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RelanceRepository;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelanceEnvoiServiceTest {

    private static final String PROPRIETAIRE = "agent.b@cabinet.test";

    @Mock private RelanceRepository relanceRepository;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private RelanceEnvoiService envoiService;

    private Relance relance;
    private Creance creance;
    private Client client;

    @BeforeEach
    void setUp() {
        Utilisateur agent = new Utilisateur();
        agent.setEmail(PROPRIETAIRE);

        client = new Client();
        client.setEmail("client@debiteur.test");

        creance = new Creance();
        creance.setNumFacture("FB-001");
        creance.setAgentRecouv(agent);
        creance.setClient(client);
        creance.setStatut(StatutCreance.IMPAYEE);

        relance = new Relance();
        relance.setId(5L);
        relance.setCreance(creance);
        Departement casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);
        relance.setDepartement(casa);
        relance.setStatutRelance(StatutRelance.EN_ATTENTE);
        relance.setMessage("Votre facture est due.");
    }

    @Test
    void relanceIntrouvable() {
        when(relanceRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RessourceIntrouvableException.class, () -> envoiService.preparerEnvoiManuel(9L));
    }

    @Test
    void unAgentNePeutPasEnvoyerLaRelanceDUnAutreAgent() {
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> envoiService.preparerEnvoiManuel(5L));
        verify(relanceRepository, never()).save(any());
    }

    @Test
    void uneRelanceDejaTraiteeNePeutPasEtreRenvoyee() {
        relance.setStatutRelance(StatutRelance.EFFECTUEE);
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> envoiService.preparerEnvoiManuel(5L));
    }

    @Test
    void uneCreancePayeeAnnuleLaRelanceEtEstRefusee() {
        creance.setStatut(StatutCreance.PAYEE);
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(true);

        var preparation = envoiService.preparerEnvoiManuel(5L);

        assertNotNull(preparation.refus());
        assertEquals(StatutRelance.ANNULEE, relance.getStatutRelance());
        verify(relanceRepository).save(relance);
    }

    @Test
    void sansEmailClientLaRelanceEstEnEchecEtRefusee() {
        client.setEmail("  ");
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(true);

        var preparation = envoiService.preparerEnvoiManuel(5L);

        assertNotNull(preparation.refus());
        assertEquals(StatutRelance.ECHEC, relance.getStatutRelance());
    }

    @Test
    void leDestinataireVientDeLaCreanceJamaisDeLAppelant() {
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(true);

        var courriel = envoiService.preparerEnvoiManuel(5L).courriel();

        assertEquals("client@debiteur.test", courriel.destinataire());
        assertEquals("Relance - Facture N°FB-001", courriel.sujet());
        assertEquals("Votre facture est due.", courriel.corps());
    }

    @Test
    void sansMessageLeCourrielPartAvecUnRappelStandardEtNonUnCorpsVide() {
        relance.setMessage(null);
        relance.setCommentaire(null);
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));
        when(currentUser.canAccess(1L, PROPRIETAIRE)).thenReturn(true);

        var courriel = envoiService.preparerEnvoiManuel(5L).courriel();

        assertEquals("Nous vous rappelons le règlement de la facture N°FB-001.", courriel.corps());
    }

    @Test
    void lEnvoiAutomatiqueIgnoreUneRelanceDejaTraitee() {
        relance.setStatutRelance(StatutRelance.ENVOYEE);
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));

        assertNull(envoiService.preparerEnvoiAutomatique(5L));
    }

    @Test
    void unMotifTropLongEstTronquePourLaColonneVarchar255() {
        when(relanceRepository.findById(5L)).thenReturn(Optional.of(relance));

        envoiService.marquerEchec(5L, "x".repeat(600));

        assertEquals(250, relance.getCommentaire().length());
        assertEquals(StatutRelance.ECHEC, relance.getStatutRelance());
    }
}
