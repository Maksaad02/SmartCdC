package org.maksaad.recouvchatbot_rag.support;

/**
 * Filtre de sortie applique a une reponse qui arrive par morceaux (streaming).
 *
 * Le filtre de TextSanitizer retire images, liens et balises HTML, mais ne sait le faire que sur une
 * construction COMPLETE : "![](https://evil/?d=" n'est pas encore une image. Emettre chaque morceau
 * tel quel laisserait donc passer le debut d'une construction dangereuse avant que sa fin n'arrive.
 *
 * Ce filtre retient donc le texte a partir de toute construction ouverte ("[", "![", balise "<x")
 * jusqu'a ce qu'elle soit resolue (fermee : alors nettoyee ; ou reconnue comme du texte ordinaire), et
 * n'emet que la partie sure. A la fin du flux, tout ce qui reste est nettoye et libere.
 *
 * Le resultat est identique a TextSanitizer.cleanAnswer sur le texte complet (a la troncature pres),
 * il arrive simplement plus tot.
 */
public final class StreamingSanitizer {

    private static final String TRUNCATION_NOTE = "\n\n[Réponse tronquée]";

    private final StringBuilder raw = new StringBuilder();
    private final int maxChars;
    private String emitted = "";
    private boolean stopped;

    public StreamingSanitizer(int maxChars) {
        this.maxChars = maxChars;
    }

    /** Ajoute un morceau et renvoie la partie NOUVELLE qui peut etre emise sans risque (souvent vide). */
    public String push(String chunk) {
        if (stopped || chunk == null || chunk.isEmpty()) {
            return "";
        }
        raw.append(chunk);
        return emit(raw.substring(0, safeEnd(raw)));
    }

    /** Fin du flux : plus rien n'est retenu, tout est nettoye et libere. */
    public String finish() {
        if (stopped) {
            return "";
        }
        return emit(raw.toString());
    }

    private String emit(String candidate) {
        String cleaned = TextSanitizer.removeActiveContent(candidate);
        // Le nettoyage d'un prefixe doit rester un prefixe du nettoyage du texte complet. Si une
        // construction inattendue casse cette regle, on n'emet plus rien maintenant : ce qui suit
        // sera libere, nettoye, par finish() plutot que de risquer un texte incoherent.
        if (!cleaned.startsWith(emitted)) {
            return "";
        }
        String delta = cleaned.substring(emitted.length());
        if (cleaned.length() > maxChars) {
            delta = cleaned.substring(emitted.length(), Math.max(emitted.length(), maxChars)) + TRUNCATION_NOTE;
            stopped = true;
        }
        emitted += delta;
        return delta;
    }

    /** Fin du texte qui ne contient aucune construction encore ouverte. */
    static int safeEnd(CharSequence text) {
        String s = text.toString();
        int earliest = s.length();

        // Balises HTML : "<" suivi d'une lettre, de "/" ou de "!" et pas encore de ">".
        for (int i = s.indexOf('<'); i >= 0; i = s.indexOf('<', i + 1)) {
            boolean tagLike = i + 1 == s.length()
                    || Character.isLetter(s.charAt(i + 1)) || s.charAt(i + 1) == '/' || s.charAt(i + 1) == '!';
            if (tagLike && s.indexOf('>', i) < 0) {
                earliest = Math.min(earliest, i);
            }
        }

        // Liens et images Markdown : [texte](url) et ![texte](url).
        for (int i = s.indexOf('['); i >= 0; i = s.indexOf('[', i + 1)) {
            if (!bracketResolved(s, i)) {
                earliest = Math.min(earliest, i > 0 && s.charAt(i - 1) == '!' ? i - 1 : i);
            }
        }

        // "!" en toute fin : peut etre le debut de "![".
        if (s.endsWith("!")) {
            earliest = Math.min(earliest, s.length() - 1);
        }
        return earliest;
    }

    /** Un "[" est resolu quand on sait si c'est un lien complet ou un simple crochet. */
    private static boolean bracketResolved(String s, int open) {
        int close = s.indexOf(']', open);
        if (close < 0) {
            return false;               // "[" sans "]" : la suite peut faire un lien
        }
        if (close == s.length() - 1) {
            return false;               // "[texte]" en fin de texte : le caractere suivant peut etre "("
        }
        if (s.charAt(close + 1) != '(') {
            return true;                // "[texte] suite" : simple crochet
        }
        return s.indexOf(')', close + 2) >= 0;   // "[texte](" : resolu seulement une fois ")" arrive
    }
}
