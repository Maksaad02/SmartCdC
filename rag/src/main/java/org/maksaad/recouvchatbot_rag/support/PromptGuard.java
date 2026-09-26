package org.maksaad.recouvchatbot_rag.support;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reperage (et non blocage) des tentatives d'injection de prompt les plus courantes.
 *
 * Une liste de motifs se contourne toujours (autre langue, reformulation) : elle ne
 * protege donc de rien et ne doit JAMAIS servir a autoriser ou refuser. Elle sert de
 * telemetrie : une alerte dans les journaux quand un utilisateur essaie manifestement de
 * detourner l'assistant, pour repondre a un incident ou ajuster les regles.
 */
public final class PromptGuard {

    private static final List<Pattern> SUSPICIOUS = List.of(
            Pattern.compile("(ignore|disregard|forget|oublie|ignorer|ignore[zr]?)\\W+(all|any|the|previous|above|prior|toutes?|les|tes|vos|pr[ée]c[ée]dentes?)?.{0,40}(instructions?|prompts?|rules?|r[èe]gles?|consignes?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(system|developer|syst[èe]me)\\s+(prompt|message|instructions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|show|print|repeat|affiche|montre|r[ée]p[èe]te).{0,30}(prompt|instructions?|consignes?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now|tu es maintenant|from now on you|d[ée]sormais tu", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak|developer mode|mode d[ée]veloppeur|\\bDAN\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("!\\[[^\\]]*\\]\\(https?://", Pattern.CASE_INSENSITIVE));

    private PromptGuard() {
    }

    public static boolean looksSuspicious(String question) {
        if (question == null) {
            return false;
        }
        return SUSPICIOUS.stream().anyMatch(p -> p.matcher(question).find());
    }
}
