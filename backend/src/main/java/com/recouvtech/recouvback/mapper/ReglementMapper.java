package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.StatutReglement;

public class ReglementMapper {
    public static ReglementResponseDTO toDto(Reglement reglement) {
        if (reglement == null) return null;
        ReglementResponseDTO dto = new ReglementResponseDTO();
        dto.setId(reglement.getId());
        dto.setMontant(reglement.getMontant());
        dto.setDateReglement(reglement.getDateReglement());
        dto.setModePaiement(reglement.getModePaiement());
        dto.setStatut(reglement.getStatut());
        dto.setReference(reglement.getReference());
        dto.setNumFacture(reglement.getCreance() != null ? reglement.getCreance().getNumFacture() : null);
        dto.setAgentName(reglement.getAgentRecouv() != null ? reglement.getAgentRecouv().getNom() : null);
        dto.setClientName(reglement.getCreance() != null && reglement.getCreance().getClient() != null ? reglement.getCreance().getClient().getRaisonSociale() : null);

        return dto;
    }

    public static Reglement fromRequestDto(ReglementRequestDTO dto, Creance creance, Utilisateur agent) {
        if (dto == null) return null;
        Reglement reglement = new Reglement();
        reglement.setMontant(dto.getMontant());
        reglement.setDateReglement(dto.getDateReglement());
        reglement.setModePaiement(dto.getModePaiement());
        reglement.setStatut(dto.getStatut());
        reglement.setReference(dto.getReference());
        reglement.setCreance(creance);
        reglement.setAgentRecouv(agent);
        return reglement;
    }

    public static void updateFromRequestDto(Reglement reglement, ReglementRequestDTO dto, Creance creance, Utilisateur agent) {
        reglement.setMontant(dto.getMontant());
        reglement.setDateReglement(dto.getDateReglement());
        reglement.setModePaiement(dto.getModePaiement());
        reglement.setStatut(dto.getStatut());
        reglement.setReference(dto.getReference());
        reglement.setCreance(creance);
        reglement.setAgentRecouv(agent);
    }
} 