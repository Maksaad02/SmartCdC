package org.maksaad.recouvchatbot_rag.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Regression du bug "le chatbot annonce que le client n'existe pas" : les erreurs 401/403/5xx
 * etaient ecrites sur System.err puis ignorees, donnant une liste vide.
 */
class RecouvBackendClientTest {

    private MockRestServiceServer server;
    private RecouvBackendClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RecouvBackendClient(builder, "http://backend.test", "cle-de-service");
    }

    @Test
    void transmetLaCleDeServiceEtLeJetonDeLAgent() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-RECOUV-KEY", "cle-de-service"))
                .andExpect(header("Authorization", "Bearer jeton-de-l-agent"))
                .andRespond(withSuccess("[{\"id\":1,\"raisonSociale\":\"Acme\"}]", MediaType.APPLICATION_JSON));

        var clients = client.searchClients("Acme", "jeton-de-l-agent");

        assertEquals(1, clients.size());
        assertEquals("Acme", clients.get(0).getRaisonSociale());
        server.verify();
    }

    @Test
    void sansJetonAucunEnTeteAuthorizationNEstEnvoye() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andExpect(request -> org.junit.jupiter.api.Assertions.assertNull(request.getHeaders().getFirst("Authorization")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(BackendAccessDeniedException.class, () -> client.searchClients("Acme", null));
    }

    @Test
    void unRefusEstUneErreurEtNonUneListeVide() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(BackendAccessDeniedException.class, () -> client.searchClients("Acme", "jeton-de-l-agent"));
    }

    @Test
    void unJetonExpireEstUnRefus() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(BackendAccessDeniedException.class, () -> client.searchClients("Acme", "jeton-de-l-agent"));
    }

    @Test
    void uneErreurServeurEstUnePanneEtNonUneListeVide() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(BackendUnavailableException.class, () -> client.searchClients("Acme", "jeton-de-l-agent"));
    }

    @Test
    void uneCoupureReseauEstUnePanne() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/search?query=Acme"))
                .andRespond(request -> { throw new IOException("Connection reset"); });

        assertThrows(BackendUnavailableException.class, () -> client.searchClients("Acme", "jeton-de-l-agent"));
    }

    @Test
    void uneCreanceIntrouvableOuHorsPortefeuilleEstUnRefus() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/9/creances"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(BackendAccessDeniedException.class, () -> client.getClientCreances(9L, "jeton-de-l-agent"));
    }

    @Test
    void lesCreancesSontDesérialisees() {
        server.expect(requestTo("http://backend.test/api/external/chatbot/clients/9/creances"))
                .andRespond(withSuccess("[{\"numFacture\":\"F-1\",\"solde\":120.5,\"joursRetard\":3}]", MediaType.APPLICATION_JSON));

        var creances = client.getClientCreances(9L, "jeton-de-l-agent");

        assertEquals("F-1", creances.get(0).getNumFacture());
        assertEquals(120.5, creances.get(0).getSolde());
    }
}
