package org.maksaad.recouvchatbot_rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecouvChatbotRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecouvChatbotRagApplication.class, args);
    }

}
