package org.maksaad.recouvchatbot_rag.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Identite de l'agent qui pose la question : son jeton (retransmis au backend, qui filtre les donnees
 * sur SON portefeuille) et son identifiant (audit).
 *
 * Capturee sur le thread de la requete HTTP puis passee EXPLICITEMENT au service et aux outils :
 * en streaming, les outils s'executent sur un autre thread ou les variables de thread (ThreadLocal :
 * jeton, contexte de securite) n'existent pas.
 */
public record CallerContext(String token, String user) {

    public static final String TOKEN_KEY = "callerToken";
    public static final String USER_KEY = "callerUser";

    /** A appeler depuis le thread de la requete. */
    public static CallerContext current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new CallerContext(CallerToken.get(), auth != null ? auth.getName() : "anonyme");
    }
}
