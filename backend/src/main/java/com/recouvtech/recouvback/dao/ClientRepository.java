package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Client findByRaisonSociale(String clientName);

    /**
     * Search clients by keyword (raison sociale, ICE, or telephone)
     * Used by External Chatbot API
     */
    @Query("SELECT c FROM Client c WHERE " +
            "LOWER(c.raisonSociale) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "c.ice = :query OR " +
            "c.telephone = :query")
    List<Client> searchByKeyword(@Param("query") String query);

    /** Portefeuille d'un agent : utilise pour le cloisonnement horizontal. */
    List<Client> findByAgentRecouv_Email(String email);
}
