package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.security.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Active le filtre Hibernate "departement" a l'ouverture de CHAQUE transaction, pour les roles
 * cloisonnes (MANAGER, AGENT).
 *
 * C'est le filet SQL du cloisonnement : listes, comptages, agregats du tableau de bord, requetes
 * @Query, recherche du chatbot... toute requete portant sur Client, Creance, Reglement ou Relance recoit
 * automatiquement "departement_id = :departementId". Aucune requete n'a donc a y penser, y compris
 * celles qu'on ajoutera plus tard. Le meme mecanisme ne s'applique pas a em.find (chargement par id) :
 * les repositories redefinissent findById en JPQL, et les services controlent l'acces explicitement.
 *
 * Un ADMIN, une tache planifiee ou un ecouteur asynchrone n'ont pas de departement a imposer
 * (CurrentUser.departementFiltre() renvoie null) : ils travaillent sur toute l'entreprise.
 */
public class DepartementFilterTransactionManager extends JpaTransactionManager {

    private final transient CurrentUser currentUser;

    public DepartementFilterTransactionManager(EntityManagerFactory entityManagerFactory, CurrentUser currentUser) {
        super(entityManagerFactory);
        this.currentUser = currentUser;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        Long departement = currentUser.departementFiltre();
        if (departement == null) {
            return;
        }
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("Aucun EntityManager lie a la transaction : filtre de departement impossible");
        }
        holder.getEntityManager().unwrap(Session.class)
                .enableFilter(Departement.FILTRE)
                .setParameter(Departement.PARAMETRE, departement);
    }
}
