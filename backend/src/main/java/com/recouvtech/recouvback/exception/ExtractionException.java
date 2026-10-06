package com.recouvtech.recouvback.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Echec de la lecture automatique d'une facture, avec le statut HTTP a renvoyer et un message
 * destine a l'utilisateur (jamais de detail technique : il est journalise par l'appelant).
 */
@Getter
public class ExtractionException extends RuntimeException {

    private final HttpStatus statut;

    public ExtractionException(HttpStatus statut, String message) {
        super(message);
        this.statut = statut;
    }
}
