package org.maksaad.recouvchatbot_rag.web;

import org.maksaad.recouvchatbot_rag.services.ChatAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.awt.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/chat")
public class ChatRestController {
    private ChatAiService chatAiService;

    public ChatRestController(ChatAiService chatAiService) {
        this.chatAiService = chatAiService;
    }

    @GetMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ask(String question){
        return chatAiService.ragChat(question);
    }
}
