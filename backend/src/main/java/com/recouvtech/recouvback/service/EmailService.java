package com.recouvtech.recouvback.service;

public interface EmailService {

    /**
     * Envoie un e-mail texte. Leve une exception si l'envoi echoue (SMTP
     * indisponible, adresse refusee...) : c'est a l'appelant de decider quoi en
     * faire, ici il n'y a aucune relance ni statut.
     *
     * Le destinataire n'est jamais fourni par un client de l'API : l'ancien
     * sendSimpleMail(EmailDetails) exposait destinataire, sujet et corps et
     * transformait /api/sendMail en relais de messagerie ouvert. Il est derive
     * de la creance par RelanceEnvoiService.
     */
    void envoyer(String destinataire, String sujet, String texte);
}
