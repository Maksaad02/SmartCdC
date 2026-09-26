package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.security.PemKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emission et verification des jetons d'acces.
 *
 * Signature ASYMETRIQUE (RS256) : seul le backend detient la cle privee et peut EMETTRE un jeton.
 * Le service chatbot ne recoit que la cle publique, il peut verifier mais pas forger. Avec l'ancien
 * secret HS256 partage, la compromission du chatbot (le composant expose aux injections de prompt
 * et a un fournisseur tiers) donnait le moyen de fabriquer un jeton ADMIN valide pour tout le
 * systeme.
 *
 * Chaque jeton porte un emetteur (iss), des destinataires (aud : l'API et le chatbot, chacun
 * refusant un jeton qui ne lui est pas destine), un identifiant unique (jti) et une duree de vie
 * courte : la session se prolonge par un jeton de renouvellement (voir RefreshTokenService).
 */
@Service
public class JwtUtils {

    public static final String AUDIENCE_API = "smartcdc-api";
    public static final String AUDIENCE_CHAT = "smartcdc-chat";

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Duration accessTtl;
    private final String issuer;

    public JwtUtils(
            @Value("${app.jwt.private-key:}") String privateKeyPem,
            // Cles ephemeres : uniquement pour les tests et le developpement local. En production ce
            // reglage reste a false : une cle absente doit empecher le demarrage, pas etre remplacee
            // par une cle aleatoire qui invaliderait tous les jetons a chaque redemarrage.
            @Value("${app.jwt.ephemeral-keys:false}") boolean ephemeralKeys,
            @Value("${app.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${app.jwt.issuer:smartcdc}") String issuer) {
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            this.privateKey = PemKeys.parsePrivateKey(privateKeyPem);
            this.publicKey = PemKeys.derivePublicKey(privateKey);
        } else if (ephemeralKeys) {
            log.warn("Cles JWT EPHEMERES (app.jwt.ephemeral-keys=true) : ne jamais utiliser en production");
            KeyPair pair = PemKeys.generateEphemeral();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        } else {
            throw new IllegalStateException(
                    "JWT_PRIVATE_KEY (app.jwt.private-key) est obligatoire. Generer une paire avec scripts/generate-jwt-keys.sh");
        }
        this.accessTtl = accessTtl;
        this.issuer = issuer;
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .audience().add(AUDIENCE_API).add(AUDIENCE_CHAT).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Verifie la signature, l'expiration, l'emetteur et le destinataire, puis renvoie les
     * revendications. Toute anomalie leve une JwtException (ou IllegalArgumentException pour un
     * jeton vide).
     *
     * verifyWith(PublicKey) n'accepte que les algorithmes asymetriques : un jeton HS256 "signe" avec
     * la cle publique comme secret (attaque de confusion d'algorithme) ou un jeton sans signature
     * sont refuses.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .requireAudience(AUDIENCE_API)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        return parse(token).getSubject().equals(username);
    }

    /** Duree de vie d'un jeton d'acces, en secondes (annoncee au client pour planifier son renouvellement). */
    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** Cle publique, pour les tests et pour distribuer la cle de verification au chatbot. */
    public PublicKey publicKey() {
        return publicKey;
    }
}
