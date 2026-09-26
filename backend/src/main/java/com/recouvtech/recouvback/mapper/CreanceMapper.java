package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;

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
        if (creance.getDepartement() != null) {
            dto.setDepartementId(creance.getDepartement().getId());
            dto.setDepartementNom(creance.getDepartement().getNom());
        }
        return dto;
    }

    public static Creance fromRequestDto(CreanceRequestDTO dto, Utilisateur agent, Client client) {
        if (dto == null) return null;
        Creance creance = new Creance();
        creance.setNumFacture(dto.getNumFacture());
        creance.setDateEmission(dto.getDateEmission());
        creance.setEcheance(dto.getEcheance());
        creance.setMontantFacture(dto.getMontantFacture());
        // montantEncaisse et statut ne viennent jamais de la requete : ils sont
        // calculables uniquement a partir des reglements (ReglementService) et
        // des penalites. Les accepter permettait a un agent de solder une dette
        // sans aucun paiement.
        creance.setAgentRecouv(agent);
        creance.setClient(client);
        // Le departement vient du client, jamais de la requete (cle etrangere composite en base).
        creance.setDepartement(client.getDepartement());
        return creance;
    }

    public static void updateFromRequestDto(Creance creance, CreanceRequestDTO dto, Utilisateur agent, Client client) {
        creance.setDateEmission(dto.getDateEmission());
        creance.setEcheance(dto.getEcheance());
        creance.setMontantFacture(dto.getMontantFacture());
        creance.setAgentRecouv(agent);
        creance.setClient(client);
        // L'echeance ou le montant ont pu changer : le statut derive doit suivre.
        creance.updateStatut();
    }
} 