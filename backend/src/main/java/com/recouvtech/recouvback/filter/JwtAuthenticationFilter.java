package com.recouvtech.recouvback.filter;

import com.recouvtech.recouvback.configuration.JwtUtils;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.security.TenantContext;
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
                String username = jwtUtils.extractUsername(jwtToken);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (jwtUtils.isTokenValid(jwtToken, userDetails.getUsername())) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // Doit etre positionne AVANT la suite de la chaine : Hibernate
                        // lit l'organisation courante a chaque requete cloisonnee.
                        if (userDetails instanceof Utilisateur u && u.getOrganisation() != null) {
                            TenantContext.set(u.getOrganisation().getId());
                        }
                    }
                }
            } catch (JwtException | UsernameNotFoundException e) {
                // Jeton expire, malforme ou signature invalide : on laisse passer sans
                // authentifier, l'AuthenticationEntryPoint repondra 401. Auparavant
                // l'exception remontait et produisait une 500 avec stacktrace.
                // Le jeton lui-meme n'est jamais journalise.
                SecurityContextHolder.clearContext();
                log.debug("Jeton JWT rejeté : {}", e.getClass().getSimpleName());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Les threads sont recycles par le conteneur : sans nettoyage, la
            // requete suivante heriterait de l'organisation de la precedente.
            TenantContext.clear();
        }
    }
}
