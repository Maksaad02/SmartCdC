package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.service.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Protection de /api/login contre le brute force : limiteur par minute et verrouillage de compte. */
@SpringBootTest
@AutoConfigureMockMvc
class LoginProtectionTest {

    private static final String MOT_DE_PASSE = "Un-Bon-Mot-De-Passe-2026";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartementRepository departementRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttempts;

    @Test
    void lesTentativesRepeteesSurUnMemeCompteSontLimiteesEn429() throws Exception {
        String email = "inconnu." + UUID.randomUUID() + "@test";

        for (int i = 0; i < 5; i++) {
            login(email, "mauvais").andExpect(status().isUnauthorized());
        }

        login(email, "mauvais")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void unBonMotDePasseFonctionneEtRemetLesEchecsAZero() throws Exception {
        Utilisateur u = creerUtilisateur();
        loginAttempts.recordFailure(u.getEmail());
        loginAttempts.recordFailure(u.getEmail());
        assertEquals(2, recharger(u).getFailedAttempts());

        login(u.getEmail(), MOT_DE_PASSE).andExpect(status().isOk());

        assertEquals(0, recharger(u).getFailedAttempts());
    }

    @Test
    void ApresDixEchecsLeCompteEstVerrouilleMemeAvecLeBonMotDePasse() throws Exception {
        Utilisateur u = creerUtilisateur();
        for (int i = 0; i < 10; i++) {
            loginAttempts.recordFailure(u.getEmail());
        }

        assertNotNull(recharger(u).getLockedUntil(), "le compte doit etre verrouille");
        login(u.getEmail(), MOT_DE_PASSE).andExpect(status().isTooManyRequests());
    }

    @Test
    void leVerrouillageExpireEtLeCompteRedevientUtilisable() throws Exception {
        Utilisateur u = creerUtilisateur();
        u.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // verrou echu
        utilisateurRepository.save(u);

        login(u.getEmail(), MOT_DE_PASSE).andExpect(status().isOk());

        assertNull(recharger(u).getLockedUntil());
    }

    @Test
    void unCorpsDeRequeteMalFormeRenvoie400() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lesRegistresDeVerrouillageNeChangentPasLeMessageDErreur() throws Exception {
        // Un compte inconnu et un mauvais mot de passe repondent pareil (pas d'enumeration).
        Utilisateur u = creerUtilisateur();
        String reponseInconnu = login("inexistant." + UUID.randomUUID() + "@test", "x")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String reponseMauvaisMdp = login(u.getEmail(), "x")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertEquals(reponseInconnu, reponseMauvaisMdp);
        assertTrue(reponseInconnu.contains("Identifiants incorrects"));
    }

    // ------------------------------------------------------------------ aides

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private Utilisateur recharger(Utilisateur u) {
        return utilisateurRepository.findById(u.getIdAgentRecouv()).orElseThrow();
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
        u.setMotDePasse(passwordEncoder.encode(MOT_DE_PASSE));
        u.setRole(role);
        u.setDepartement(departement);
        return utilisateurRepository.save(u);
    }
}
