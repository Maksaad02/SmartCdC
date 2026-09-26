package org.maksaad.recouvchatbot_rag.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plafonne le nombre de questions par utilisateur.
 *
 * Chaque question appelle gpt-4o (cout reel) et peut declencher des outils : sans plafond,
 * un seul compte, ou un jeton vole, pouvait faire exploser la facture OpenAI. Deux fenetres,
 * par minute (rafale) et par heure (usage soutenu).
 *
 * Compteurs en memoire : suffisant pour une instance unique ; en cas de replication, il faudra
 * un magasin partage (Redis), sinon chaque instance appliquerait sa propre limite.
 */
@Component
public class ChatRateLimiter {

    private final int perMinute;
    private final int perHour;
    private final Cache<String, AtomicInteger> minuteCounters = cache(Duration.ofMinutes(1));
    private final Cache<String, AtomicInteger> hourCounters = cache(Duration.ofHours(1));

    private final MeterRegistry meterRegistry;

    public ChatRateLimiter(
            @Value("${app.rag.chat.per-minute:5}") int perMinute,
            @Value("${app.rag.chat.per-hour:60}") int perHour,
            MeterRegistry meterRegistry) {
        this.perMinute = perMinute;
        this.perHour = perHour;
        this.meterRegistry = meterRegistry;
    }

    /** Leve RateLimitExceededException si l'utilisateur a depasse l'une des deux limites. */
    public void check(String user) {
        int minute = minuteCounters.get(user, k -> new AtomicInteger()).incrementAndGet();
        int hour = hourCounters.get(user, k -> new AtomicInteger()).incrementAndGet();
        if (minute > perMinute) {
            meterRegistry.counter("smartcdc.chat.throttled", "window", "minute").increment();
            throw new RateLimitExceededException(60);
        }
        if (hour > perHour) {
            meterRegistry.counter("smartcdc.chat.throttled", "window", "hour").increment();
            throw new RateLimitExceededException(3600);
        }
    }

    private static Cache<String, AtomicInteger> cache(Duration window) {
        return Caffeine.newBuilder().expireAfterWrite(window).maximumSize(10_000).build();
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitExceededException(long retryAfterSeconds) {
            super("Trop de questions");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
