package org.maksaad.recouvchatbot_rag.client.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * DTO for Debt/Invoice information (mirror of Backend's CreanceResponseDTO)
 */
@Data
public class CreanceDTO {
    private Long id;
    private String numFacture;
    private LocalDate echeance;
    private Double montantFacture;
    private Double montantEncaisse;
    private Double solde; // Calculated field
    private Double montantPenalites;
    private Double montantTotal; // Calculated field
    private int joursRetard; // Calculated field
    private String statut; // IMPAYEE, EN_RETARD, PENALISEE, etc.
    private String agentName;
    private String clientName;
}
