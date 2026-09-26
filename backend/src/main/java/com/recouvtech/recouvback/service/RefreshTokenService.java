package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RefreshTokenRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.RefreshToken;
import com.recouvtech.recouvback.entity.Utilisateur;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cycle de vie des jetons de renouvellement.
 *
 *  - emission a la connexion ;
 *  - ROTATION a chaque renouvellement : le jeton presente est revoque et remplace ;
 *  - detection de rejeu : presenter un jeton deja utilise revoque toute la famille (l'attaquant
 *    et la victime perdent la session, la victime se reconnecte) ;
 *  - revocation par utilisateur (changement de role, suppression de compte).
 *
 * Le jeton en clair n'existe que dans le cookie du navigateur : la base ne contient que son
 * empreinte SHA-256.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Deux onglets qui renouvellent en meme temps presentent le meme jeton : le second arrive apres
     * la rotation du premier. Dans cette courte fenetre on ne le traite pas comme un vol.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    /** Jeton en clair (a poser dans le cookie) et duree de vie. */
    public record Issued(String token, Duration ttl) {}

    /** Utilisateur authentifie et nouveau jeton de renouvellement. */
    public record Rotation(Utilisateur user, Issued refresh) {}

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    private final RefreshTokenRepository tokens;
    private final UtilisateurRepository users;
    private final MeterRegistry meterRegistry;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository tokens, UtilisateurRepository users, MeterRegistry meterRegistry,
                               @Value("${app.jwt.refresh-ttl:PT12H}") Duration ttl) {
        this.tokens = tokens;
        this.users = users;
        this.meterRegistry = meterRegistry;
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Nouvelle session (connexion) : nouvelle famille de jetons. */
    @Transactional
    public Issued issue(Utilisateur user) {
        return create(user.getIdAgentRecouv(), UUID.randomUUID().toString());
    }

    /**
     * Echange un jeton de renouvellement contre un nouveau. noRollbackFor : la revocation d'une famille
     * detectee comme rejouee doit etre ENREGISTREE malgre l'exception qui suit.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Jeton absent");
        }
        RefreshToken current = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Jeton inconnu"));
        LocalDateTime now = LocalDateTime.now();

        // Revocation ferme (deconnexion, changement de role, vol detecte) : aucun usage, aucune tolerance.
        if (current.isRevoked()) {
            throw new InvalidRefreshTokenException("Jeton revoque");
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException("Jeton expire");
        }
        // Deja echange contre un successeur : normal si un autre onglet vient de le faire (fenetre
        // de tolerance), sinon c'est un rejeu -- le jeton a probablement ete copie -- et toute la
        // session est revoquee.
        if (current.isUsed() && !current.getUsedAt().isAfter(now.minus(REUSE_GRACE))) {
            tokens.revokeFamily(current.getFamilyId(), now);
            meterRegistry.counter("smartcdc.refresh.reuse.detected").increment();
            log.warn("Rejeu d'un jeton de renouvellement deja utilise : session {} entierement revoquee", current.getFamilyId());
            throw new InvalidRefreshTokenException("Jeton deja utilise");
        }

        Utilisateur user = users.findById(current.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Compte supprime"));
        if (!user.isAccountNonLocked()) {
            throw new InvalidRefreshTokenException("Compte verrouille");
        }

        if (!current.isUsed()) {
            current.setUsedAt(now);
        }
        return new Rotation(user, create(current.getUserId(), current.getFamilyId()));
    }

    /** Deconnexion : revoque la session (toute la famille) correspondant a ce jeton, si elle existe. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByTokenHash(hash(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), LocalDateTime.now()));
    }

    /** Toutes les sessions d'un utilisateur : a appeler quand ses droits ou son compte changent. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        int n = tokens.revokeAllForUser(userId, LocalDateTime.now());
        if (n > 0) {
            log.info("{} session(s) revoquee(s) pour l'utilisateur {}", n, userId);
        }
    }

    /** Les jetons expires ne servent plus a rien : purge quotidienne. */
    @Scheduled(cron = "0 15 3 * * *", zone = "${app.penalties.zone:Africa/Casablanca}")
    @Transactional
    public void purgeExpired() {
        int n = tokens.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        log.info("{} jeton(s) de renouvellement expiré(s) purgé(s)", n);
    }

    private Issued create(Long userId, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        tokens.save(token);
        return new Issued(raw, ttl);
    }

    /** Empreinte SHA-256 (hex) d'un jeton : ce qui est stocke en base. */
    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
