package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.mapper.UtilisateurMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilisateur ne porte pas @TenantId : la connexion doit pouvoir resoudre un
 * compte avant que l'organisation soit connue. Son cloisonnement est donc
 * explicite ici, et ce service est la seule voie d'acces.
 */
@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final CurrentUser currentUser;

    public UtilisateurResponseDTO create(UtilisateurRequestDTO dto) {
        if (utilisateurRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email déjà utilisé");
        }

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Rôle non trouvé"));
        assertPeutAttribuer(role);

        Utilisateur utilisateur = UtilisateurMapper.fromRequestDto(dto, role);
        // L'organisation vient de l'appelant, jamais de la requete.
        utilisateur.setOrganisation(appelant().getOrganisation());

        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    public List<UtilisateurResponseDTO> getAll() {
        List<Utilisateur> utilisateurs = currentUser.isSuperAdmin()
                ? utilisateurRepository.findAll()
                : utilisateurRepository.findByOrganisation_Id(organisationCourante());

        return utilisateurs.stream()
                .map(UtilisateurMapper::toDto)
                .collect(Collectors.toList());
    }

    public UtilisateurResponseDTO getById(Long id) {
        return UtilisateurMapper.toDto(chargerDansOrganisation(id));
    }

    public UtilisateurResponseDTO update(Long id, UtilisateurRequestDTO dto) {
        Utilisateur utilisateur = chargerDansOrganisation(id);

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        assertPeutAttribuer(role);

        UtilisateurMapper.updateFromRequestDto(utilisateur, dto, role);
        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    public void delete(Long id) {
        Utilisateur utilisateur = chargerDansOrganisation(id);
        if (utilisateur.getEmail().equals(currentUser.email())) {
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte");
        }
        utilisateurRepository.delete(utilisateur);
    }

    public UtilisateurResponseDTO getByEmail(String email) {
        return utilisateurRepository.findByEmail(email)
                .map(UtilisateurMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));
    }

    public UtilisateurResponseDTO updateRole(Long id, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Rôle manquant");
        }

        Utilisateur utilisateur = chargerDansOrganisation(id);

        RoleAgent nouveauRole;
        try {
            nouveauRole = RoleAgent.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rôle inconnu : " + roleName);
        }

        Role role = roleRepository.findByNom(nouveauRole)
                .orElseThrow(() -> new IllegalArgumentException("Rôle non trouvé"));
        assertPeutAttribuer(role);

        utilisateur.setRole(role);
        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    /**
     * Charge un utilisateur en verifiant qu'il releve de l'organisation de
     * l'appelant. Un id d'une autre organisation se comporte comme un id
     * inexistant : ne pas distinguer les deux evite de confirmer l'existence
     * d'un compte chez un autre client.
     */
    private Utilisateur chargerDansOrganisation(Long id) {
        if (currentUser.isSuperAdmin()) {
            return utilisateurRepository.findById(id)
                    .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur not found"));
        }
        return utilisateurRepository.findByIdAgentRecouvAndOrganisation_Id(id, organisationCourante())
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur not found"));
    }

    /**
     * Seul un SUPER_ADMIN peut attribuer SUPER_ADMIN : sans ce controle, un
     * administrateur d'organisation pouvait se hisser au niveau plateforme et
     * voir tous les clients.
     */
    private void assertPeutAttribuer(Role role) {
        if (role.getNom() == RoleAgent.SUPER_ADMIN && !currentUser.isSuperAdmin()) {
            throw new AccessDeniedException("Seul un SUPER_ADMIN peut attribuer ce rôle");
        }
    }

    private Utilisateur appelant() {
        return utilisateurRepository.findByEmail(currentUser.email())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur courant introuvable"));
    }

    private Long organisationCourante() {
        Long org = currentUser.organisationId();
        if (org == null) {
            throw new AccessDeniedException("Organisation courante inconnue");
        }
        return org;
    }
}
