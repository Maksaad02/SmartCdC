package com.recouvtech.recouvback.dto.UtilisateurDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Creation et mise a jour d'un compte par un administrateur.
 *
 * motDePasse est obligatoire a la creation (verifie dans le service) et
 * facultatif a la mise a jour : un champ vide conserve le mot de passe actuel.
 * Le maximum de 72 caracteres correspond a la limite de BCrypt.
 */
@Data
public class UtilisateurRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas depasser 100 caracteres")
    private String nom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 180)
    private String email;

    @Size(min = 12, max = 72, message = "Le mot de passe doit contenir entre 12 et 72 caracteres")
    private String motDePasse;

    @NotNull(message = "Le role est obligatoire")
    private Long roleId;

    /**
     * Departement de rattachement : obligatoire pour un MANAGER ou un AGENT, interdit pour un ADMIN
     * (verifie par UtilisateurService). Absent a la modification : le departement actuel est conserve.
     */
    private Long departementId;
}
