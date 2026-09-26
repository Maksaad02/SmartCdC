package org.maksaad.recouvchatbot_rag.client;

/**
 * Le backend a refuse l'acces (401/403) ou la ressource n'est pas visible pour cet agent.
 * Distincte d'une panne : l'agent doit apprendre qu'il n'a pas acces, pas que le service est
 * en panne, et le modele ne doit surtout pas conclure que "le client n'existe pas".
 */
public class BackendAccessDeniedException extends RuntimeException {
    public BackendAccessDeniedException(String message) {
        super(message);
    }
}
