package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.event.RelanceCreeeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression du bug "e-mail envoye dans la transaction" : un debiteur recevait
 * une relance pour une creance ensuite annulee par rollback.
 */
@SpringBootTest
class RelanceEmailAfterCommitTest {

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private EmailService emailService;
    @MockitoBean private RelanceEnvoiService envoiService;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        reset(emailService, envoiService);
    }

    @Test
    void uneTransactionAnnuleeNEnvoieAucunEmail() throws InterruptedException {
        transaction.executeWithoutResult(status -> {
            publisherEvent(5L);
            status.setRollbackOnly();
        });

        Thread.sleep(500); // laisse a un traitement asynchrone indu le temps de se produire
        verifyNoInteractions(emailService);
        verifyNoInteractions(envoiService);
    }

    @Test
    void uneTransactionValideeEnvoieLEmailUneFoisPuisMarqueLaRelance() {
        var courriel = new RelanceEnvoiService.Courriel(5L, "client@debiteur.test", "Relance", "Bonjour");
        AtomicReference<Object> authentificationVue = new AtomicReference<>("non renseigne");
        when(envoiService.preparerEnvoiAutomatique(5L)).thenAnswer(inv -> {
            // Thread du pool : tache systeme, aucun utilisateur authentifie (donc aucun filtre de departement).
            authentificationVue.set(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            return courriel;
        });

        transaction.executeWithoutResult(status -> publisherEvent(5L));

        verify(emailService, timeout(3000)).envoyer("client@debiteur.test", "Relance", "Bonjour");
        verify(envoiService, timeout(3000)).marquerEnvoyee(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(com.recouvtech.recouvback.entity.enums.StatutRelance.ENVOYEE),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        assertNull(authentificationVue.get(),
                "l'envoi asynchrone est une tache systeme : il ne doit pas heriter de l'identite de la requete");
    }

    @Test
    void unEchecSmtpMarqueLaRelanceEnEchecSansFairePlanterLAppelant() {
        var courriel = new RelanceEnvoiService.Courriel(5L, "client@debiteur.test", "Relance", "Bonjour");
        when(envoiService.preparerEnvoiAutomatique(5L)).thenReturn(courriel);
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP indisponible"))
                .when(emailService).envoyer("client@debiteur.test", "Relance", "Bonjour");

        transaction.executeWithoutResult(status -> publisherEvent(5L));

        verify(envoiService, timeout(3000)).marquerEchec(
                org.mockito.ArgumentMatchers.eq(5L), anyString());
        verify(envoiService, never()).marquerEnvoyee(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void publisherEvent(Long relanceId) {
        publisher.publishEvent(new RelanceCreeeEvent(relanceId));
    }
}
