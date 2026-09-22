package org.maksaad.recouvchatbot_rag.client;

import org.maksaad.recouvchatbot_rag.client.dto.ClientDTO;
import org.maksaad.recouvchatbot_rag.client.dto.CreanceDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.maksaad.recouvchatbot_rag.security.CallerToken;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * HTTP Client for RecouvTek Backend API
 * Provides methods to query client and debt data via REST API
 */
@Component
public class RecouvBackendClient {

    private final RestClient restClient;

    public RecouvBackendClient(
            @Value("${recouv.backend.base-url}") String baseUrl,
            @Value("${recouv.backend.api-key}") String apiKey) {

        // X-RECOUV-KEY authentifie le SERVICE ; l'en-tete Authorization transporte
        // l'identite de l'AGENT appelant. Le backend exige desormais les deux, si
        // bien que le chatbot ne voit que le portefeuille de cet agent au lieu de
        // l'integralite de la base.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-RECOUV-KEY", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestInitializer(request -> {
                    String token = CallerToken.get();
                    if (token != null) {
                        request.getHeaders().setBearerAuth(token);
                    }
                })
                .build();
    }

    /**
     * Search clients by keyword (company name, ICE, or phone)
     * 
     * @param query Search term
     * @return List of matching clients (empty if none found or error)
     */
    public List<ClientDTO> searchClients(String query) {
        try {
            return restClient.get()
                    .uri("/api/external/chatbot/clients/search?query={query}", query)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        // Log but don't throw - return empty list instead
                        System.err.println("Client search failed: " + response.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        System.err.println("Backend server error: " + response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<List<ClientDTO>>() {
                    });
        } catch (Exception e) {
            System.err.println("Error searching clients: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get client details by ID
     * 
     * @param clientId Client ID
     * @return Client details or null if not found
     */
    public ClientDTO getClientById(Long clientId) {
        try {
            return restClient.get()
                    .uri("/api/external/chatbot/clients/{id}", clientId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        System.err.println("Client not found: " + clientId);
                    })
                    .body(ClientDTO.class);
        } catch (Exception e) {
            System.err.println("Error fetching client: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get all debts for a specific client
     * 
     * @param clientId Client ID
     * @return List of debts (empty if none or error)
     */
    public List<CreanceDTO> getClientCreances(Long clientId) {
        try {
            return restClient.get()
                    .uri("/api/external/chatbot/clients/{id}/creances", clientId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        System.err.println("Creances not found for client: " + clientId);
                    })
                    .body(new ParameterizedTypeReference<List<CreanceDTO>>() {
                    });
        } catch (Exception e) {
            System.err.println("Error fetching client creances: " + e.getMessage());
            return Collections.emptyList();
        }
    }

}
