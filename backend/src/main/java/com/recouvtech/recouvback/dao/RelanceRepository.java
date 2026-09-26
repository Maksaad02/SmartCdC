package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
@Repository
public interface RelanceRepository extends JpaRepository<Relance, Long>, JpaSpecificationExecutor<Relance> {

    /**
     * Chargement par id en JPQL : em.find (findById d'origine) ignore le filtre Hibernate de
     * departement, une requete JPQL l'applique. Sans cette redefinition, un MANAGER pourrait lire
     * un enregistrement d'un autre departement en devinant son id.
     */
    @Override
    @Query("SELECT r FROM Relance r WHERE r.id = :id")
    Optional<Relance> findById(@Param("id") Long id);

    /** Liste paginee : creance, son agent, l'agent de la relance et le departement charges avec la page. */
    @Override
    @EntityGraph(attributePaths = {"creance", "creance.client", "creance.agentRecouv", "agentRecouv", "departement"})
    Page<Relance> findAll(Specification<Relance> spec, Pageable pageable);

    /**
     * Trouver les relances par statut
     */
    List<Relance> findByStatutRelance(StatutRelance statutRelance);

    /**
     * Trouver les relances par numéro de facture et statut
     */
    List<Relance> findByCreanceNumFactureAndStatutRelance(String numFacture, StatutRelance statutRelance);

    /**
     * Trouver les relances par créance
     */
    List<Relance> findByCreanceNumFacture(String numFacture);
}
