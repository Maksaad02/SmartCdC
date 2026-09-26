package com.recouvtech.recouvback.filter;

import com.recouvtech.recouvback.configuration.JwtUtils;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.security.PemKeys;
import com.recouvtech.recouvback.service.CustomUserDetailsService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le filtre porte deux garanties de securite : n'authentifier que sur un jeton valide, et ne
 * jamais laisser l'identite d'une requete a la suivante (les threads sont reutilises).
 */
class JwtAuthenticationFilterTest {

    /** Cles RSA de ce test uniquement : deux paires, pour tester un jeton signe par un autre emetteur. */
    private static final KeyPair KEYS = PemKeys.generateEphemeral();
    private static final KeyPair OTHER_KEYS = PemKeys.generateEphemeral();

    private JwtUtils jwtUtils;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private Utilisateur agent;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()),
                false, Duration.ofMinutes(15), "smartcdc");

        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtUtils, userDetailsService);

        Departement departement = new Departement("Casablanca", "CASA");
        departement.setId(7L);
        Role role = new Role();
        role.setNom(RoleAgent.AGENT);
        agent = new Utilisateur();
        agent.setEmail("agent@cabinet.test");
        agent.setRole(role);
        agent.setDepartement(departement);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private record Observed(Authentication auth) {}

    /** Execute la requete et renvoie ce que la suite de la chaine a VU pendant son execution. */
    private Observed run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        AtomicReference<Observed> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen.set(new Observed(SecurityContextHolder.getContext().getAuthentication())));
        return seen.get();
    }

    private String token(String subject, KeyPair signer, long expiresInMs) {
        return Jwts.builder()
                .subject(subject)
                .issuer("smartcdc")
                .audience().add(JwtUtils.AUDIENCE_API).add(JwtUtils.AUDIENCE_CHAT).and()
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(signer.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    void unJetonValideAuthentifieAvecLePrincipalPortantSonDepartement() throws Exception {
        when(userDetailsService.loadUserByUsername("agent@cabinet.test")).thenReturn(agent);

        Observed seen = run("Bearer " + token("agent@cabinet.test", KEYS, 60_000));

        assertNotNull(seen.auth());
        assertEquals("agent@cabinet.test", seen.auth().getName());
        // Le departement du principal alimente le filtre SQL de chaque transaction.
        assertEquals(7L, ((Utilisateur) seen.auth().getPrincipal()).getDepartement().getId());
    }

    @Test
    void sansJetonAucuneAuthentification() throws Exception {
        Observed seen = run(null);

        assertNull(seen.auth());
    }

    @Test
    void unJetonSigneAvecUneAutreCleEstIgnore() throws Exception {
        Observed seen = run("Bearer " + token("agent@cabinet.test", OTHER_KEYS, 60_000));

        assertNull(seen.auth());
    }

    @Test
    void unJetonExpireEstIgnoreSansErreur() throws Exception {
        Observed seen = run("Bearer " + token("agent@cabinet.test", KEYS, -1_000));

        assertNull(seen.auth());
    }

    @Test
    void unJetonMalFormeEstIgnoreSansErreur() throws Exception {
        assertNull(run("Bearer pas-un-jwt").auth());
        assertNull(run("Bearer ").auth());
        assertNull(run("Basic dXNlcjpwYXNz").auth());
    }

    @Test
    void unJetonValideDUnCompteSupprimeEstIgnore() throws Exception {
        when(userDetailsService.loadUserByUsername("fantome@cabinet.test"))
                .thenThrow(new UsernameNotFoundException("supprime"));

        Observed seen = run("Bearer " + token("fantome@cabinet.test", KEYS, 60_000));

        assertNull(seen.auth(), "un compte supprime ne doit pas rester utilisable jusqu'a l'expiration du jeton");
    }
}
