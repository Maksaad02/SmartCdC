package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;

public class ClientMapper {
    public static ClientResponseDTO toDto(Client client) {
        if (client == null) return null;
        ClientResponseDTO dto = new ClientResponseDTO();
        dto.setId(client.getId());
        dto.setRaisonSociale(client.getRaisonSociale());
        dto.setEmail(client.getEmail());
        dto.setTelephone(client.getTelephone());
        dto.setRc(client.getRc());
        dto.setAdresse(client.getAdresse());
        dto.setIce(client.getIce());
        dto.setIdentiteFiscale(client.getIdentiteFiscale());
        dto.setAgentName(client.getAgentRecouv() != null ? client.getAgentRecouv().getNom() : null);
        return dto;
    }

    public static Client fromRequestDto(ClientRequestDTO dto, Utilisateur agent) {
        if (dto == null) return null;
        Client client = new Client();
        client.setRaisonSociale(dto.getRaisonSociale());
        client.setEmail(dto.getEmail());
        client.setTelephone(dto.getTelephone());
        client.setRc(dto.getRc());
        client.setAdresse(dto.getAdresse());
        client.setIce(dto.getIce());
        client.setIdentiteFiscale(dto.getIdentiteFiscale());
        client.setAgentRecouv(agent);
        return client;
    }

    public static void updateFromRequestDto(Client client, ClientRequestDTO dto, Utilisateur agent) {
        client.setRaisonSociale(dto.getRaisonSociale());
        client.setEmail(dto.getEmail());
        client.setTelephone(dto.getTelephone());
        client.setRc(dto.getRc());
        client.setAdresse(dto.getAdresse());
        client.setIce(dto.getIce());
        client.setIdentiteFiscale(dto.getIdentiteFiscale());
        client.setAgentRecouv(agent);
    }
} 