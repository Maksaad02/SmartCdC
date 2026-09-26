package org.maksaad.recouvchatbot_rag.tools;

import org.maksaad.recouvchatbot_rag.client.BackendAccessDeniedException;
import org.maksaad.recouvchatbot_rag.client.BackendUnavailableException;
import org.maksaad.recouvchatbot_rag.client.RecouvBackendClient;
import org.maksaad.recouvchatbot_rag.client.dto.ClientDTO;
import org.maksaad.recouvchatbot_rag.client.dto.CreanceDTO;
import org.maksaad.recouvchatbot_rag.support.AuditHash;
import org.maksaad.recouvchatbot_rag.support.TextSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.maksaad.recouvchatbot_rag.security.CallerContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Outils exposes au modele. Tous en LECTURE SEULE.
 *
 * Le cloisonnement n'est PAS assure ici : le backend filtre chaque reponse sur le
 * portefeuille de l'agent dont le jeton est retransmis. Une consigne de prompt ne peut pas
 * tenir lieu de controle d'acces, un utilisateur pouvant toujours demander au modele de
 * faire autrement.
 *
 * Ce que ces outils ajoutent, en profondeur :
 *  - les arguments choisis par le modele sont valides (liste blanche) : le modele est
 *    manipulable, ses arguments ne sont pas une entree de confiance ;
 *  - les erreurs sont distinguees (acces refuse / service indisponible) et jamais
 *    presentees comme "introuvable" ;
 *  - les donnees renvoyees sont nettoyees et bornees : une raison sociale piegee ne doit
 *    pas pouvoir se faire passer pour une consigne ;
 *  - chaque appel est trace (outil, utilisateur, empreinte de l'argument, jamais sa valeur).
 */
@Component("backendApiTool")
public class BackendApiTool {

    private static final Logger audit = LoggerFactory.getLogger("chat.audit");
    private static final Logger log = LoggerFactory.getLogger(BackendApiTool.class);

    /** Lettres (accents inclus), chiffres, espace et ponctuation usuelle d'une raison sociale, d'un ICE ou d'un telephone. */
    private static final Pattern SEARCH_TERM = Pattern.compile("^[\\p{L}\\p{N} .,'&@+()\\-]{2,80}$");
    private static final int MAX_FIELD = 120;
    private static final int MAX_INVOICES_LISTED = 50;

    static final String DENIED = "Accès refusé : cette information n'est pas disponible pour votre compte.";
    static final String UNAVAILABLE = "Le service est momentanément indisponible. Réessayez dans un instant.";

    private final RecouvBackendClient backendClient;

    public BackendApiTool(RecouvBackendClient backendClient) {
        this.backendClient = backendClient;
    }

    @Tool(description = "Search for clients by company name, ICE number, or phone number. " +
            "Use this when the user mentions their company or wants to find client information.")
    public String searchClient(
            @ToolParam(description = "The search query: company name, ICE number, or phone number") String query,
            ToolContext toolContext) {

        String term = query == null ? "" : query.strip();
        audit("searchClient", term, toolContext);
        // Le modele choisit cet argument, et le modele peut etre manipule : on n'envoie au
        // backend que ce qui ressemble a un nom, un ICE ou un telephone.
        if (!SEARCH_TERM.matcher(term).matches()) {
            return "Terme de recherche invalide : indiquez un nom de société, un ICE ou un numéro de téléphone (2 à 80 caractères).";
        }

        try {
            List<ClientDTO> clients = backendClient.searchClients(term, token(toolContext));

            if (clients == null || clients.isEmpty()) {
                return "Aucun client de votre portefeuille ne correspond à cette recherche.";
            }
            if (clients.size() == 1) {
                ClientDTO c = clients.get(0);
                return String.format("Client trouvé : %s (ID : %d, ICE : %s, Téléphone : %s)",
                        field(c.getRaisonSociale()), c.getId(), field(c.getIce()), field(c.getTelephone()));
            }

            StringBuilder result = new StringBuilder(
                    String.format("%d clients correspondent :\n", clients.size()));
            for (ClientDTO c : clients) {
                result.append(String.format("- %s (ID : %d, ICE : %s)\n",
                        field(c.getRaisonSociale()), c.getId(), field(c.getIce())));
            }
            result.append("\nPrécisez de quel client il s'agit.");
            return result.toString();

        } catch (BackendAccessDeniedException e) {
            return DENIED;
        } catch (BackendUnavailableException e) {
            log.warn("Backend indisponible pour searchClient : {}", e.getMessage());
            return UNAVAILABLE;
        } catch (RuntimeException e) {
            log.error("Erreur inattendue dans searchClient", e);
            return UNAVAILABLE;
        }
    }

    @Tool(description = "Get all debts (invoices) for a specific client. " +
            "Use this when the user asks about their invoices, unpaid amounts, or payment status.")
    public String getClientDebts(
            @ToolParam(description = "The client ID number") Long clientId,
            ToolContext toolContext) {

        audit("getClientDebts", String.valueOf(clientId), toolContext);
        if (clientId == null || clientId <= 0) {
            return "Identifiant client invalide. Recherchez d'abord le client.";
        }

        try {
            List<CreanceDTO> creances = backendClient.getClientCreances(clientId, token(toolContext));

            if (creances == null || creances.isEmpty()) {
                return String.format("Le client %d n'a aucune créance.", clientId);
            }

            StringBuilder result = new StringBuilder(String.format("Le client a %d facture(s) :\n\n", creances.size()));
            double totalUnpaid = 0.0;
            int shown = 0;

            for (CreanceDTO creance : creances) {
                totalUnpaid += value(creance.getSolde());
                if (shown++ >= MAX_INVOICES_LISTED) {
                    continue; // le total reste exact, seul le detail est borne
                }
                result.append(String.format("Facture %s :\n", field(creance.getNumFacture())));
                result.append(String.format("   - Montant total : %.2f DH\n", value(creance.getMontantTotal())));
                result.append(String.format("   - Payé : %.2f DH\n", value(creance.getMontantEncaisse())));
                result.append(String.format("   - Solde dû : %.2f DH\n", value(creance.getSolde())));
                result.append(String.format("   - Statut : %s\n", field(creance.getStatut())));
                if (creance.getJoursRetard() > 0) {
                    result.append(String.format("   - Retard : %d jours\n", creance.getJoursRetard()));
                }
                if (value(creance.getMontantPenalites()) > 0) {
                    result.append(String.format("   - Pénalités : %.2f DH\n", value(creance.getMontantPenalites())));
                }
                result.append("\n");
            }
            if (creances.size() > MAX_INVOICES_LISTED) {
                result.append(String.format("(%d autres factures non détaillées)\n", creances.size() - MAX_INVOICES_LISTED));
            }
            result.append(String.format("TOTAL DÛ : %.2f DH", totalUnpaid));
            return result.toString();

        } catch (BackendAccessDeniedException e) {
            // Inexistant ou hors portefeuille : le backend ne distingue pas, nous non plus.
            return DENIED;
        } catch (BackendUnavailableException e) {
            log.warn("Backend indisponible pour getClientDebts : {}", e.getMessage());
            return UNAVAILABLE;
        } catch (RuntimeException e) {
            log.error("Erreur inattendue dans getClientDebts", e);
            return UNAVAILABLE;
        }
    }

    private static String field(String value) {
        return TextSanitizer.cleanField(value, MAX_FIELD);
    }

    private static double value(Double number) {
        return number == null ? 0.0 : number;
    }

    /**
     * Identite de l'agent, transmise par ChatAiService dans le contexte de l'appel (et non lue dans une
     * variable de thread : en streaming l'outil s'execute sur un autre thread).
     */
    private static String token(ToolContext toolContext) {
        Object token = toolContext == null ? null : toolContext.getContext().get(CallerContext.TOKEN_KEY);
        return token instanceof String s && !s.isBlank() ? s : null;
    }

    /** Trace : quel utilisateur, quel outil, et une empreinte de l'argument (jamais sa valeur). */
    private static void audit(String tool, String argument, ToolContext toolContext) {
        Object user = toolContext == null ? null : toolContext.getContext().get(CallerContext.USER_KEY);
        audit.info("tool={} user={} argHash={}", tool, user != null ? user : "anonyme", AuditHash.of(argument));
    }
}
