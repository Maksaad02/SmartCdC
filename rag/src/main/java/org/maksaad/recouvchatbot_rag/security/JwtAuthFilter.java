package org.maksaad.recouvchatbot_rag.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;

/**
 * Valide le JWT emis par le backend et memorise le jeton brut pour la duree de la requete.
 *
 * Verification par CLE PUBLIQUE (RS256) : ce service peut verifier un jeton mais n'a aucun moyen d'en
 * fabriquer un. Avec l'ancien secret HS256 partage avec le backend, la compromission du chatbot --
 * le composant expose aux injections de prompt et a un fournisseur d'IA tiers -- aurait permis de
 * forger un jeton SUPER_ADMIN valable pour toute la plateforme.
 *
 * Le jeton doit avoir ete emis pour ce service (aud = smartcdc-chat) par l'emetteur attendu.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUDIENCE = "smartcdc-chat";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${app.jwt.public-key}")
    private String publicKeyPem;

    @Value("${app.jwt.issuer:smartcdc}")
    private String issuer;

    private PublicKey publicKey;

    @PostConstruct
    void init() {
        this.publicKey = PemPublicKey.parse(publicKeyPem);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .requireIssuer(issuer)
                        .requireAudience(AUDIENCE)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String username = claims.getSubject();
                if (username != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    CallerToken.set(token);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Jeton invalide, expire, vide ou destine a un autre service : on n'authentifie pas,
                // Spring Security repondra 401. IllegalArgumentException : jjwt le leve pour un jeton
                // vide ("Authorization: Bearer "), ce qui provoquait une erreur 500. Le jeton lui-meme
                // n'est jamais journalise.
                SecurityContextHolder.clearContext();
                log.debug("Jeton JWT rejeté : {}", e.getClass().getSimpleName());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Indispensable : les threads sont recycles par le conteneur.
            CallerToken.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
