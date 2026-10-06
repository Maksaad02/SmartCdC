package com.recouvtech.recouvback.service.facture;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** PDF generes pour les tests : avec couche texte (facture numerique) ou sans (equivalent d'un scan). */
public final class PdfDeTest {

    private PdfDeTest() {
    }

    public static byte[] avecTexte(String... lignes) {
        return generer(1, lignes);
    }

    public static byte[] sansTexte(int pages) {
        return generer(pages);
    }

    private static byte[] generer(int pages, String... lignes) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (i == 0 && lignes.length > 0) {
                    try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                        flux.beginText();
                        flux.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                        flux.setLeading(14);
                        flux.newLineAtOffset(50, 700);
                        for (String ligne : lignes) {
                            flux.showText(ligne);
                            flux.newLine();
                        }
                        flux.endText();
                    }
                }
            }
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            document.save(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
