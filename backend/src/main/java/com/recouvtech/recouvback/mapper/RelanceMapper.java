package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.RelanceDTO.RelanceRequestDTO;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceResponseDTO;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Utilisateur;

public class RelanceMapper {
    public static RelanceResponseDTO toDto(Relance relance) {
        if (relance == null) return null;
        RelanceResponseDTO dto = new RelanceResponseDTO();
        dto.setId(relance.getId());
        dto.setNumFacture(relance.getCreance() != null ? relance.getCreance().getNumFacture() : null);
        dto.setAgentName(relance.getAgentRecouv() != null ? relance.getAgentRecouv().getNom() : null);
        dto.setDateRelance(relance.getDateRelance());
        dto.setTypeRelance(relance.getTypeRelance());
        dto.setStatutRelance(relance.getStatutRelance());
        dto.setCommentaire(relance.getCommentaire());
        return dto;
    }

    public static Relance fromRequestDto(RelanceRequestDTO dto, Creance creance, Utilisateur agent) {
        if (dto == null) return null;
        Relance relance = new Relance();
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setDateRelance(dto.getDateRelance());
        relance.setTypeRelance(dto.getTypeRelance());
        relance.setStatutRelance(dto.getStatutRelance());
        relance.setCommentaire(dto.getCommentaire());
        return relance;
    }

    public static void updateFromRequestDto(Relance relance, RelanceRequestDTO dto, Creance creance, Utilisateur agent) {
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setDateRelance(dto.getDateRelance());
        relance.setTypeRelance(dto.getTypeRelance());
        relance.setStatutRelance(dto.getStatutRelance());
        relance.setCommentaire(dto.getCommentaire());
    }
} 