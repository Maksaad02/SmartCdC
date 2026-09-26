package com.recouvtech.recouvback.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Le PasswordEncoder vit dans sa propre configuration : il est injecte par
 * SecurityConfig (via l'AuthenticationManager) et par les services, et le
 * declarer dans SecurityConfig creerait une dependance circulaire des que
 * l'un de ces services est lui-meme requis par la chaine de securite.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
