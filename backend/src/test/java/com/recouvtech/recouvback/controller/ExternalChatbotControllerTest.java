package com.recouvtech.recouvback.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API consommee par le chatbot. Deux controles s'appliquent : la cle de service ET un jeton
 * utilisateur. Un client ou une creance inexistants ou hors portefeuille repondent 404/403 --
 * jamais une liste vide, que le chatbot presentait comme "aucun client".
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExternalChatbotControllerTest {

    private static final String KEY = "test-only-external-api-key-0123456789abcdef";

    @Autowired private MockMvc mvc;

    @Test
    @WithMockUser(roles = "AGENT")
    void uneRechercheSansResultatRenvoieUneListeVide() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/search").param("query", "introuvable")
                        .header("X-RECOUV-KEY", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void uneRechercheVideRenvoie400() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/search").param("query", "  ")
                        .header("X-RECOUV-KEY", KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void unClientInexistantRenvoie404() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/999999").header("X-RECOUV-KEY", KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void lesCreancesDUnClientInexistantRenvoient404EtNonUneListeVide() throws Exception {
        mvc.perform(get("/api/external/chatbot/clients/999999/creances").header("X-RECOUV-KEY", KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void lesImpayesAcceptentUneLimiteEtRefusentUneValeurInvalide() throws Exception {
        mvc.perform(get("/api/external/chatbot/creances/impayees").param("limit", "10")
                        .header("X-RECOUV-KEY", KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/external/chatbot/creances/impayees").param("limit", "beaucoup")
                        .header("X-RECOUV-KEY", KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sansJetonUtilisateurLaCleDeServiceSeuleNeSuffitPas() throws Exception {
        mvc.perform(get("/api/external/chatbot/creances/impayees").header("X-RECOUV-KEY", KEY))
                .andExpect(status().isUnauthorized());
    }
}
