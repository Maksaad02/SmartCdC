package org.maksaad.recouvchatbot_rag.client;

/** Le backend est injoignable, trop lent ou en erreur (5xx). */
public class BackendUnavailableException extends RuntimeException {
    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
