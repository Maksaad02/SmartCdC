package com.recouvtech.recouvback.dto.DepartementDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartementRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 255)
    private String nom;

    /** Code court et stable, en majuscules (ex. SIEGE, CASA, RABAT-2). */
    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[A-Z0-9_-]{2,30}$", message = "Le code doit contenir 2 à 30 caractères : majuscules, chiffres, - ou _")
    private String code;

    /** Facultatif : actif par defaut. */
    private Boolean actif;
}
