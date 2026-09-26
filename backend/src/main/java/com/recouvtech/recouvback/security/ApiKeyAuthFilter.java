package com.recouvtech.recouvback.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * API Key Authentication Filter for External Chatbot API
 * Validates the X-RECOUV-KEY header for all /api/external/** endpoints
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final int MIN_KEY_LENGTH = 32;

    // Pas de valeur par defaut : une cle absente ou faible doit empecher le
    // demarrage, pas retomber sur une constante connue de tous.
    @Value("${recouv.api.external.key}")
    private String validApiKey;

    private static final String API_KEY_HEADER = "X-RECOUV-KEY";
    private static final String EXTERNAL_API_PREFIX = "/api/external/";

    @PostConstruct
    void verifierCle() {
        if (validApiKey == null || validApiKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "recouv.api.external.key (CHATBOT_API_KEY) doit contenir au moins "
                            + MIN_KEY_LENGTH + " caracteres");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only check API key for external API endpoints
        if (requestPath.startsWith(EXTERNAL_API_PREFIX)) {
            String apiKey = request.getHeader(API_KEY_HEADER);

            if (!cleValide(apiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        String.format("{\"error\": \"Invalid or missing API Key\", \"timestamp\": \"%s\"}",
                                java.time.LocalDateTime.now()));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Comparaison en temps constant : String.equals fuit la longueur du prefixe commun. */
    private boolean cleValide(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                validApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
