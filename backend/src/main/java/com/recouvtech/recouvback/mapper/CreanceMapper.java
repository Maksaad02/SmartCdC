package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutCreance;

public class CreanceMapper {
    public static CreanceResponseDTO toDto(Creance creance) {
        if (creance == null) return null;
        CreanceResponseDTO dto = new CreanceResponseDTO();
        dto.setId(creance.getId()); // Creance uses numFacture as ID, set if needed
        dto.setNumFacture(creance.getNumFacture());
        dto.setEcheance(creance.getEcheance());
        dto.setMontantFacture(creance.getMontantFacture());
        dto.setMontantEncaisse(creance.getMontantEncaisse());
        dto.setSolde(creance.getSolde());
        dto.setMontantPenalites(creance.getMontantPenalites());
        dto.setMontantTotal(creance.getMontantTotal());
        dto.setJoursRetard(creance.getJoursRetard());
        dto.setStatut(creance.getStatut() != null ? creance.getStatut().name() : null);
        dto.setAgentName(creance.getAgentRecouv() != null ? creance.getAgentRecouv().getNom() : null);
        dto.setClientName(creance.getClient() != null ? creance.getClient().getRaisonSociale() : null);
        return dto;
    }

    public static Creance fromRequestDto(CreanceRequestDTO dto, Utilisateur agent, Client client) {
        if (dto == null) return null;
        Creance creance = new Creance();
        creance.setNumFacture(dto.getNumFacture());
        creance.setDateEmission(dto.getDateEmission());
        creance.setEcheance(dto.getEcheance());
        creance.setMontantFacture(dto.getMontantFacture());
        creance.setMontantEncaisse(dto.getMontantEncaisse());
        creance.setStatut(dto.getStatut() != null ? StatutCreance.valueOf(dto.getStatut()) : null);
        creance.setAgentRecouv(agent);
        creance.setClient(client);
        return creance;
    }

    public static void updateFromRequestDto(Creance creance, CreanceRequestDTO dto, Utilisateur agent, Client client) {
        creance.setDateEmission(dto.getDateEmission());
        creance.setEcheance(dto.getEcheance());
        creance.setMontantFacture(dto.getMontantFacture());
        creance.setMontantEncaisse(dto.getMontantEncaisse());
        creance.setStatut(dto.getStatut() != null ? StatutCreance.valueOf(dto.getStatut()) : null);
        creance.setAgentRecouv(agent);
        creance.setClient(client);
    }
} 