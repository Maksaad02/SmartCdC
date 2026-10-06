package com.recouvtech.recouvback.service.facture;

import com.recouvtech.recouvback.exception.ExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controle et lecture LOCALE d'une facture PDF (PDFBox) : rien ne quitte le serveur ici.
 *
 * Le type annonce par le navigateur n'est pas une preuve : on verifie la signature du fichier.
 */
public final class PdfFacture {

    public static final long TAILLE_MAX = 10L * 1024 * 1024;
    public static final int PAGES_MAX = 10;
    /** En dessous, la couche texte est absente ou inexploitable (facture scannee) : lecture visuelle. */
    static final int TEXTE_MIN = 100;

    private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private PdfFacture() {
    }

    /** Refuse (400) tout ce qui n'est pas un PDF lisible de taille raisonnable. */
    public static void verifier(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ExtractionException(HttpStatus.BAD_REQUEST, "Fichier vide");
        }
        if (pdf.length > TAILLE_MAX) {
            throw new ExtractionException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (10 Mo maximum)");
        }
        if (!commencePar(pdf, SIGNATURE)) {
            throw new ExtractionException(HttpStatus.BAD_REQUEST, "Le fichier n'est pas un PDF");
        }
    }

    /**
     * Texte de la facture, extrait localement. Chaine vide si le PDF n'a pas de couche texte (scan).
     * Verifie aussi que le document s'ouvre et ne depasse pas {@link #PAGES_MAX} pages.
     */
    public static String texte(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() > PAGES_MAX) {
                throw new ExtractionException(HttpStatus.BAD_REQUEST,
                        "Facture trop longue (" + PAGES_MAX + " pages maximum)");
            }
            return new PDFTextStripper().getText(document).strip();
        } catch (InvalidPasswordException e) {
            throw new ExtractionException(HttpStatus.BAD_REQUEST, "PDF protégé par mot de passe");
        } catch (IOException e) {
            throw new ExtractionException(HttpStatus.BAD_REQUEST, "PDF illisible ou endommagé");
        }
    }

    /** true si la couche texte suffit : seul ce texte sera envoye, pas le fichier. */
    static boolean texteExploitable(String texte) {
        return texte.chars().filter(c -> !Character.isWhitespace(c)).count() >= TEXTE_MIN;
    }

    private static boolean commencePar(byte[] donnees, byte[] prefixe) {
        if (donnees.length < prefixe.length) {
            return false;
        }
        for (int i = 0; i < prefixe.length; i++) {
            if (donnees[i] != prefixe[i]) {
                return false;
            }
        }
        return true;
    }
}
