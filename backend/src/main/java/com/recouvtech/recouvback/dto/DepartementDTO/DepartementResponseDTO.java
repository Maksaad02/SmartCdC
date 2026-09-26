package com.recouvtech.recouvback.dto.DepartementDTO;

import lombok.Data;

@Data
public class DepartementResponseDTO {
    private Long id;
    private String nom;
    private String code;
    private boolean actif;
}
