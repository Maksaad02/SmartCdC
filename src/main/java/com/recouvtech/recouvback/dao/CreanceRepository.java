package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Creance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CreanceRepository extends JpaRepository<Creance, Long> {
    Creance findByNumFacture(String numFacture);

    boolean existsByNumFacture(String numFacture);

    /**
     * Find all unpaid debts (for External Chatbot API)
     */
    @Query("SELECT c FROM Creance c WHERE c.statut IN ('IMPAYEE', 'EN_RETARD', 'PENALISEE', 'PARTIELLEMENT_PAYEE')")
    List<Creance> findAllUnpaid();

    /**
     * Find all debts for a specific client (for External Chatbot API)
     */
    List<Creance> findByClientId(Long clientId);

    /** Portefeuille d'un agent : utilise pour le cloisonnement horizontal. */
    List<Creance> findByAgentRecouv_Email(String email);

    /** Creance appartenant a l'agent donne (null si elle existe mais ne lui appartient pas). */
    Creance findByNumFactureAndAgentRecouv_Email(String numFacture, String email);
}
