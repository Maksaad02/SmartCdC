package org.maksaad.recouvchatbot_rag.web;

import org.maksaad.recouvchatbot_rag.services.ChatAiService;
import org.springframework.http.MediaType;
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

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final ChatAiService chatAiService;

    public ChatRestController(ChatAiService chatAiService) {
        this.chatAiService = chatAiService;
    }

    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String ask(@RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("La question ne peut pas être vide");
        }
        if (request.question().length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Question trop longue (max " + MAX_QUESTION_LENGTH + " caractères)");
        }
        return chatAiService.ragChat(request.question());
    }

    public record AskRequest(String question) {}
}
