package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.DepartementDTO.DepartementResponseDTO;
import com.recouvtech.recouvback.entity.Departement;

public class DepartementMapper {

    public static DepartementResponseDTO toDto(Departement departement) {
        if (departement == null) return null;
        DepartementResponseDTO dto = new DepartementResponseDTO();
        dto.setId(departement.getId());
        dto.setNom(departement.getNom());
        dto.setCode(departement.getCode());
        dto.setActif(departement.isActif());
        return dto;
    }
}
