package org.maksaad.recouvchatbot_rag.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * Valide le JWT emis par le backend et memorise le jeton brut pour la duree de
 * la requete.
 *
 * Le service ne disposait d'aucune authentification : /chat/ask etait accessible
 * publiquement et ses outils interrogeaient l'ensemble des clients et creances.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${app.secret-key}")
    private String secretKey;

    private Key signingKey;

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                if (username != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    CallerToken.set(token);
                }
            } catch (JwtException e) {
                // Jeton invalide ou expire : on n'authentifie pas, Spring Security
                // repondra 401. Le jeton lui-meme n'est jamais journalise.
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
