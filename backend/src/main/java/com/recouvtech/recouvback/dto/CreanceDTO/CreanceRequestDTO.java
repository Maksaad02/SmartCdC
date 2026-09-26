package com.recouvtech.recouvback.dto.CreanceDTO;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreanceRequestDTO {

    @NotBlank(message = "Le numéro de facture est obligatoire")
    @Size(max = 100)
    @Pattern(regexp = "^[\\p{L}\\p{N} ._/\\-]+$", message = "Le numéro de facture contient des caractères non autorisés")
    private String numFacture;

    private LocalDate dateEmission;

    @NotNull(message = "L'échéance est obligatoire")
    private LocalDate echeance;

    @NotNull(message = "Le montant de la facture est obligatoire")
    @Positive(message = "Le montant doit être strictement positif")
    @Digits(integer = 17, fraction = 2, message = "Montant invalide (2 décimales maximum)")
    private BigDecimal montantFacture;

    // montantEncaisse et statut sont volontairement absents : ils sont calcules
    // cote serveur (reglements, penalites) et ne doivent pas etre modifiables.

    @Size(max = 255)
    private String agentName;

    @NotBlank(message = "Le client est obligatoire")
    @Size(max = 255)
    private String clientName; // raisonSociale
}
