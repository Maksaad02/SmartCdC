package com.recouvtech.recouvback.dto.RelanceDTO;

import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RelanceRequestDTO {

    @NotBlank(message = "Le numéro de facture est obligatoire")
    @Size(max = 100)
    private String numFacture;

    @NotBlank(message = "L'agent est obligatoire")
    @Size(max = 255)
    private String agentName;

    @NotNull(message = "La date de relance est obligatoire")
    private LocalDate dateRelance;

    @NotNull(message = "Le type de relance est obligatoire")
    private TypeRelance typeRelance;

    private StatutRelance statutRelance;

    // relance.commentaire : VARCHAR(255) ; relance.message : VARCHAR(1000).
    @Size(max = 255, message = "Le commentaire ne peut pas dépasser 255 caractères")
    private String commentaire;

    @Size(max = 1000, message = "Le message ne peut pas dépasser 1000 caractères")
    private String message;
}
