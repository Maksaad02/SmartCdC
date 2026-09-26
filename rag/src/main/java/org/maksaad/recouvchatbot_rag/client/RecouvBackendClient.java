package org.maksaad.recouvchatbot_rag.client;

import org.maksaad.recouvchatbot_rag.client.dto.ClientDTO;
import org.maksaad.recouvchatbot_rag.client.dto.CreanceDTO;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Client HTTP du backend RecouvTek.
 *
 * Erreurs : elles ne sont plus avalees. Auparavant les handlers onStatus se contentaient
 * d'ecrire sur System.err sans lever d'exception, si bien qu'un 401, un 403 ou un 500 se
 * traduisait par une reponse vide, que le chatbot presentait a l'agent comme "aucun client
 * trouve". Maintenant :
 *  - 401/403                 -> BackendAccessDeniedException
 *  - 5xx, delai, connexion   -> BackendUnavailableException
 *  - 404 sur une ressource   -> BackendAccessDeniedException (le backend ne distingue pas
 *    "inexistant" de "hors portefeuille", et nous non plus)
 */
@Component
public class RecouvBackendClient {

    private final RestClient restClient;

    @Autowired
    public RecouvBackendClient(
            @Value("${recouv.backend.base-url}") String baseUrl,
            @Value("${recouv.backend.api-key}") String apiKey,
            @Value("${recouv.backend.connect-timeout:3s}") Duration connectTimeout,
            @Value("${recouv.backend.read-timeout:10s}") Duration readTimeout) {
        this(timedBuilder(connectTimeout, readTimeout), baseUrl, apiKey);
    }

    /** Point d'entree des tests : permet de brancher un MockRestServiceServer sur le builder. */
    RecouvBackendClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        // X-RECOUV-KEY authentifie le SERVICE ; l'en-tete Authorization transporte
        // l'identite de l'AGENT appelant. Le backend exige les deux, si bien que le chatbot ne
        // voit que le portefeuille de cet agent au lieu de l'integralite de la base.
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-RECOUV-KEY", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestInitializer(request -> {
                    // Meme identifiant de correlation cote backend : relie les journaux des deux services.
                    String requestId = MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().set("X-Request-Id", requestId);
                    }
                })
                .build();
    }

    private static RestClient.Builder timedBuilder(Duration connectTimeout, Duration readTimeout) {
        // Sans delai, un backend qui ne repond plus bloquait la question du chatbot indefiniment.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Clients correspondant au mot-cle (raison sociale, ICE, telephone), dans le portefeuille de l'agent
     * dont le jeton est fourni. Le jeton est passe EXPLICITEMENT : en streaming, l'outil s'execute sur un
     * autre thread que la requete HTTP, ou une variable de thread (ThreadLocal) serait vide.
     */
    public List<ClientDTO> searchClients(String query, String callerToken) {
        return call(() -> restClient.get()
                .uri("/api/external/chatbot/clients/search?query={query}", query)
                .headers(h -> authorize(h, callerToken))
                .retrieve()
                .onStatus(RecouvBackendClient::isDenied, (req, res) -> {
                    throw new BackendAccessDeniedException("Accès refusé par le backend (" + res.getStatusCode() + ")");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new BackendUnavailableException("Erreur du backend (" + res.getStatusCode() + ")", null);
                })
                .body(new ParameterizedTypeReference<List<ClientDTO>>() {}));
    }

    /** Creances d'un client, dans le portefeuille de l'agent dont le jeton est fourni. */
    public List<CreanceDTO> getClientCreances(Long clientId, String callerToken) {
        return call(() -> restClient.get()
                .uri("/api/external/chatbot/clients/{id}/creances", clientId)
                .headers(h -> authorize(h, callerToken))
                .retrieve()
                .onStatus(RecouvBackendClient::isDenied, (req, res) -> {
                    throw new BackendAccessDeniedException("Accès refusé par le backend (" + res.getStatusCode() + ")");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new BackendUnavailableException("Erreur du backend (" + res.getStatusCode() + ")", null);
                })
                .body(new ParameterizedTypeReference<List<CreanceDTO>>() {}));
    }

    private static void authorize(org.springframework.http.HttpHeaders headers, String callerToken) {
        if (callerToken != null) {
            headers.setBearerAuth(callerToken);
        }
    }

    private static boolean isDenied(HttpStatusCode status) {
        return status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()
                || status.value() == HttpStatus.NOT_FOUND.value();
    }

    /** Traduit les erreurs reseau (delai, connexion refusee) en BackendUnavailableException. */
    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (BackendAccessDeniedException | BackendUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BackendUnavailableException("Backend injoignable ou trop lent", e);
        }
    }
}
