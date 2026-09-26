package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.RefreshTokenRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.RefreshToken;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cycle complet de la session : jeton d'acces court en memoire + cookie de renouvellement httpOnly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final String PASSWORD = "Un-Bon-Mot-De-Passe-2026";
    private static final String COOKIE = "smartcdc_refresh";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartementRepository departementRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------ connexion

    @Test
    void laConnexionRenvoieUnJetonCourtEtPoseUnCookieDeRenouvellementBlinde() throws Exception {
        Utilisateur u = creerUtilisateur();

        MvcResult result = login(u).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(COOKIE + "="));
        assertTrue(setCookie.contains("HttpOnly"), "illisible depuis JavaScript : " + setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertTrue(setCookie.contains("Path=/api/auth"), "envoye seulement aux endpoints d'authentification : " + setCookie);
    }

    @Test
    void seuleLEmpreinteDuJetonEstStockeeJamaisLeJetonLuiMeme() throws Exception {
        Utilisateur u = creerUtilisateur();

        String brut = cookieValue(login(u).andReturn());

        assertEquals(1, refreshTokenRepository.findAll().stream().filter(t -> t.getUserId().equals(u.getIdAgentRecouv())).count());
        boolean brutEnBase = refreshTokenRepository.findAll().stream()
                .anyMatch(t -> t.getTokenHash().equals(brut) || t.getTokenHash().contains(brut));
        assertTrue(!brutEnBase, "le jeton en clair ne doit jamais etre en base");
        assertEquals(64, refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(brut)).orElseThrow().getTokenHash().length());
    }

    @Test
    void leJetonDAccesEmisOuvreLApi() throws Exception {
        Utilisateur u = creerUtilisateur();
        String access = accessToken(login(u).andReturn());

        mvc.perform(get("/api/utilisateurs/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(u.getEmail()));
    }

    // ------------------------------------------------------------ renouvellement

    @Test
    void lRenouvellementDonneUnNouveauJetonEtRemplaceLeCookie() throws Exception {
        Utilisateur u = creerUtilisateur();
        String ancien = cookieValue(login(u).andReturn());

        MvcResult refreshed = refresh(ancien).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();

        String nouveau = cookieValue(refreshed);
        assertNotEquals(ancien, nouveau, "le jeton de renouvellement doit changer a chaque utilisation");
        assertTrue(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(ancien)).orElseThrow().isUsed(),
                "l'ancien jeton est marque comme utilise (echange contre le nouveau)");
    }

    @Test
    void sansEnTeteXRequestedWithLeRenouvellementEstRefuse() throws Exception {
        Utilisateur u = creerUtilisateur();
        String cookie = cookieValue(login(u).andReturn());

        // Un formulaire ou une image d'un autre site ne peut pas ajouter cet en-tete (protection CSRF).
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sansCookieOuAvecUnCookieInconnuLeRenouvellementEchoueEtEffaceLeCookie() throws Exception {
        MvcResult sans = mvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertTrue(sans.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));

        refresh("cookie-forge-qui-nexiste-pas").andExpect(status().isUnauthorized());
    }

    /**
     * Detection de vol : un jeton deja utilise, represente apres la fenetre de tolerance, revoque
     * toute la session -- y compris le jeton legitime obtenu entre-temps.
     */
    @Test
    void rejouerUnJetonDejaUtiliseRevoqueToutLaSession() throws Exception {
        Utilisateur u = creerUtilisateur();
        String premier = cookieValue(login(u).andReturn());
        String deuxieme = cookieValue(refresh(premier).andExpect(status().isOk()).andReturn());
        vieillirUsage(premier); // la rotation date de plus de 10 secondes

        refresh(premier).andExpect(status().isUnauthorized()); // rejeu : l'attaquant

        refresh(deuxieme).andExpect(status().isUnauthorized()); // la victime perd aussi la session
    }

    @Test
    void deuxOngletsQuiRenouvellentEnMemeTempsNeSontPasPrisPourUnVol() throws Exception {
        Utilisateur u = creerUtilisateur();
        String premier = cookieValue(login(u).andReturn());

        refresh(premier).andExpect(status().isOk());
        // Le second onglet presente le meme jeton quelques millisecondes plus tard.
        refresh(premier).andExpect(status().isOk());
    }

    @Test
    void unJetonDeRenouvellementExpireEstRefuse() throws Exception {
        Utilisateur u = creerUtilisateur();
        String cookie = cookieValue(login(u).andReturn());
        RefreshToken t = refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(cookie)).orElseThrow();
        t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(t);

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void unCompteVerrouilleNePeutPasRenouveler() throws Exception {
        Utilisateur u = creerUtilisateur();
        String cookie = cookieValue(login(u).andReturn());
        u.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        utilisateurRepository.save(u);

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ deconnexion et revocation

    @Test
    void laDeconnexionRevoqueLaSessionEtEffaceLeCookie() throws Exception {
        Utilisateur u = creerUtilisateur();
        String cookie = cookieValue(login(u).andReturn());

        MvcResult result = mvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(COOKIE, cookie)).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent()).andReturn();

        assertTrue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void changerLeRoleOuSupprimerLeCompteRevoqueLesSessions() throws Exception {
        Utilisateur u = creerUtilisateur();
        String cookie = cookieValue(login(u).andReturn());

        refreshTokenService.revokeAllForUser(u.getIdAgentRecouv()); // appele par UtilisateurService.updateRole/delete

        // Revocation ferme : aucune fenetre de tolerance, meme immediatement apres.
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ aides

    private org.springframework.test.web.servlet.ResultActions login(Utilisateur u) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(COOKIE, cookie)).header("X-Requested-With", "XMLHttpRequest"));
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header, "Set-Cookie absent");
        return header.substring((COOKIE + "=").length(), header.indexOf(';'));
    }

    private static String accessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
    }

    /** Recule la date d'usage : simule "echange il y a plus de 10 secondes". */
    private void vieillirUsage(String cookie) {
        RefreshToken t = refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(cookie)).orElseThrow();
        t.setUsedAt(LocalDateTime.now().minusMinutes(5));
        refreshTokenRepository.save(t);
    }

    private Utilisateur creerUtilisateur() {
        Role role = roleRepository.findByNom(RoleAgent.AGENT).orElseGet(() -> {
            Role r = new Role();
            r.setNom(RoleAgent.AGENT);
            return roleRepository.save(r);
        });
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Departement departement = departementRepository.save(new Departement("Dept " + code, code));
        Utilisateur u = new Utilisateur();
        u.setNom("Agent Test");
        u.setEmail("agent." + UUID.randomUUID() + "@test");
        u.setMotDePasse(passwordEncoder.encode(PASSWORD));
        u.setRole(role);
        u.setDepartement(departement);
        return utilisateurRepository.save(u);
    }
}
