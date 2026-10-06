package com.recouvtech.recouvback.service.facture;

import com.anthropic.models.beta.messages.MessageCreateParams;
import com.recouvtech.recouvback.dto.FactureDTO.FactureExtractionDTO;
import com.recouvtech.recouvback.exception.ExtractionException;
import com.recouvtech.recouvback.security.CurrentUser;
import com.recouvtech.recouvback.service.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FactureExtractionServiceTest {

    private static final byte[] FACTURE_NUMERIQUE = PdfDeTest.avecTexte(
            "SOCIETE EMETTRICE SA - ICE 001122334455667",
            "FACTURE N F-2026-0042 du 01/09/2026",
            "Client : ACME SARL - ICE 009988776655443",
            "Echeance : 01/10/2026",
            "Total TTC : 12 500,00 MAD");

    private final LecteurFacture lecteur = mock(LecteurFacture.class);
    private final ClientService clientService = mock(ClientService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private FactureExtractionService service;

    @BeforeEach
    void init() {
        when(lecteur.isActif()).thenReturn(true);
        when(currentUser.email()).thenReturn("agent@exemple.ma");
        service = new FactureExtractionService(lecteur, clientService, currentUser, 3);
    }

    private static FactureLue lue(String num, String emission, String echeance, String montant,
                                  String client, String ice, String... remarques) {
        return new FactureLue(num, emission, echeance, montant, client, ice, List.of(remarques));
    }

    @Test
    void uneFactureNumeriqueEnvoieSeulementSonTexte() {
        when(lecteur.lireTexte(anyString()))
                .thenReturn(lue("F-2026-0042", "2026-09-01", "2026-10-01", "12500.00", "ACME SARL", "009988776655443"));
        when(clientService.trouverPourFacture("009988776655443", "ACME SARL")).thenReturn(Optional.of("ACME SARL"));

        FactureExtractionDTO r = service.extraire(FACTURE_NUMERIQUE);

        verify(lecteur).lireTexte(argThat(t -> t.contains("F-2026-0042")));
        verify(lecteur, never()).lirePdf(any());
        assertEquals("TEXTE", r.source());
        assertEquals("F-2026-0042", r.numFacture());
        assertEquals(LocalDate.of(2026, 9, 1), r.dateEmission());
        assertEquals(LocalDate.of(2026, 10, 1), r.echeance());
        assertEquals(new BigDecimal("12500.00"), r.montantFacture());
        assertEquals("ACME SARL", r.clientTrouve());
        assertTrue(r.avertissements().isEmpty(), () -> r.avertissements().toString());
    }

    @Test
    void uneFactureScanneeEnvoieLePdf() {
        byte[] scan = PdfDeTest.sansTexte(1);
        when(lecteur.lirePdf(scan)).thenReturn(lue("F-1", "", "", "", "", ""));
        when(clientService.trouverPourFacture(null, null)).thenReturn(Optional.empty());

        FactureExtractionDTO r = service.extraire(scan);

        verify(lecteur, never()).lireTexte(anyString());
        assertEquals("SCAN", r.source());
        assertNull(r.echeance());
        assertNull(r.montantFacture());
        assertTrue(r.avertissements().contains("Échéance non trouvée : à saisir."));
        assertTrue(r.avertissements().contains("Montant TTC non trouvé : à saisir."));
        assertTrue(r.avertissements().contains("Client non identifié sur la facture : à choisir."));
    }

    @Test
    void lesValeursInvalidesSontEcarteesEtSignalees() {
        when(lecteur.lireTexte(anyString()))
                .thenReturn(lue("F-9", "2026-09-31", "2026-08-01", "12 500,5", "Inconnu SA", "", "Total HT et TTC ambigus"));
        when(clientService.trouverPourFacture(null, "Inconnu SA")).thenReturn(Optional.empty());

        FactureExtractionDTO r = service.extraire(FACTURE_NUMERIQUE);

        assertNull(r.dateEmission(), "le 31 septembre n'existe pas");
        assertEquals(new BigDecimal("12500.50"), r.montantFacture(), "virgule decimale et espaces toleres");
        assertNull(r.clientTrouve());
        assertTrue(r.avertissements().contains("Total HT et TTC ambigus"), "les remarques du modele sont transmises");
        assertTrue(r.avertissements().stream().anyMatch(a -> a.startsWith("La date d'émission lue")));
        assertTrue(r.avertissements().stream().anyMatch(a -> a.contains("« Inconnu SA » introuvable")));
    }

    @Test
    void sansCleLaFonctionnaliteEstDesactivee() {
        when(lecteur.isActif()).thenReturn(false);

        ExtractionException e = assertThrows(ExtractionException.class, () -> service.extraire(FACTURE_NUMERIQUE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatut());
        verifyNoInteractions(clientService);
    }

    @Test
    void leQuotaParUtilisateurEstApplique() {
        when(lecteur.lireTexte(anyString())).thenReturn(lue("F", "", "", "", "", ""));
        when(clientService.trouverPourFacture(any(), any())).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            service.extraire(FACTURE_NUMERIQUE);
        }
        ExtractionException e = assertThrows(ExtractionException.class, () -> service.extraire(FACTURE_NUMERIQUE));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatut());

        when(currentUser.email()).thenReturn("autre@exemple.ma");
        assertDoesNotThrow(() -> service.extraire(FACTURE_NUMERIQUE), "le quota est propre a chaque utilisateur");
    }

    @Test
    void unFichierInvalideNeConsommePasDeQuotaNiDAppel() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ExtractionException.class, () -> service.extraire("pas un pdf".getBytes()));
        }
        verify(lecteur, never()).lireTexte(anyString());
        verify(lecteur, never()).lirePdf(any());
    }

    /** Le schema JSON est derive de FactureLue et valide localement par le SDK a la construction. */
    @Test
    void leSchemaDeSortieStructureeEstAccepteParLeSdk() {
        assertDoesNotThrow(() -> MessageCreateParams.builder()
                .model("claude-opus-5-5")
                .maxTokens(100L)
                .outputConfig(FactureLue.class)
                .addUserMessage("test")
                .build());
    }
}
