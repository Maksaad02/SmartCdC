package com.recouvtech.recouvback.dto.UtilisateurDTO;


import lombok.Data;
import org.antlr.v4.runtime.misc.NotNull;

@Data
public class UtilisateurRequestDTO {
    private String nom;
    private String email;
    private String motDePasse;
    @NotNull
    private Long roleId;
}