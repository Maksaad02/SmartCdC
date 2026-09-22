package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelanceRepository extends JpaRepository<Relance, Long> {
    
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
