package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long>, JpaSpecificationExecutor<Utilisateur> {
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);

    Utilisateur findByNom(String agentName);

    /** Resolution d'un agent par nom, limitee a un departement. */
    Utilisateur findFirstByNomAndDepartement_IdOrderByIdAgentRecouv(String nom, Long departementId);

    /** Nombre d'utilisateurs rattaches a un departement (controle avant suppression, statistiques). */
    long countByDepartement_Id(Long departementId);
}
