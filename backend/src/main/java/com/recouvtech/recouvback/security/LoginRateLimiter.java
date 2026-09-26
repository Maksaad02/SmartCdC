package com.recouvtech.recouvback.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite les ECHECS de connexion, par identifiant ET par adresse IP.
 *
 * Sans limite, /api/login etait une machine a deviner des mots de passe. Deux compteurs car ils
 * couvrent des attaques differentes : par identifiant contre l'attaque ciblee menee depuis de nombreuses
 * adresses, par IP contre le "password spraying" (un mot de passe courant essaye sur beaucoup de comptes).
 *
 * Seuls les echecs comptent : une connexion reussie ne consomme aucun quota (un utilisateur legitime, un
 * compte partage ou un test automatise peuvent se connecter autant que necessaire), et remet a zero le
 * compteur de l'identifiant.
 *
 * Fenetre fixe d'une minute, en memoire : suffisant pour une instance unique. Si le backend est un jour
 * replique, ces compteurs devront vivre dans un magasin partage (Redis), faute de quoi chaque replique
 * aurait ses propres limites.
 */
@Component
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int perIp;
    private final int perUser;
    private final Cache<String, AtomicInteger> ipFailures = newCache();
    private final Cache<String, AtomicInteger> userFailures = newCache();

    public LoginRateLimiter(
            @Value("${app.security.login.ip-per-minute:20}") int perIp,
            @Value("${app.security.login.user-per-minute:5}") int perUser) {
        this.perIp = perIp;
        this.perUser = perUser;
    }

    /** true si l'identifiant ou l'adresse a atteint sa limite d'echecs : la tentative est refusee sans etre evaluee. */
    public boolean isBlocked(String ip, String username) {
        return count(ipFailures, ip(ip)) >= perIp || count(userFailures, user(username)) >= perUser;
    }

    public void recordFailure(String ip, String username) {
        ipFailures.get(ip(ip), k -> new AtomicInteger()).incrementAndGet();
        userFailures.get(user(username), k -> new AtomicInteger()).incrementAndGet();
    }

    /** Connexion reussie : les echecs precedents de cet identifiant sont oublies. */
    public void recordSuccess(String username) {
        userFailures.invalidate(user(username));
    }

    /** Duree a attendre, en secondes, annoncee dans l'en-tete Retry-After. */
    public long retryAfterSeconds() {
        return WINDOW.toSeconds();
    }

    private static int count(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger counter = cache.getIfPresent(key);
        return counter == null ? 0 : counter.get();
    }

    private static String ip(String ip) {
        return ip == null ? "?" : ip;
    }

    /** L'identifiant est normalise : "Admin@x" et "admin@x" ne doivent pas avoir chacun leur quota. */
    private static String user(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static Cache<String, AtomicInteger> newCache() {
        // Borne en taille : un attaquant qui varie l'identifiant ne doit pas pouvoir remplir la memoire.
        return Caffeine.newBuilder().expireAfterWrite(WINDOW).maximumSize(50_000).build();
    }
}
