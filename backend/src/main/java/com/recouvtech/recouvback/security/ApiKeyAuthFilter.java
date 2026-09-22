package com.recouvtech.recouvback.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key Authentication Filter for External Chatbot API
 * Validates the X-RECOUV-KEY header for all /api/external/** endpoints
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    @Value("${recouv.api.external.key:DEFAULT_INSECURE_KEY}")
    private String validApiKey;

    private static final String API_KEY_HEADER = "X-RECOUV-KEY";
    private static final String EXTERNAL_API_PREFIX = "/api/external/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only check API key for external API endpoints
        if (requestPath.startsWith(EXTERNAL_API_PREFIX)) {
            String apiKey = request.getHeader(API_KEY_HEADER);

            if (apiKey == null || !apiKey.equals(validApiKey)) {
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
}
