package com.recouvtech.recouvback.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP des listes : pagination bornee, forme PagedModel, erreurs client en 400. */
@SpringBootTest
@AutoConfigureMockMvc
class PaginationApiTest {

    @Autowired private MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void laTailleDePageEstPlafonneeA200() throws Exception {
        mvc.perform(get("/api/clients").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(200))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void laTailleParDefautEst20() throws Exception {
        mvc.perform(get("/api/creances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unTriSurUneProprieteSensibleNeProvoquePasDErreur() throws Exception {
        mvc.perform(get("/api/clients").param("sort", "agentRecouv.motDePasse,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unStatutInconnuRenvoie400() throws Exception {
        mvc.perform(get("/api/creances").param("statut", "N_IMPORTE_QUOI"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uneDateInvalideRenvoie400() throws Exception {
        mvc.perform(get("/api/relances").param("dateRelance", "pas-une-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unCheminInconnuRenvoie404EtNonUneErreur500() throws Exception {
        mvc.perform(get("/api/n-existe-pas")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uneRequeteInvalideRenvoie400AvecLesChampsEnErreur() throws Exception {
        mvc.perform(post("/api/creances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numFacture\":\"\",\"montantFacture\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champs.numFacture").exists())
                .andExpect(jsonPath("$.champs.montantFacture").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void leTableauDeBordRepondSansChargerLesCreances() throws Exception {
        mvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreances").value(0))
                .andExpect(jsonPath("$.parStatut.PAYEE").value(0));
    }
}
