package com.recouvtech.recouvback.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regles de securite de bout en bout, sur la vraie chaine de filtres :
 * authentification obligatoire, suppression reservee aux ADMIN, cle de service
 * pour l'API chatbot et CORS restreint aux origines configurees.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void uneRequeteSansJetonEstRefuseeEn401() throws Exception {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void unAgentNePeutPasSupprimerUnReglement() throws Exception {
        mvc.perform(delete("/api/reglements/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void unAgentNePeutPasSupprimerUneRelance() throws Exception {
        mvc.perform(delete("/api/relances/1")).andExpect(status().isForbidden());
    }

    /**
     * Hierarchie ADMIN > MANAGER > AGENT : un ADMIN dispose des droits d'un MANAGER, qui a ceux d'un
     * AGENT (hasRole('AGENT') accepte un MANAGER).
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void unAdminPeutSupprimerEtListerLesRoles() throws Exception {
        // Le reglement n'existe pas : 404 prouve que l'acces n'est plus refuse (403).
        mvc.perform(delete("/api/reglements/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/roles")).andExpect(status().isOk());
    }

    /** Un MANAGER gere son departement mais n'administre ni les roles, ni les comptes, ni les departements. */
    @Test
    @WithMockUser(roles = "MANAGER")
    void unManagerNAPasLesDroitsAdmin() throws Exception {
        mvc.perform(get("/api/roles")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/reglements/1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/utilisateurs")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/departements")).andExpect(status().isForbidden());
        mvc.perform(post("/api/departements").contentType("application/json")
                .content("{\"nom\":\"X\",\"code\":\"XX\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void unAgentNAccedeNiAuComparatifNiAuxDepartementsAdmin() throws Exception {
        mvc.perform(get("/api/dashboard/departements")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/departements/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void unAgentNAPasLesDroitsAdmin() throws Exception {
        mvc.perform(get("/api/roles")).andExpect(status().isForbidden());
    }

    @Test
    void lApiChatbotExigeLaCleDeService() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/search").param("query", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lApiChatbotRefuseUneMauvaiseCle() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/search")
                        .param("query", "x")
                        .header("X-RECOUV-KEY", "mauvaise-cle"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lePreflightCorsEstAccepteePourUneOrigineConfiguree() throws Exception {
        mvc.perform(options("/api/clients")
                        .header("Origin", "https://front.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://front.test"));
    }

    @Test
    void lePreflightCorsEstRefusePourUneOrigineInconnue() throws Exception {
        mvc.perform(options("/api/clients")
                        .header("Origin", "https://evil.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
