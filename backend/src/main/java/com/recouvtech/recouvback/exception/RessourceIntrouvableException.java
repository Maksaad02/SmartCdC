package com.recouvtech.recouvback.exception;

/**
 * Ressource absente, ou hors du perimetre de l'appelant.
 *
 * Les deux cas sont volontairement confondus : distinguer « n'existe pas » de
 * « existe mais appartient a un autre departement » confirmerait l'existence
 * de donnees hors du perimetre de l'appelant.
 */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
