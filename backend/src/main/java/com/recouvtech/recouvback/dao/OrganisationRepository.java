package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    Optional<Organisation> findByNom(String nom);
}
