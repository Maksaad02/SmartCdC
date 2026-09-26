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
        dto.setMessage(relance.getMessage());
        if (relance.getCreance() != null) {
            dto.setEcheance(relance.getCreance().getEcheance());
            dto.setJoursRetard(relance.getCreance().getJoursRetard());
            if (relance.getCreance().getClient() != null) {
                dto.setClientName(relance.getCreance().getClient().getRaisonSociale());
            }
        }
        return dto;
    }

    public static Relance fromRequestDto(RelanceRequestDTO dto, Creance creance, Utilisateur agent) {
        if (dto == null) return null;
        Relance relance = new Relance();
        relance.setCreance(creance);
        // Herite du departement de la creance, jamais de la requete.
        relance.setDepartement(creance.getDepartement());
        relance.setAgentRecouv(agent);
        relance.setDateRelance(dto.getDateRelance());
        relance.setTypeRelance(dto.getTypeRelance());
        relance.setStatutRelance(dto.getStatutRelance());
        relance.setCommentaire(dto.getCommentaire());
        relance.setMessage(dto.getMessage());
        return relance;
    }

    public static void updateFromRequestDto(Relance relance, RelanceRequestDTO dto, Creance creance, Utilisateur agent) {
        relance.setCreance(creance);
        relance.setAgentRecouv(agent);
        relance.setDateRelance(dto.getDateRelance());
        relance.setTypeRelance(dto.getTypeRelance());
        relance.setStatutRelance(dto.getStatutRelance());
        relance.setCommentaire(dto.getCommentaire());
        // Absent de la requete : conserver le message existant plutot que de l'effacer.
        if (dto.getMessage() != null) {
            relance.setMessage(dto.getMessage());
        }
    }
} 