package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Creance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @Transactional(readOnly = true) au niveau de l'interface : Spring Data n'applique PAS de transaction aux
 * methodes de requete declarees (findByNumFacture, @Query...), seulement aux methodes CRUD heritees. Or le
 * filtre de departement est active a l'ouverture d'une transaction (DepartementFilterTransactionManager) :
 * hors transaction, une requete s'executerait SANS filtre. Cette annotation ferme cette porte pour tout
 * appel direct au repository ; les ecritures (save, delete) gardent leur propre transaction.
 */
@Transactional(readOnly = true)
public interface CreanceRepository extends JpaRepository<Creance, Long>, JpaSpecificationExecutor<Creance> {

    String UNPAID = "c.statut IN ('IMPAYEE', 'EN_RETARD', 'PENALISEE', 'PARTIELLEMENT_PAYEE')";

    /**
     * Chargement par id en JPQL : em.find (findById d'origine) ignore le filtre Hibernate de
     * departement, une requete JPQL l'applique. Sans cette redefinition, un MANAGER pourrait lire
     * un enregistrement d'un autre departement en devinant son id.
     */
    @Override
    @Query("SELECT c FROM Creance c WHERE c.id = :id")
    Optional<Creance> findById(@Param("id") Long id);

    Creance findByNumFacture(String numFacture);
    boolean existsByNumFacture(String numFacture);

    /** Liste paginee, avec agent, client et departement charges d'un coup (pas de N+1 au mapping). */
    @Override
    @EntityGraph(attributePaths = {"agentRecouv", "client", "departement"})
    Page<Creance> findAll(Specification<Creance> spec, Pageable pageable);

    /** Impayes du perimetre de l'appelant (tous departements pour un ADMIN), API chatbot, bornes par Pageable. */
    @EntityGraph(attributePaths = {"agentRecouv", "client"})
    @Query("SELECT c FROM Creance c WHERE " + UNPAID)
    List<Creance> findUnpaid(Pageable pageable);

    /** Impayes du portefeuille d'un agent (API chatbot), bornes par Pageable. */
    @EntityGraph(attributePaths = {"agentRecouv", "client"})
    @Query("SELECT c FROM Creance c WHERE " + UNPAID + " AND c.agentRecouv.email = :email")
    List<Creance> findUnpaidByAgent(@Param("email") String email, Pageable pageable);

    /** Creances d'un client (API chatbot), bornees par Pageable. */
    @EntityGraph(attributePaths = {"agentRecouv", "client"})
    List<Creance> findByClientId(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"agentRecouv", "client"})
    List<Creance> findByClientIdAndAgentRecouv_Email(Long clientId, String email, Pageable pageable);

    /** Portefeuille d'un agent : utilise pour le cloisonnement horizontal. */
    List<Creance> findByAgentRecouv_Email(String email);

    /** Creance appartenant a l'agent donne (null si elle existe mais ne lui appartient pas). */
    Creance findByNumFactureAndAgentRecouv_Email(String numFacture, String email);

    /**
     * Agregats par statut, calcules en base : le tableau de bord ne charge plus
     * toutes les creances dans le navigateur pour les compter. Colonnes :
     * statut, nombre, somme facturee, somme encaissee.
     */
    @Query("SELECT c.statut, COUNT(c), COALESCE(SUM(c.montantFacture), 0), COALESCE(SUM(c.montantEncaisse), 0), " +
            "COALESCE(SUM(c.montantPenalites), 0) FROM Creance c GROUP BY c.statut")
    List<Object[]> statsByStatut();

    @Query("SELECT c.statut, COUNT(c), COALESCE(SUM(c.montantFacture), 0), COALESCE(SUM(c.montantEncaisse), 0), " +
            "COALESCE(SUM(c.montantPenalites), 0) FROM Creance c WHERE c.agentRecouv.email = :email GROUP BY c.statut")
    List<Object[]> statsByStatutForAgent(@Param("email") String email);

    /**
     * Comparatif des departements (tableau de bord ADMIN) en UNE requete agregee, departements sans
     * creance inclus. Colonnes : id, nom, nombre, facture, penalites, encaisse, nombre en retard
     * (EN_RETARD ou PENALISEE).
     */
    @Query("SELECT d.id, d.nom, COUNT(c), COALESCE(SUM(c.montantFacture), 0), COALESCE(SUM(c.montantPenalites), 0), " +
            "COALESCE(SUM(c.montantEncaisse), 0), " +
            "COALESCE(SUM(CASE WHEN c.statut IN ('EN_RETARD', 'PENALISEE') THEN 1 ELSE 0 END), 0) " +
            "FROM Departement d LEFT JOIN Creance c ON c.departement = d " +
            "GROUP BY d.id, d.nom ORDER BY d.nom")
    List<Object[]> statsByDepartement();
}
