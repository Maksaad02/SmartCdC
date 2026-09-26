package org.maksaad.recouvchatbot_rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSectionSplitterTest {

    private static final String DOC = """
            # Recouvrement au Maroc

            ## Chapitre 1 : la procédure d'injonction de payer

            Dans cette section, le créancier dépose une requête auprès du tribunal compétent.

            ### Article 155

            Le juge rend une ordonnance d'injonction de payer lorsque la créance est certaine, liquide et exigible.

            ### Article 156

            Le débiteur dispose de quinze jours pour former opposition à compter de la notification.

            ## Chapitre 2 : les pénalités

            Les pénalités de retard courent à partir de l'échéance contractuelle, au taux convenu entre les parties.
            """;

    @Test
    void chaquePassageGardeLeCheminDeSesTitres() {
        List<MarkdownSectionSplitter.Section> sections = MarkdownSectionSplitter.split(DOC);

        assertEquals(4, sections.size());
        assertEquals("Recouvrement au Maroc > Chapitre 1 : la procédure d'injonction de payer > Article 155",
                sections.get(1).path());
        assertTrue(sections.get(1).text().startsWith(sections.get(1).path()),
                "le texte du passage doit commencer par son chemin de titres (contexte pour le modele)");
    }

    @Test
    void unArticleReste_intactEtNEstPasMelangeAvecSonVoisin() {
        List<MarkdownSectionSplitter.Section> sections = MarkdownSectionSplitter.split(DOC);

        String article155 = sections.get(1).text();
        assertTrue(article155.contains("certaine, liquide et exigible"));
        assertTrue(!article155.contains("quinze jours"), "l'article 156 ne doit pas fuiter dans le 155");
    }

    @Test
    void lesTitresDeNiveauSuperieurSontReinitialisesAuChangementDeChapitre() {
        List<MarkdownSectionSplitter.Section> sections = MarkdownSectionSplitter.split(DOC);

        assertEquals("Recouvrement au Maroc > Chapitre 2 : les pénalités", sections.get(3).path(),
                "l'Article 156 du chapitre 1 ne doit pas rester dans le chemin du chapitre 2");
    }

    @Test
    void unTitreSansTexteNeProduitPasDePassage() {
        List<MarkdownSectionSplitter.Section> sections = MarkdownSectionSplitter.split("# Titre\n\n## Sous-titre\n\n");

        assertTrue(sections.isEmpty());
    }

    @Test
    void unDieseDansUnBlocDeCodeNEstPasUnTitre() {
        String md = "# Doc\n\nExemple de commande :\n\n```\n# ceci est un commentaire\nls\n```\n\nEt une phrase suffisamment longue pour compter.";

        List<MarkdownSectionSplitter.Section> sections = MarkdownSectionSplitter.split(md);

        assertEquals(1, sections.size());
        assertTrue(sections.get(0).text().contains("# ceci est un commentaire"));
    }
}
