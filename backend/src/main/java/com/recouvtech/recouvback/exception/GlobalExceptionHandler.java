package com.recouvtech.recouvback.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    /**
     * Contrainte d'unicite ou de reference violee (ICE, RC, numero de facture deja utilises...) :
     * conflit avec l'etat actuel des donnees (409), pas erreur interne. Le detail SQL n'est pas
     * renvoye : il revele la structure de la base.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrite(DataIntegrityViolationException e) {
        log.warn("Contrainte d'intégrité violée : {}", e.getMostSpecificCause().getMessage());
        return reponse(HttpStatus.CONFLICT,
                "Cette valeur existe déjà ou est référencée par d'autres données (RC, ICE, identité fiscale, n° de facture...).");
    }

    /** Corps JSON absent ou mal forme : erreur du client (400), pas du serveur. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> corpsIllisible(HttpMessageNotReadableException e) {
        return reponse(HttpStatus.BAD_REQUEST, "Corps de requête invalide");
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

    /** Parametre d'URL absent ou de mauvais type (ex. statut=XYZ) : erreur du client. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> parametreInvalide(Exception e) {
        return reponse(HttpStatus.BAD_REQUEST, "Paramètre de requête invalide");
    }

    /**
     * Sans ces cas, le filet ci-dessous transformait un chemin inconnu (404), une
     * methode non permise (405) ou un type de contenu refuse (415) en erreur 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> cheminInconnu(NoResourceFoundException e) {
        return reponse(HttpStatus.NOT_FOUND, "Ressource introuvable");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodeNonPermise(HttpRequestMethodNotSupportedException e) {
        return reponse(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> typeNonSupporte(HttpMediaTypeNotSupportedException e) {
        return reponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporté");
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
