package org.maksaad.recouvchatbot_rag.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${recouv.frontend.origin}")
    private String frontendOrigin;

    /** Port d'administration (Prometheus), interne au reseau Docker et jamais publie ; -1 = non configure. */
    @Value("${management.server.port:-1}")
    private int managementPort;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Reponse en flux (SSE) : une fois la requete autorisee, le conteneur la reprend en
                        // "dispatch ASYNC" pour ecrire la fin de la reponse, SANS contexte de securite
                        // (session STATELESS). Sans cette regle, ce second passage etait refuse et la
                        // reponse deja commencee echouait. Un client externe ne peut pas provoquer un
                        // dispatch ASYNC ou ERROR : ils ne suivent qu'une requete deja autorisee.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Sondes de sante (Docker, orchestrateur), sans detail.
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Metriques : ouvertes UNIQUEMENT sur le port d'administration interne.
                        .requestMatchers(request -> managementPort > 0
                                && request.getLocalPort() == managementPort
                                && request.getRequestURI().startsWith("/actuator/")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Origine explicite : @CrossOrigin(origins = "*") permettait a n'importe
        // quel site d'interroger le chatbot depuis le navigateur d'un visiteur.
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
