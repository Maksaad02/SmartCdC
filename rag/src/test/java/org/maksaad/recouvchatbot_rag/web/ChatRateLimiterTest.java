package org.maksaad.recouvchatbot_rag.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.maksaad.recouvchatbot_rag.web.ChatRateLimiter.RateLimitExceededException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatRateLimiterTest {

    @Test
    void laLimiteParMinuteBloqueLaSixiemeQuestion() {
        ChatRateLimiter limiter = new ChatRateLimiter(5, 100, new SimpleMeterRegistry());

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check("agent@x"));
        }
        RateLimitExceededException e = assertThrows(RateLimitExceededException.class, () -> limiter.check("agent@x"));
        assertEquals(60, e.getRetryAfterSeconds());
    }

    @Test
    void laLimiteHoraireProtegeDUnUsageSoutenu() {
        ChatRateLimiter limiter = new ChatRateLimiter(1000, 3, new SimpleMeterRegistry());

        for (int i = 0; i < 3; i++) {
            limiter.check("agent@x");
        }
        RateLimitExceededException e = assertThrows(RateLimitExceededException.class, () -> limiter.check("agent@x"));
        assertEquals(3600, e.getRetryAfterSeconds());
    }

    @Test
    void lesUtilisateursNeSePartagentPasLeQuota() {
        ChatRateLimiter limiter = new ChatRateLimiter(1, 10, new SimpleMeterRegistry());

        limiter.check("a@x");
        assertDoesNotThrow(() -> limiter.check("b@x"));
    }
}
