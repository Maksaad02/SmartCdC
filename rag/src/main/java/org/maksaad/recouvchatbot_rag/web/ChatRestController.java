package org.maksaad.recouvchatbot_rag.web;

import org.maksaad.recouvchatbot_rag.services.ChatAiService;
import org.springframework.http.MediaType;
import org.maksaad.recouvchatbot_rag.security.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entree du chatbot.
 *
 * POST et non GET : la question transitait en parametre d'URL et se retrouvait
 * donc dans les journaux d'acces, l'historique du navigateur et l'en-tete
 * Referer, alors qu'elle porte des donnees de recouvrement.
 *
 * L'authentification est imposee par SecurityConfig ; il n'y a plus de
 * @CrossOrigin(origins = "*") ici.
 */
@RestController
@RequestMapping("/chat")
public class ChatRestController {

    private static final Logger log = LoggerFactory.getLogger(ChatRestController.class);
    private static final int MAX_QUESTION_LENGTH = 2000;

    private final ChatAiService chatAiService;
    private final ChatRateLimiter rateLimiter;

    public ChatRestController(ChatAiService chatAiService, ChatRateLimiter rateLimiter) {
        this.chatAiService = chatAiService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String ask(@RequestBody AskRequest request) {
        CallerContext caller = accept(request);
        return chatAiService.ragChat(request.question(), caller);
    }

    /**
     * Meme question, reponse en flux (Server-Sent Events) : les premiers mots s'affichent des qu'ils
     * sont generes. Evenements : "message" (texte a ajouter), "error" (echec en cours de route) et
     * "done" (fin). Memes controles que /ask : authentification, taille, plafond de questions.
     */
    @PostMapping(value = "/ask/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            // UTF-8 explicite : sans charset, certains clients decodent le flux en ISO-8859-1 (accents brises).
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> askStream(@RequestBody AskRequest request) {
        CallerContext caller = accept(request);
        return chatAiService.stream(request.question(), caller)
                .map(delta -> ServerSentEvent.builder(delta).event("message").build())
                // Une erreur en cours de flux ne peut plus changer le code HTTP (200 deja envoye) :
                // elle est signalee par un evenement, sans detail technique.
                .onErrorResume(error -> {
                    log.error("Échec du flux de réponse", error);
                    return Flux.just(ServerSentEvent.<String>builder("Le service d'IA est momentanément indisponible.")
                            .event("error").build());
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder("").event("done").build()));
    }

    /** Validation et plafond de questions, communs aux deux endpoints, AVANT tout appel a OpenAI. */
    private CallerContext accept(AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("La question ne peut pas être vide");
        }
        if (request.question().length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Question trop longue (max " + MAX_QUESTION_LENGTH + " caractères)");
        }
        CallerContext caller = CallerContext.current();
        rateLimiter.check(caller.user());
        return caller;
    }

    public record AskRequest(String question) {}
}
