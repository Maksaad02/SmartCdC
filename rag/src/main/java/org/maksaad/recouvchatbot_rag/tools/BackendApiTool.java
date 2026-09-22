package org.maksaad.recouvchatbot_rag.tools;

import org.maksaad.recouvchatbot_rag.client.RecouvBackendClient;
import org.maksaad.recouvchatbot_rag.client.dto.ClientDTO;
import org.maksaad.recouvchatbot_rag.client.dto.CreanceDTO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outils exposes au modele.
 *
 * Le cloisonnement n'est PAS assure ici : le backend filtre chaque reponse sur
 * le portefeuille de l'agent dont le jeton est retransmis. Une consigne de
 * prompt ne peut pas tenir lieu de controle d'acces, un utilisateur pouvant
 * toujours demander au modele de faire autrement.
 *
 * getAllUnpaidDebtsStats() a ete retire : cet agregat portait sur l'ensemble
 * des clients et n'avait aucun usage legitime pour un appelant donne.
 *
 * Backend API Tool for AI Chatbot
 * Provides functions for the AI to query client and debt data via REST API
 * Replaces the deprecated SqlTool that accessed MySQL directly
 */
@Component("backendApiTool")
public class BackendApiTool {

    private final RecouvBackendClient backendClient;

    public BackendApiTool(RecouvBackendClient backendClient) {
        this.backendClient = backendClient;
    }

    /**
     * Search for clients by name, ICE number, or phone number
     * Use this when the user mentions a company name or wants to find their account
     * 
     * @param query Company name, ICE number, or phone number
     * @return Description of found clients or error message
     */
    @Tool(description = "Search for clients by company name, ICE number, or phone number. " +
            "Use this when the user mentions their company or wants to find client information.")
    public String searchClient(
            @ToolParam(description = "The search query: company name, ICE number, or phone number") String query) {

        if (query == null || query.trim().isEmpty()) {
            return "Error: Please provide a company name, ICE number, or phone number to search.";
        }

        try {
            List<ClientDTO> clients = backendClient.searchClients(query.trim());

            if (clients == null || clients.isEmpty()) {
                return String.format(
                        "No client found matching '%s'. Please verify the company name, ICE, or phone number.", query);
            }

            if (clients.size() == 1) {
                ClientDTO client = clients.get(0);
                return String.format("Client found: %s (ID: %d, ICE: %s, Phone: %s)",
                        client.getRaisonSociale(),
                        client.getId(),
                        client.getIce(),
                        client.getTelephone());
            }

            // Multiple results
            StringBuilder result = new StringBuilder(
                    String.format("Found %d clients matching '%s':\n", clients.size(), query));
            for (ClientDTO client : clients) {
                result.append(String.format("- %s (ID: %d, ICE: %s)\n",
                        client.getRaisonSociale(),
                        client.getId(),
                        client.getIce()));
            }
            result.append("\nPlease specify which client you're asking about.");
            return result.toString();

        } catch (Exception e) {
            return "The backend service is temporarily unavailable. Please try again later.";
        }
    }

    /**
     * Get all debts (invoices) for a specific client
     * Use this after finding the client ID with searchClient
     * 
     * @param clientId The client ID obtained from searchClient
     * @return Description of client's debts with amounts and status
     */
    @Tool(description = "Get all debts (invoices) for a specific client. " +
            "Use this when the user asks about their invoices, unpaid amounts, or payment status.")
    public String getClientDebts(
            @ToolParam(description = "The client ID number") Long clientId) {

        if (clientId == null || clientId <= 0) {
            return "Error: Invalid client ID. Please search for the client first.";
        }

        try {
            List<CreanceDTO> creances = backendClient.getClientCreances(clientId);

            if (creances == null || creances.isEmpty()) {
                return String.format("Client ID %d has no outstanding debts.", clientId);
            }

            StringBuilder result = new StringBuilder(String.format("Client has %d invoice(s):\n\n", creances.size()));
            double totalUnpaid = 0.0;

            for (CreanceDTO creance : creances) {
                result.append(String.format("📄 Invoice %s:\n", creance.getNumFacture()));
                result.append(String.format("   - Total Amount: %.2f DH\n", creance.getMontantTotal()));
                result.append(String.format("   - Paid: %.2f DH\n", creance.getMontantEncaisse()));
                result.append(String.format("   - Balance Due: %.2f DH\n", creance.getSolde()));
                result.append(String.format("   - Status: %s\n", creance.getStatut()));

                if (creance.getJoursRetard() > 0) {
                    result.append(String.format("   - Late by: %d days\n", creance.getJoursRetard()));
                }

                if (creance.getMontantPenalites() > 0) {
                    result.append(String.format("   - Penalties: %.2f DH\n", creance.getMontantPenalites()));
                }

                result.append("\n");
                totalUnpaid += creance.getSolde();
            }

            result.append(String.format("💰 TOTAL BALANCE DUE: %.2f DH", totalUnpaid));
            return result.toString();

        } catch (Exception e) {
            return "The backend service is temporarily unavailable. Please try again later.";
        }
    }

}
