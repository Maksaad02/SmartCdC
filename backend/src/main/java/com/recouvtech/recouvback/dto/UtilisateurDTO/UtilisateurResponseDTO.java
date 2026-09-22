package com.recouvtech.recouvback.dto.UtilisateurDTO;

import lombok.Data;

@Data
public class UtilisateurResponseDTO {
    private Long id;
    private String nom;
    private String email;
    private String role;
}