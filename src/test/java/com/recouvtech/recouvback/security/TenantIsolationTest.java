package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.configuration.TenantResolver;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preuve au niveau ORM que @TenantId isole reellement les organisations.
 *
 * C'est le test qui compte le plus dans ce projet : sans lui, une regression de
 * cloisonnement (une requete qui contourne le filtre, un TenantContext mal
 * positionne) ne serait decouverte qu'en production, par une fuite reelle de
 * donnees entre deux clients payants.
 *
 * Le multi-tenant par discriminant de Hibernate resout l'organisation UNE FOIS
 * a l'ouverture de la Session, pas a chaque requete. En production
 * (spring.jpa.open-in-view=false), chaque appel @Transactional ouvre sa propre
 * session, APRES que JwtAuthenticationFilter a positionne TenantContext : donc
 * chaque requete HTTP resout naturellement la bonne organisation. Ce test
 * reproduit cette frontiere en ouvrant explicitement un EntityManager distinct
 * par organisation simulee, plutot que de reutiliser la session partagee que
 * @DataJpaTest fournit par defaut pour toute la duree du test.
 *
 * @Transactional(NOT_SUPPORTED) desactive l'enveloppe transactionnelle que
 * @DataJpaTest applique par defaut : sans cela, ce test partagerait quand meme
 * une session unique, pour la meme raison que la premiere version (echouee) de
 * ce test.
 */
@DataJpaTest
@Import(TenantResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantIsolationTest {

    private static final Long ORG_A = 1L;
    private static final Long ORG_B = 2L;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void nettoyerLeContexte() {
        TenantContext.clear();
    }

    @Test
    void unClientCreeDansOrgANEstPasVisibleDepuisOrgB() {
        Long id = executerCommeOrganisation(ORG_A, em -> {
            Client client = new Client();
            client.setRaisonSociale("ACME SARL");
            client.setEmail("contact@acme.test");
            client.setTelephone("0522000001");
            client.setRc("RC-1");
            client.setAdresse("1 rue Test");
            client.setIce("ICE-1");
            client.setIdentiteFiscale("IF-1");
            em.persist(client);
            return client.getId();
        });

        executerCommeOrganisation(ORG_B, em -> {
            assertNull(em.find(Client.class, id),
                    "Un client de l'organisation A a ete lu depuis l'organisation B");
            List<Client> tous = em.createQuery("select c from Client c", Client.class).getResultList();
            assertTrue(tous.isEmpty(),
                    "Une requete sous l'organisation B a renvoye des donnees de l'organisation A");
            return null;
        });

        executerCommeOrganisation(ORG_A, em -> {
            assertNotNull(em.find(Client.class, id),
                    "Le client n'est meme plus visible depuis sa propre organisation");
            return null;
        });
    }

    @Test
    void deuxOrganisationsPeuventUtiliserLeMemeNumeroDeFacture() {
        executerCommeOrganisation(ORG_A, em -> {
            em.persist(nouvelleCreance("F-2026-001"));
            return null;
        });

        assertDoesNotThrow(() -> executerCommeOrganisation(ORG_B, em -> {
            em.persist(nouvelleCreance("F-2026-001"));
            return null;
        }), "Le numero de facture ne devrait etre unique que PAR organisation");

        executerCommeOrganisation(ORG_B, em -> {
            List<Creance> visibles = em.createQuery("select c from Creance c", Creance.class).getResultList();
            assertEquals(1, visibles.size(),
                    "L'organisation B ne doit voir que sa propre facture, pas celle de A");
            return null;
        });
    }

    /**
     * Simule une requete HTTP : positionne l'organisation, ouvre une session
     * dediee (donc un nouveau resolveCurrentTenantIdentifier()), l'utilise dans
     * sa propre transaction, puis la ferme.
     */
    private <T> T executerCommeOrganisation(Long organisationId, java.util.function.Function<EntityManager, T> action) {
        TenantContext.set(organisationId);
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            T resultat = action.apply(em);
            em.getTransaction().commit();
            return resultat;
        } catch (RuntimeException | AssertionError e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private Creance nouvelleCreance(String numFacture) {
        Creance c = new Creance();
        c.setNumFacture(numFacture);
        c.setMontantFacture(BigDecimal.valueOf(1000));
        c.setMontantEncaisse(BigDecimal.ZERO);
        return c;
    }
}
