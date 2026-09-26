package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Departement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartementRepository extends JpaRepository<Departement, Long> {

    Optional<Departement> findByCode(String code);

    boolean existsByNom(String nom);

    boolean existsByCode(String code);
}
