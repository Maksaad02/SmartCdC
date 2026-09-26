package org.maksaad.recouvchatbot_rag.web;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.maksaad.recouvchatbot_rag.security.JwtAuthFilter;
import org.maksaad.recouvchatbot_rag.security.SecurityConfig;
import org.maksaad.recouvchatbot_rag.services.ChatAiService;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRestController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, ApiExceptionHandler.class, ChatRateLimiter.class,
        ChatRestControllerTest.Metrics.class})
@TestPropertySource(properties = {
        "app.jwt.issuer=smartcdc",
        "recouv.frontend.origin=https://front.test",
        "app.rag.chat.per-minute=2",
        "app.rag.chat.per-hour=100"})
class ChatRestControllerTest {

    /** @WebMvcTest ne charge pas la configuration des metriques : un registre en memoire suffit. */
    @org.springframework.boot.test.context.TestConfiguration
    static class Metrics {
        @org.springframework.context.annotation.Bean
        io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }

    /** Paires generees pour ce test : le chatbot ne recoit que la cle PUBLIQUE de KEYS. */
    private static final KeyPair KEYS = newKeyPair();
    private static final KeyPair OTHER_KEYS = newKeyPair();

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private ChatAiService chatAiService;

    private static String token(String subject, KeyPair signer, long expiresInMs) {
        return token(subject, signer, expiresInMs, "smartcdc", "smartcdc-api", "smartcdc-chat");
    }

    private static String token(String subject, KeyPair signer, long expiresInMs, String issuer, String... audiences) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(signer.getPrivate(), Jwts.SIG.RS256);
        var audience = builder.audience();
        for (String a : audiences) {
            audience.add(a);
        }
        return audience.and().compact();
    }

    /** Chaque test utilise son propre utilisateur : les compteurs du limiteur sont partages. */
    private static String freshUser() {
        return "agent." + UUID.randomUUID() + "@test";
    }

    private org.springframework.test.web.servlet.ResultActions ask(String bearer, String json) throws Exception {
        var request = post("/chat/ask").contentType(MediaType.APPLICATION_JSON).content(json);
        if (bearer != null) {
            request = request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request);
    }

    @Test
    void sansJetonLeChatbotEstRefuse() throws Exception {
        ask(null, "{\"question\":\"Bonjour\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonVideOuMalFormeEstRefuseEn401EtNonEn500() throws Exception {
        ask("", "{\"question\":\"Bonjour\"}").andExpect(status().isUnauthorized());
        ask("pas-un-jwt", "{\"question\":\"Bonjour\"}").andExpect(status().isUnauthorized());
    }

    /** Un jeton destine a l'API seule (aud) n'ouvre pas le chatbot, et inversement. */
    @Test
    void unJetonNonDestineAuChatbotEstRefuse() throws Exception {
        ask(token(freshUser(), KEYS, 60_000, "smartcdc", "smartcdc-api"), "{\"question\":\"Bonjour\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonDUnAutreEmetteurEstRefuse() throws Exception {
        ask(token(freshUser(), KEYS, 60_000, "un-autre-systeme", "smartcdc-chat"), "{\"question\":\"Bonjour\"}")
                .andExpect(status().isUnauthorized());
    }

    /**
     * Attaque de confusion d'algorithme : un jeton HS256 dont le secret HMAC est la cle PUBLIQUE
     * (connue de tous) ne doit pas etre accepte.
     */
    @Test
    void unJetonHs256SigneAvecLaClePubliqueEstRefuse() throws Exception {
        var forge = Jwts.builder().subject("admin@x").issuer("smartcdc")
                .audience().add("smartcdc-chat").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(KEYS.getPublic().getEncoded()), Jwts.SIG.HS256).compact();

        ask(forge, "{\"question\":\"Bonjour\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonExpireEstRefuse() throws Exception {
        ask(token(freshUser(), KEYS, -1000), "{\"question\":\"Bonjour\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonSigneAvecUneAutreCleEstRefuse() throws Exception {
        ask(token(freshUser(), OTHER_KEYS, 60_000), "{\"question\":\"Bonjour\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonValideObtientLaReponse() throws Exception {
        when(chatAiService.ragChat(anyString(), any())).thenReturn("Le délai est de 15 jours.");

        ask(token(freshUser(), KEYS, 60_000), "{\"question\":\"Quel est le délai d'opposition ?\"}")
                .andExpect(status().isOk())
                .andExpect(content().string("Le délai est de 15 jours."));
    }

    @Test
    void uneQuestionVideOuTropLongueRenvoie400() throws Exception {
        String jeton = token(freshUser(), KEYS, 60_000);

        ask(jeton, "{\"question\":\"   \"}").andExpect(status().isBadRequest());
        ask(jeton, "{\"question\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void unCorpsIllisibleRenvoie400() throws Exception {
        ask(token(freshUser(), KEYS, 60_000), "{").andExpect(status().isBadRequest());
    }

    @Test
    void laTroisiemeQuestionDeLaMinuteEstRefuseeEn429AvantToutAppelALIA() throws Exception {
        String jeton = token(freshUser(), KEYS, 60_000);
        when(chatAiService.ragChat(anyString(), any())).thenReturn("ok");

        ask(jeton, "{\"question\":\"1\"}").andExpect(status().isOk());
        ask(jeton, "{\"question\":\"2\"}").andExpect(status().isOk());
        ask(jeton, "{\"question\":\"3\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        org.mockito.Mockito.verify(chatAiService, org.mockito.Mockito.times(2)).ragChat(anyString(), any());
    }

    @Test
    void uneErreurDuFournisseurDIARenvoie502SansDetailTechnique() throws Exception {
        when(chatAiService.ragChat(anyString(), any()))
                .thenThrow(new NonTransientAiException("HTTP 401 - Incorrect API key provided: sk-proj-secret"));

        ask(token(freshUser(), KEYS, 60_000), "{\"question\":\"Bonjour\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Le service d'IA est momentanément indisponible."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-proj"))));
    }

    @Test
    void uneErreurInattendueRenvoie500SansMessageInterne() throws Exception {
        when(chatAiService.ragChat(anyString(), any())).thenThrow(new IllegalStateException("jdbc:postgresql://interne:5432 refuse"));

        ask(token(freshUser(), KEYS, 60_000), "{\"question\":\"Bonjour\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
    }

    @Test
    void lePreflightCorsNEstAccepteQuePourLOrigineDuFront() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/chat/ask")
                        .header("Origin", "https://front.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/chat/ask")
                        .header("Origin", "https://evil.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ streaming (SSE)

    private org.springframework.test.web.servlet.ResultActions askStream(String bearer, String json) throws Exception {
        var request = post("/chat/ask/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).content(json);
        if (bearer != null) {
            request = request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request);
    }

    private String streamBody(org.springframework.test.web.servlet.ResultActions started) throws Exception {
        var result = started.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void leFluxEmetLesMorceauxPuisUnEvenementDone() throws Exception {
        when(chatAiService.stream(anyString(), any())).thenReturn(reactor.core.publisher.Flux.just("Le délai ", "est de 15 jours."));

        String body = streamBody(askStream(token(freshUser(), KEYS, 60_000), "{\"question\":\"Délai ?\"}"));

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("event:message"), body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("data:Le délai "), body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("data:est de 15 jours."), body);
        org.junit.jupiter.api.Assertions.assertTrue(body.trim().endsWith("data:") || body.contains("event:done"), body);
    }

    @Test
    void uneErreurEnCoursDeFluxEstSignaleeSansDetailTechnique() throws Exception {
        when(chatAiService.stream(anyString(), any())).thenReturn(reactor.core.publisher.Flux.concat(
                reactor.core.publisher.Flux.just("début "),
                reactor.core.publisher.Flux.error(new IllegalStateException("jdbc:postgresql://interne:5432 refuse"))));

        String body = streamBody(askStream(token(freshUser(), KEYS, 60_000), "{\"question\":\"Q ?\"}"));

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("event:error"), body);
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("jdbc"), "aucun detail interne dans le flux : " + body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("event:done"), body);
    }

    @Test
    void leFluxExigeUnJetonEtLesMemesValidationsQueLAppelSimple() throws Exception {
        askStream(null, "{\"question\":\"Q ?\"}").andExpect(status().isUnauthorized());
        askStream(token(freshUser(), KEYS, 60_000), "{\"question\":\"  \"}").andExpect(status().isBadRequest());
        askStream(token(freshUser(), KEYS, 60_000), "{\"question\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void leFluxPartageLePlafondDeQuestionsAvecLAppelSimple() throws Exception {
        String jeton = token(freshUser(), KEYS, 60_000);
        when(chatAiService.ragChat(anyString(), any())).thenReturn("ok");

        // Deux questions par l'appel simple epuisent le quota (2 par minute dans ce test)...
        ask(jeton, "{\"question\":\"1\"}").andExpect(status().isOk());
        ask(jeton, "{\"question\":\"2\"}").andExpect(status().isOk());

        // ...et le flux, qui appelle le meme OpenAI, est refuse avant toute generation.
        askStream(jeton, "{\"question\":\"3\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        org.mockito.Mockito.verify(chatAiService, org.mockito.Mockito.never()).stream(anyString(), any());
    }
}
