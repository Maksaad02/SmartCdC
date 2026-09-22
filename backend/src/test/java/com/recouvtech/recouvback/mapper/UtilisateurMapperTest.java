package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class UtilisateurMapperTest {

    private Utilisateur utilisateur;
    private UtilisateurRequestDTO requestDTO;
    private Role role;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setNom(RoleAgent.AGENT);

        utilisateur = new Utilisateur();
        utilisateur.setIdAgentRecouv(1L);
        utilisateur.setNom("John Doe");
        utilisateur.setEmail("john@example.com");
        utilisateur.setMotDePasse("password123");
        utilisateur.setRole(role);

        requestDTO = new UtilisateurRequestDTO();
        requestDTO.setNom("Jane Doe");
        requestDTO.setEmail("jane@example.com");
        requestDTO.setMotDePasse("password456");
        requestDTO.setRoleId(1L);
    }

    @Test
    void testToDto() {
        UtilisateurResponseDTO result = UtilisateurMapper.toDto(utilisateur);

        assertNotNull(result);
        assertEquals(utilisateur.getIdAgentRecouv(), result.getId());
        assertEquals(utilisateur.getNom(), result.getNom());
        assertEquals(utilisateur.getEmail(), result.getEmail());
        assertEquals(utilisateur.getRole().getNom().name(), result.getRole());
    }

    @Test
    void testToDtoWithNullUtilisateur() {
        UtilisateurResponseDTO result = UtilisateurMapper.toDto(null);
        assertNull(result);
    }

    @Test
    void testFromRequestDto() {
        Utilisateur result = UtilisateurMapper.fromRequestDto(requestDTO, role);

        assertNotNull(result);
        assertEquals(requestDTO.getNom(), result.getNom());
        assertEquals(requestDTO.getEmail(), result.getEmail());
        assertEquals(requestDTO.getMotDePasse(), result.getMotDePasse());
        assertEquals(role, result.getRole());
    }

    @Test
    void testFromRequestDtoWithNullDto() {
        Utilisateur result = UtilisateurMapper.fromRequestDto(null, role);
        assertNull(result);
    }

    @Test
    void testUpdateFromRequestDto() {
        UtilisateurMapper.updateFromRequestDto(utilisateur, requestDTO, role);

        assertEquals(requestDTO.getNom(), utilisateur.getNom());
        assertEquals(requestDTO.getEmail(), utilisateur.getEmail());
        assertEquals(requestDTO.getMotDePasse(), utilisateur.getMotDePasse());
        assertEquals(role, utilisateur.getRole());
    }
} 