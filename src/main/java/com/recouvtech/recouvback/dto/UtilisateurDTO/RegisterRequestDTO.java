package com.recouvtech.recouvback.dto.UtilisateurDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload d'inscription.
 *
 * Volontairement limite a nom / email / motDePasse : lier directement l'entite
 * Utilisateur exposait idAgentRecouv et role, ce qui permettait d'ecraser un
 * compte existant (merge sur un id fourni par le client) ou de s'auto-attribuer
 * un role. Le role est impose cote serveur.
 */
@Data
public class RegisterRequestDTO {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas depasser 100 caracteres")
    private String nom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 180)
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 12, max = 128, message = "Le mot de passe doit contenir au moins 12 caracteres")
    private String motDePasse;
}
