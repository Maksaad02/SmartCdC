package com.recouvtech.recouvback.dto.CreanceDTO;

import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class CreanceResponseDTO {
    private Long id;
    private String numFacture;
    private LocalDate echeance;
    private BigDecimal montantFacture;
    private BigDecimal montantEncaisse;
    private BigDecimal solde;
    private BigDecimal montantPenalites;
    private BigDecimal montantTotal;
    private int joursRetard;
    private String statut;
    private String agentName;
    private String clientName;
    private Long departementId;
    private String departementNom;
}
