package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Client;
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
public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    /**
     * Chargement par id en JPQL : em.find (findById d'origine) ignore le filtre Hibernate de
     * departement, une requete JPQL l'applique. Sans cette redefinition, un MANAGER pourrait lire
     * un enregistrement d'un autre departement en devinant son id.
     */
    @Override
    @Query("SELECT c FROM Client c WHERE c.id = :id")
    Optional<Client> findById(@Param("id") Long id);

    Client findByRaisonSociale(String clientName);

    /**
     * Liste paginee. L'agent est charge avec le client (EntityGraph) : le mapping
     * en DTO lit agentRecouv.nom, ce qui declenchait sinon une requete par ligne.
     */
    @Override
    @EntityGraph(attributePaths = {"agentRecouv", "departement"})
    Page<Client> findAll(Specification<Client> spec, Pageable pageable);

    /** Portefeuille d'un agent : utilise pour le cloisonnement horizontal. */
    List<Client> findByAgentRecouv_Email(String email);
}
