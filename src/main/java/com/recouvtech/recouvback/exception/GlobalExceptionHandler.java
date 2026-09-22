package com.recouvtech.recouvback.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduction unique des exceptions en reponses HTTP.
 *
 * Sans cela, toute erreur metier remontait en 500 accompagnee d'une trace :
 * mauvais statut pour l'appelant, et divulgation de la structure interne.
 * Le detail technique est journalise, jamais renvoye.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> introuvable(RessourceIntrouvableException e) {
        return reponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accesRefuse(AccessDeniedException e) {
        return reponse(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> nonAuthentifie(AuthenticationException e) {
        return reponse(HttpStatus.UNAUTHORIZED, "Identifiants incorrects");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> requeteInvalide(IllegalArgumentException e) {
        return reponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> etatInvalide(IllegalStateException e) {
        log.error("État applicatif invalide", e);
        return reponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Erreurs de @Valid : renvoie les champs fautifs, sans trace. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, Object> corps = corps(HttpStatus.BAD_REQUEST, "Requête invalide");
        corps.put("champs", e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalide",
                        (a, b) -> a)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corps);
    }

    /**
     * Filet de securite. Le message reel n'est jamais renvoye : il peut contenir
     * des identifiants techniques, des fragments de requete ou des donnees.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inattendue(Exception e) {
        log.error("Erreur non gérée", e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur interne est survenue");
    }

    private ResponseEntity<Map<String, Object>> reponse(HttpStatus statut, String message) {
        return ResponseEntity.status(statut).body(corps(statut, message));
    }

    private Map<String, Object> corps(HttpStatus statut, String message) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("timestamp", LocalDateTime.now().toString());
        corps.put("status", statut.value());
        corps.put("error", statut.getReasonPhrase());
        corps.put("message", message != null ? message : statut.getReasonPhrase());
        return corps;
    }
}
