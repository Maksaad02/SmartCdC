package org.maksaad.recouvchatbot_rag.security;

/**
 * Jeton JWT de l'appelant, porte par le thread de la requete.
 *
 * Permet au client HTTP backend de retransmettre l'identite reelle de l'agent :
 * sans cela, le chatbot appelait le backend avec une seule cle de service et
 * voyait donc l'integralite des donnees, tous agents confondus.
 */
public final class CallerToken {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private CallerToken() {
    }

    public static void set(String token) {
        TOKEN.set(token);
    }

    public static String get() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
