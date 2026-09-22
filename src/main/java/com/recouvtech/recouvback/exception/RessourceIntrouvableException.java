package com.recouvtech.recouvback.exception;

/**
 * Ressource absente, ou hors du perimetre de l'appelant.
 *
 * Les deux cas sont volontairement confondus : distinguer « n'existe pas » de
 * « existe mais appartient a une autre organisation » confirmerait l'existence
 * de donnees chez un autre client.
 */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
