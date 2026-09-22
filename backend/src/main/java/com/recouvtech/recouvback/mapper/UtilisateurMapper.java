package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;

public class UtilisateurMapper {
    public static UtilisateurResponseDTO toDto(Utilisateur utilisateur) {
        if (utilisateur == null) return null;
        UtilisateurResponseDTO dto = new UtilisateurResponseDTO();
        dto.setId(utilisateur.getIdAgentRecouv());
        dto.setNom(utilisateur.getNom());
        dto.setEmail(utilisateur.getEmail());
        dto.setRole(utilisateur.getRole() != null ? utilisateur.getRole().getNom().name() : null);
        return dto;
    }

    public static Utilisateur fromRequestDto(UtilisateurRequestDTO dto, Role role) {
        if (dto == null) return null;
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(dto.getNom());
        utilisateur.setEmail(dto.getEmail());
        utilisateur.setMotDePasse(dto.getMotDePasse());
        utilisateur.setRole(role);
        return utilisateur;
    }

    public static void updateFromRequestDto(Utilisateur utilisateur, UtilisateurRequestDTO dto, Role role) {
        utilisateur.setNom(dto.getNom());
        utilisateur.setEmail(dto.getEmail());
        utilisateur.setMotDePasse(dto.getMotDePasse());
        utilisateur.setRole(role);
    }
} 