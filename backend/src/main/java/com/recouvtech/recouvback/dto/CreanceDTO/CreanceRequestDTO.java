package com.recouvtech.recouvback.dto.CreanceDTO;

import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class CreanceRequestDTO {
    private String numFacture;
    private LocalDate dateEmission;
    private LocalDate echeance;
    private BigDecimal montantFacture;
    private BigDecimal montantEncaisse;
    private String statut; // Enum sous forme de String
    private String agentName;
    private String clientName; // raisonSociale
}
