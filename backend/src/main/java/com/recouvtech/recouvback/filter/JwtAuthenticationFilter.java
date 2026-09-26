package com.recouvtech.recouvback.filter;

import com.recouvtech.recouvback.configuration.JwtUtils;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            try {
                // Une seule verification : signature, expiration, emetteur et destinataire.
                String username = jwtUtils.parse(jwtToken).getSubject();

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (username.equals(userDetails.getUsername())) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        // Le departement du principal alimente le filtre SQL de chaque transaction
                        // (DepartementFilterTransactionManager) : rien d'autre a positionner ici.
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                // IllegalArgumentException : jjwt le leve pour un jeton vide ("Authorization: Bearer "),
                // qui provoquait auparavant une erreur 500 declenchable par n'importe qui.
                // Jeton expire, malforme ou signature invalide : on laisse passer sans
                // authentifier, l'AuthenticationEntryPoint repondra 401. Auparavant
                // l'exception remontait et produisait une 500 avec stacktrace.
                // Le jeton lui-meme n'est jamais journalise.
                SecurityContextHolder.clearContext();
                log.debug("Jeton JWT rejeté : {}", e.getClass().getSimpleName());
            }
        }

        filterChain.doFilter(request, response);
    }
}
