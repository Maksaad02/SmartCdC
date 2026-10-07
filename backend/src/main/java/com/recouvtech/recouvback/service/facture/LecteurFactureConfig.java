package com.recouvtech.recouvback.service.facture;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Choix du fournisseur d'IA qui lit les factures, selon les cles configurees, dans cet ordre :
 * Claude (ANTHROPIC_API_KEY), OpenRouter (OPENROUTER_API_KEY), OpenAI (OPENAI_API_KEY). Sans aucune
 * cle, la fonctionnalite est desactivee (le bouton d'import est masque).
 *
 * OpenAI passe en dernier : OPENAI_API_KEY sert d'abord au chatbot et peut etre renseignee sans
 * intention d'utiliser OpenAI pour les factures ; une cle dediee exprime ce choix.
 */
@Configuration
@Slf4j
public class LecteurFactureConfig {

    static final String ADRESSE_OPENROUTER = "https://openrouter.ai/api/v1";

    @Bean
    public LecteurFacture lecteurFacture(@Value("${app.anthropic.api-key:}") String cleAnthropic,
                                         @Value("${app.openrouter.api-key:}") String cleOpenRouter,
                                         @Value("${app.openai.api-key:}") String cleOpenAi,
                                         @Value("${app.extraction.modele-claude:claude-opus-5-5}") String modeleClaude,
                                         @Value("${app.extraction.modele-openrouter:openai/gpt-4o}") String modeleOpenRouter,
                                         @Value("${app.extraction.modele-openai:gpt-4o}") String modeleOpenAi) {
        if (!cleAnthropic.isBlank()) {
            log.info("Import des factures PDF : Claude ({})", modeleClaude);
            return new ClaudeLecteurFacture(cleAnthropic, modeleClaude);
        }
        if (!cleOpenRouter.isBlank()) {
            log.info("Import des factures PDF : OpenRouter ({})", modeleOpenRouter);
            return new OpenAiLecteurFacture(cleOpenRouter, modeleOpenRouter, ADRESSE_OPENROUTER, "OpenRouter");
        }
        if (!cleOpenAi.isBlank()) {
            log.info("Import des factures PDF : OpenAI ({})", modeleOpenAi);
            return new OpenAiLecteurFacture(cleOpenAi, modeleOpenAi);
        }
        log.info("Import des factures PDF desactive : aucune cle d'API configuree");
        return new ClaudeLecteurFacture("", modeleClaude);
    }
}
