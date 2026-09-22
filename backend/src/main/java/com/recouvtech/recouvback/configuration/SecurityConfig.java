package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.filter.JwtAuthenticationFilter;
import com.recouvtech.recouvback.security.ApiKeyAuthFilter;
import com.recouvtech.recouvback.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Laisser passer la redirection interne vers /error.
                        // Sans cela, un refus @PreAuthorize (403) declenche un dispatch
                        // ERROR vers /error, rejoue anonymement : aucune regle ne
                        // correspondant, l'entry point ecrasait le 403 par un 401. Le
                        // front interprete 401 comme une session expiree et deconnectait
                        // l'agent au lieu d'afficher un refus.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()

                        // API chatbot : double controle. ApiKeyAuthFilter verifie la cle de
                        // service, et .authenticated() impose en plus un JWT utilisateur valide,
                        // pour que le chatbot herite exactement des droits de l'agent appelant
                        // au lieu de disposer d'un acces global non cloisonne.
                        .requestMatchers("/api/external/**").authenticated()

                        // Seul le login est public. La creation de compte est reservee aux ADMIN
                        // (voir C-5 : /api/register ouvert + /utilisateurs/{id}/role non protege
                        // permettait a un anonyme de devenir ADMIN en deux requetes).
                        .requestMatchers("/api/login").permitAll()
                        .requestMatchers("/api/register").hasRole("ADMIN")
                        // .requestMatchers(HttpMethod.GET, "/api/reglements/**").permitAll() // ⬅️
                        // Allow GET on reglements
                        // Autoriser les preflight CORS (navigateur)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Toutes les autres requêtes doivent être authentifiées
                        .requestMatchers("/api/**").authenticated()

                        // Deny-by-default sur tout le reste.
                        .anyRequest().authenticated())
                // Renvoyer 401 si non authentifié (au lieu d'un 403 générique)
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(authManager(http))
                // API Key filter BEFORE JWT filter (external API uses different auth)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);

        builder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());

        return builder.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
