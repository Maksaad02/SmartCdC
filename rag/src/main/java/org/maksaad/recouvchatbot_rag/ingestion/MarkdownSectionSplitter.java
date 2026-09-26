package org.maksaad.recouvchatbot_rag.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decoupe un document Markdown par section (titres #, ##, ###...).
 *
 * L'ancien decoupage par nombre de jetons coupait le texte n'importe ou : un
 * article de loi pouvait etre separe de son titre, et rien n'indiquait a quelle partie
 * appartenait un passage. Ici chaque passage porte son chemin de titres
 * ("Chapitre > Section > Article"), ce qui donne au modele le contexte et permet de
 * citer la source.
 */
public final class MarkdownSectionSplitter {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final int MIN_CONTENT_CHARS = 40;

    /** Un passage : le chemin de titres et le texte complet (titre inclus). */
    public record Section(String path, String text) {}

    private MarkdownSectionSplitter() {
    }

    public static List<Section> split(String markdown) {
        List<Section> sections = new ArrayList<>();
        String[] titles = new String[6];
        String currentPath = "";
        StringBuilder content = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : markdown.split("\\R")) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            }
            Matcher heading = inCodeBlock ? null : HEADING.matcher(line);
            if (heading != null && heading.matches()) {
                flush(sections, currentPath, content);
                int level = heading.group(1).length();
                titles[level - 1] = heading.group(2).trim();
                for (int i = level; i < titles.length; i++) {
                    titles[i] = null;
                }
                currentPath = path(titles);
                content.setLength(0);
            } else {
                content.append(line).append('\n');
            }
        }
        flush(sections, currentPath, content);
        return sections;
    }

    private static void flush(List<Section> sections, String path, StringBuilder content) {
        String body = content.toString().strip();
        // Une section sans texte propre (titre suivi directement d'un sous-titre) n'apporte rien.
        if (body.length() >= MIN_CONTENT_CHARS) {
            String text = path.isEmpty() ? body : path + "\n\n" + body;
            sections.add(new Section(path, text));
        }
    }

    private static String path(String[] titles) {
        List<String> parts = new ArrayList<>();
        for (String t : titles) {
            if (t != null) {
                parts.add(t);
            }
        }
        return String.join(" > ", parts);
    }
}
