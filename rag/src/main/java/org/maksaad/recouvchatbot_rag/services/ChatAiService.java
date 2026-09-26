package org.maksaad.recouvchatbot_rag.services;

import org.maksaad.recouvchatbot_rag.config.SystemPrompts;
import org.maksaad.recouvchatbot_rag.security.CallerContext;
import org.maksaad.recouvchatbot_rag.support.AuditHash;
import org.maksaad.recouvchatbot_rag.support.StreamingSanitizer;
import org.maksaad.recouvchatbot_rag.support.PromptGuard;
import org.maksaad.recouvchatbot_rag.support.TextSanitizer;
import org.maksaad.recouvchatbot_rag.tools.BackendApiTool;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class ChatAiService {

    private static final Logger audit = LoggerFactory.getLogger("chat.audit");
    /** Une reponse plus longue est tronquee : borne ce qui est renvoye a l'interface. */
    private static final int MAX_ANSWER_CHARS = 6000;

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;

    public ChatAiService(ChatClient.Builder builder,
                         VectorStore vectorStore,
                         BackendApiTool backendApiTool,
                         @Value("${app.rag.top-k:4}") int topK,
                         @Value("${app.rag.similarity-threshold:0.5}") double similarityThreshold,
                         MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Recherche vectorielle bornee : au plus topK passages, tous au-dessus du seuil de
        // similarite (sans seuil, toute question recevait des passages sans rapport), et
        // uniquement ceux marques scope=public. Le jour ou des documents propres a une
        // organisation seront indexes, ils auront un autre scope : ils ne seront jamais
        // renvoyes par defaut a un autre client.
        SearchRequest search = SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression("scope == 'public'")
                .build();

        this.chatClient = builder
                .defaultSystem(SystemPrompts.ASSISTANT)
                .defaultTools(backendApiTool)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(search).build())
                .build();
    }

    /** Reponse complete (une seule requete, une seule reponse). */
    public String ragChat(String question, CallerContext caller) {
        String clean = TextSanitizer.cleanQuestion(question);
        long start = System.nanoTime();
        noteQuestion(clean);

        String answer = chatClient.prompt()
                .user(clean)
                .toolContext(toolContext(caller))
                .call()
                .content();

        auditQuestion(caller, clean, (System.nanoTime() - start) / 1_000_000);
        // Filtre de sortie : retire images, liens et HTML meme si le modele en produit (injection).
        return TextSanitizer.cleanAnswer(answer, MAX_ANSWER_CHARS);
    }

    /**
     * Reponse au fil de l'eau : les premiers mots arrivent en quelques secondes au lieu d'attendre la
     * fin de la generation. Le filtre de sortie s'applique aussi (StreamingSanitizer retient toute
     * construction ouverte tant qu'elle n'est pas resolue). Annuler l'abonnement (fermeture de la
     * connexion par le navigateur) annule l'appel a OpenAI : on ne paie plus des mots que personne ne lit.
     */
    public Flux<String> stream(String question, CallerContext caller) {
        String clean = TextSanitizer.cleanQuestion(question);
        return Flux.defer(() -> {
            long start = System.nanoTime();
            noteQuestion(clean);
            StreamingSanitizer sanitizer = new StreamingSanitizer(MAX_ANSWER_CHARS);
            return chatClient.prompt()
                    .user(clean)
                    .toolContext(toolContext(caller))
                    .stream()
                    .content()
                    .map(sanitizer::push)
                    .filter(delta -> !delta.isEmpty())
                    .concatWith(Mono.fromSupplier(sanitizer::finish).filter(rest -> !rest.isEmpty()))
                    .doOnComplete(() -> auditQuestion(caller, clean, (System.nanoTime() - start) / 1_000_000));
        });
    }

    private void noteQuestion(String clean) {
        meterRegistry.counter("smartcdc.chat.questions").increment();
        if (PromptGuard.looksSuspicious(clean)) {
            // Suivi des tentatives d'injection reperees (voir PromptGuard) : alerte en cas de pic.
            meterRegistry.counter("smartcdc.chat.suspicious").increment();
        }
    }

    /** Trace d'audit sans contenu : utilisateur, empreinte et taille de la question, duree. */
    private void auditQuestion(CallerContext caller, String clean, long millis) {
        if (PromptGuard.looksSuspicious(clean)) {
            audit.warn("question user={} qHash={} len={} ms={} suspicious=true",
                    caller.user(), AuditHash.of(clean), clean.length(), millis);
        } else {
            audit.info("question user={} qHash={} len={} ms={}", caller.user(), AuditHash.of(clean), clean.length(), millis);
        }
    }

    /**
     * Identite transmise aux outils : le jeton de l'agent (retransmis au backend, qui filtre sur son
     * portefeuille) et son identifiant. Map ordinaire : Map.of refuse les valeurs nulles.
     */
    private static Map<String, Object> toolContext(CallerContext caller) {
        Map<String, Object> context = new HashMap<>();
        if (caller.token() != null) {
            context.put(CallerContext.TOKEN_KEY, caller.token());
        }
        context.put(CallerContext.USER_KEY, caller.user());
        return context;
    }
}
