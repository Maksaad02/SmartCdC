package com.recouvtech.recouvback.service.facture;

import com.recouvtech.recouvback.exception.ExtractionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PdfFactureTest {

    @Test
    void laCoucheTexteEstLueLocalement() {
        byte[] pdf = PdfDeTest.avecTexte("FACTURE N F-2026-0042", "Client : ACME SARL", "Total TTC : 12 500,00 MAD");

        PdfFacture.verifier(pdf);
        String texte = PdfFacture.texte(pdf);

        assertTrue(texte.contains("F-2026-0042"));
        assertTrue(texte.contains("ACME SARL"));
    }

    @Test
    void unPdfSansTexteEstTraiteCommeUnScan() {
        String texte = PdfFacture.texte(PdfDeTest.sansTexte(1));

        assertFalse(PdfFacture.texteExploitable(texte));
    }

    @Test
    void unTexteSuffisantEstExploitable() {
        assertTrue(PdfFacture.texteExploitable("x".repeat(PdfFacture.TEXTE_MIN)));
        assertFalse(PdfFacture.texteExploitable(" \n".repeat(500) + "x".repeat(PdfFacture.TEXTE_MIN - 1)));
    }

    @Test
    void unFichierQuiNEstPasUnPdfEstRefuse() {
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> PdfFacture.verifier("<html>facture</html>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatut());
        assertThrows(ExtractionException.class, () -> PdfFacture.verifier(new byte[0]));
    }

    @Test
    void unPdfEndommageOuTropLongEstRefuse() {
        assertThrows(ExtractionException.class,
                () -> PdfFacture.texte("%PDF-1.7 tronque".getBytes(StandardCharsets.US_ASCII)));
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> PdfFacture.texte(PdfDeTest.sansTexte(PdfFacture.PAGES_MAX + 1)));
        assertTrue(e.getMessage().contains("pages"));
    }
}
