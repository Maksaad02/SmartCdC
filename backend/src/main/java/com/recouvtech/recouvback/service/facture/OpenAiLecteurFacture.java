package com.recouvtech.recouvback.service.facture;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.recouvtech.recouvback.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Lecture des factures par OpenAI (Chat Completions), en sortie structuree validee par {@link FactureLue}.
 * Sert aussi pour OpenRouter, qui expose la meme API a une autre adresse (voir {@link LecteurFactureConfig}).
 * Memes consignes et memes garanties que {@link ClaudeLecteurFacture} : contenu balise, rien du
 * contenu de la facture n'est journalise.
 */
@Slf4j
public class OpenAiLecteurFacture implements LecteurFacture {

    private final OpenAIClient client;
    private final String modele;
    /** "OpenAI" ou "OpenRouter" : seulement pour les journaux. */
    private final String fournisseur;

    public OpenAiLecteurFacture(String cleApi, String modele) {
        this(cleApi, modele, null, "OpenAI");
    }

    /** @param adresse adresse de l'API compatible OpenAI, ou null pour l'API OpenAI elle-meme */
    public OpenAiLecteurFacture(String cleApi, String modele, String adresse, String fournisseur) {
        this.modele = modele;
        this.fournisseur = fournisseur;
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(cleApi)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(2);
        if (adresse != null) {
            builder.baseUrl(adresse);
        }
        this.client = builder.build();
    }

    @Override
    public boolean isActif() {
        return true;
    }

    @Override
    public FactureLue lireTexte(String texte) {
        return lire(List.of(texteDe(ConsignesFacture.texteBalise(texte))));
    }

    @Override
    public FactureLue lirePdf(byte[] pdf) {
        ChatCompletionContentPart fichier = ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder()
                .file(ChatCompletionContentPart.File.FileObject.builder()
                        .filename("facture.pdf")
                        .fileData("data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf))
                        .build())
                .build());
        return lire(List.of(fichier, texteDe(ConsignesFacture.CONSIGNE)));
    }

    private static ChatCompletionContentPart texteDe(String texte) {
        return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(texte).build());
    }

    private FactureLue lire(List<ChatCompletionContentPart> contenu) {
        StructuredChatCompletionCreateParams<FactureLue> params = ChatCompletionCreateParams.builder()
                .model(modele)
                // Une facture lue tient en ~100 jetons. OpenRouter refuse (402) une requete dont le maximum
                // depasse le credit restant : une borne trop large bloquait des lectures payables.
                .maxCompletionTokens(1500L)
                .addSystemMessage(ConsignesFacture.SYSTEME)
                .responseFormat(FactureLue.class)
                .addUserMessageOfArrayOfContentParts(contenu)
                .build();
        try {
            StructuredChatCompletion<FactureLue> reponse = client.chat().completions().create(params);
            reponse.usage().ifPresent(u -> log.info("Facture lue par {} (requete {}, jetons entree={}, sortie={})",
                    fournisseur, reponse.id(), u.promptTokens(), u.completionTokens()));
            var choix = reponse.choices().stream().findFirst().orElseThrow(OpenAiLecteurFacture::illisible);
            if (!ChatCompletion.Choice.FinishReason.STOP.equals(choix.finishReason())
                    || choix.message().refusal().isPresent()) {
                log.warn("Lecture de facture interrompue (requete {}, fin {})", reponse.id(), choix.finishReason());
                throw illisible();
            }
            return choix.message().content().orElseThrow(OpenAiLecteurFacture::illisible);
        } catch (RateLimitException e) {
            log.warn("Lecture de facture : limite de debit ou quota {} atteint : {}", fournisseur, e.getMessage());
            throw indisponible();
        } catch (OpenAIServiceException e) {
            // Le message d'erreur du fournisseur (cle invalide, credit epuise...) ne contient pas la facture.
            log.error("Lecture de facture : {} a repondu {} : {}", fournisseur, e.statusCode(), e.getMessage());
            throw e.statusCode() >= 500 ? indisponible()
                    : new ExtractionException(HttpStatus.BAD_GATEWAY, "La lecture de la facture a échoué");
        } catch (OpenAIIoException e) {
            log.error("Lecture de facture : {} injoignable ({})", fournisseur, e.getClass().getSimpleName());
            throw indisponible();
        } catch (OpenAIInvalidDataException e) {
            log.warn("Lecture de facture : reponse {} non conforme au schema", fournisseur);
            throw illisible();
        }
    }

    private static ExtractionException illisible() {
        return new ExtractionException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Extraction impossible : saisissez la facture manuellement");
    }

    private static ExtractionException indisponible() {
        return new ExtractionException(HttpStatus.SERVICE_UNAVAILABLE,
                "Service de lecture des factures indisponible, réessayez plus tard");
    }
}
