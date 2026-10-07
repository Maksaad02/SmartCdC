package com.recouvtech.recouvback.service.facture;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LecteurFactureConfigTest {

    private final LecteurFactureConfig config = new LecteurFactureConfig();

    private LecteurFacture lire(String anthropic, String openRouter, String openAi) {
        return config.lecteurFacture(anthropic, openRouter, openAi, "claude-opus-5-5", "openai/gpt-4o", "gpt-4o");
    }

    @Test
    void claudeEstPrefereQuandLesDeuxClesSontPresentes() {
        LecteurFacture lecteur = lire("sk-ant-test", "sk-or-test", "sk-proj-test");

        assertInstanceOf(ClaudeLecteurFacture.class, lecteur);
        assertTrue(lecteur.isActif());
    }

    @Test
    void openRouterPasseAvantOpenAi() {
        LecteurFacture lecteur = lire("", "sk-or-test", "sk-proj-test");

        assertInstanceOf(OpenAiLecteurFacture.class, lecteur);
        assertTrue(lecteur.isActif());
    }

    @Test
    void openAiEstUtiliseSansCleDediee() {
        LecteurFacture lecteur = lire("", "", "sk-proj-test");

        assertInstanceOf(OpenAiLecteurFacture.class, lecteur);
        assertTrue(lecteur.isActif());
    }

    @Test
    void sansAucuneCleLaLectureEstDesactivee() {
        assertFalse(lire("", "", " ").isActif());
    }

    /** Le schema JSON est derive de FactureLue et valide localement par le SDK OpenAI a la construction. */
    @Test
    void leSchemaDeSortieStructureeEstAccepteParLeSdkOpenAi() {
        assertDoesNotThrow(() -> ChatCompletionCreateParams.builder()
                .model("gpt-4o")
                .responseFormat(FactureLue.class)
                .addUserMessage("test")
                .build());
    }
}
