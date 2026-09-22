package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);

    Utilisateur findByNom(String agentName);

    /** Resolution d'un agent par nom, limitee a une organisation. */
    Utilisateur findByNomAndOrganisation_Id(String nom, Long organisationId);

    /**
     * Utilisateurs d'une organisation.
     *
     * Utilisateur ne porte pas @TenantId (la connexion doit pouvoir resoudre un
     * compte avant de connaitre l'organisation), donc son cloisonnement est
     * explicite : ces methodes sont la seule voie d'acces cote service.
     */
    List<Utilisateur> findByOrganisation_Id(Long organisationId);

    Optional<Utilisateur> findByIdAgentRecouvAndOrganisation_Id(Long id, Long organisationId);
}
