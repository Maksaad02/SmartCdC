package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Reglement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReglementRepository extends JpaRepository<Reglement, Long> {
    List<Reglement> findByCreance(Creance creance);
}
