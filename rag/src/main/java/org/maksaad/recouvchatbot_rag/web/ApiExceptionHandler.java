package org.maksaad.recouvchatbot_rag.web;

import org.maksaad.recouvchatbot_rag.web.ChatRateLimiter.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduction unique des erreurs en reponses HTTP. Sans elle, toute erreur (question
 * trop longue, OpenAI indisponible) remontait en 500 par defaut, indistinguable pour
 * l'interface. Le detail technique est journalise, jamais renvoye : un message
 * d'erreur d'OpenAI peut contenir des fragments de configuration.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        // Message de validation volontaire (IllegalArgumentException) ; corps JSON illisible -> generique.
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "Requête invalide";
        return body(HttpStatus.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> tooMany(RateLimitExceededException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS, "Trop de questions. Réessayez dans un instant.",
                e.getRetryAfterSeconds());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "Accès refusé", null);
    }

    /** Fournisseur d'IA en erreur ou injoignable : ce n'est ni la faute de l'utilisateur ni un bug interne. */
    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class, RestClientException.class})
    public ResponseEntity<Map<String, Object>> aiUnavailable(Exception e) {
        log.error("Service d'IA indisponible", e);
        return body(HttpStatus.BAD_GATEWAY, "Le service d'IA est momentanément indisponible.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Erreur non gérée", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue.", null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Long retryAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (retryAfter != null) {
            builder.header("Retry-After", String.valueOf(retryAfter));
        }
        return builder.body(body);
    }
}
