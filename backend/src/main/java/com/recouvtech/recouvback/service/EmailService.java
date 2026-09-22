package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Relance;

public interface EmailService {

    /**
     * Envoie la relance au client de la creance concernee.
     *
     * Le destinataire est derive de la creance, jamais fourni par l'appelant :
     * l'ancien sendSimpleMail(EmailDetails) exposait destinataire, sujet et corps
     * et transformait /api/sendMail en relais de messagerie ouvert.
     */
    String envoyerRelance(Relance relance);
}
