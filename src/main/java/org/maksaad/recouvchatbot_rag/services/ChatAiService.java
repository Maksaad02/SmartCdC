package org.maksaad.recouvchatbot_rag.services;

import org.maksaad.recouvchatbot_rag.config.SystemPrompts;
import org.maksaad.recouvchatbot_rag.tools.SqlTool;  // Add this import
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class ChatAiService {

    private final ChatClient chatClient;

    public ChatAiService(ChatClient.Builder builder,
                         VectorStore vectorStore,
                         SqlTool sqlTool) {  // Add SqlTool parameter
        this.chatClient = builder
                .defaultSystem(SystemPrompts.DATABASE_SCHEMA)
                .defaultTools(sqlTool)  // Pass the actual object, not the string name
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    public String ragChat(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}