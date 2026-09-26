package com.recouvtech.recouvback.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final String expediteur;

    public EmailServiceImpl(JavaMailSender javaMailSender, @Value("${spring.mail.username}") String expediteur) {
        this.javaMailSender = javaMailSender;
        this.expediteur = expediteur;
    }

    @Override
    public void envoyer(String destinataire, String sujet, String texte) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject(sujet);
        message.setText(texte);
        // Delais de connexion, d'ecriture et de lecture : voir spring.mail.properties.mail.smtp.*
        javaMailSender.send(message);
    }
}
