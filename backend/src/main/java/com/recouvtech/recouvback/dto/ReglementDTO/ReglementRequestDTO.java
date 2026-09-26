package com.recouvtech.recouvback.dto.ReglementDTO;

import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReglementRequestDTO {

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être strictement positif")
    @Digits(integer = 17, fraction = 2, message = "Montant invalide (2 décimales maximum)")
    private BigDecimal montant;

    @NotNull(message = "La date de règlement est obligatoire")
    private LocalDate dateReglement;

    @NotNull(message = "Le mode de paiement est obligatoire")
    private ModePaiement modePaiement;

    private StatutReglement statut;

    @Size(max = 255)
    private String reference;

    @NotBlank(message = "Le numéro de facture est obligatoire")
    @Size(max = 100)
    private String numFacture;

    @NotBlank(message = "L'agent est obligatoire")
    @Size(max = 255)
    private String agentName;
}
