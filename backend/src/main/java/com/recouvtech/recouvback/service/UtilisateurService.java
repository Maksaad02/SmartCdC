package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dao.spec.UtilisateurSpecs;
import com.recouvtech.recouvback.web.PageRequests;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.mapper.UtilisateurMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Gestion des comptes, reservee aux ADMIN (controle a l'entree du controleur).
 *
 * Invariant de rattachement, applique ici a la creation et a chaque modification :
 * un ADMIN n'appartient a aucun departement (il voit toute l'entreprise) ; un MANAGER ou un AGENT
 * appartient a exactement un departement.
 *
 * Utilisateur ne porte pas le filtre Hibernate de departement : la connexion doit pouvoir resoudre un
 * compte avant de connaitre son departement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UtilisateurService {

    private static final Set<String> SORTABLE = Set.of("idAgentRecouv", "nom", "email");

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final DepartementRepository departementRepository;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UtilisateurResponseDTO create(UtilisateurRequestDTO dto) {
        if (utilisateurRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email déjà utilisé");
        }

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Rôle non trouvé"));

        if (dto.getMotDePasse() == null || dto.getMotDePasse().isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire");
        }

        Utilisateur utilisateur = UtilisateurMapper.fromRequestDto(dto, role);
        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        utilisateur.setDepartement(departementPour(role.getNom(), dto.getDepartementId(), null));

        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    /** Liste paginee, avec filtres facultatifs par role, par departement et recherche. */
    public Page<UtilisateurResponseDTO> list(String q, RoleAgent role, Long departementId, Pageable pageable) {
        Specification<Utilisateur> spec = Specification.where(null);
        if (departementId != null) {
            spec = spec.and(UtilisateurSpecs.inDepartement(departementId));
        }
        if (role != null) {
            spec = spec.and(UtilisateurSpecs.hasRole(role));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(UtilisateurSpecs.matches(q.trim()));
        }
        return utilisateurRepository
                .findAll(spec, PageRequests.sanitize(pageable, SORTABLE, Sort.by("nom")))
                .map(UtilisateurMapper::toDto);
    }

    public UtilisateurResponseDTO getById(Long id) {
        return UtilisateurMapper.toDto(charger(id));
    }

    @Transactional
    public UtilisateurResponseDTO update(Long id, UtilisateurRequestDTO dto) {
        Utilisateur utilisateur = charger(id);

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        Departement departement = departementPour(role.getNom(), dto.getDepartementId(), utilisateur);

        UtilisateurMapper.updateFromRequestDto(utilisateur, dto, role);
        utilisateur.setDepartement(departement);
        // Un mot de passe absent conserve le hash actuel : ne jamais l'ecraser
        // par null, ni enregistrer la valeur brute.
        if (dto.getMotDePasse() != null && !dto.getMotDePasse().isBlank()) {
            utilisateur.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
            // Mot de passe change (par exemple apres une compromission) : les sessions ouvertes avec
            // l'ancien ne doivent pas continuer a se renouveler.
            refreshTokenService.revokeAllForUser(utilisateur.getIdAgentRecouv());
        }
        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    @Transactional
    public void delete(Long id) {
        Utilisateur utilisateur = charger(id);
        if (utilisateur.getEmail().equals(currentUser.email())) {
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte");
        }
        // Les sessions disparaissent avec le compte (ON DELETE CASCADE) ; la revocation explicite
        // evite qu'un renouvellement en cours n'aboutisse entre-temps.
        refreshTokenService.revokeAllForUser(utilisateur.getIdAgentRecouv());
        utilisateurRepository.delete(utilisateur);
    }

    public UtilisateurResponseDTO getByEmail(String email) {
        return utilisateurRepository.findByEmail(email)
                .map(UtilisateurMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));
    }

    /**
     * Changement de role. Vers ADMIN, le departement est retire ; vers MANAGER ou AGENT, un
     * departement est necessaire : celui fourni, sinon celui que le compte porte deja.
     */
    @Transactional
    public UtilisateurResponseDTO updateRole(Long id, String roleName, Long departementId) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Rôle manquant");
        }

        Utilisateur utilisateur = charger(id);

        RoleAgent nouveauRole;
        try {
            nouveauRole = RoleAgent.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rôle inconnu : " + roleName);
        }

        Role role = roleRepository.findByNom(nouveauRole)
                .orElseThrow(() -> new IllegalArgumentException("Rôle non trouvé"));
        Departement departement = departementPour(nouveauRole, departementId, utilisateur);

        utilisateur.setRole(role);
        utilisateur.setDepartement(departement);
        // Nouveau role : les sessions existantes doivent se reconnecter pour l'obtenir (et un role
        // retire ne doit pas survivre dans une session renouvelable).
        refreshTokenService.revokeAllForUser(utilisateur.getIdAgentRecouv());
        return UtilisateurMapper.toDto(utilisateurRepository.save(utilisateur));
    }

    /**
     * Applique l'invariant de rattachement : ADMIN sans departement ; MANAGER et AGENT avec un
     * departement existant et actif. "existant" est le compte modifie (son departement actuel sert de
     * valeur par defaut), null a la creation.
     */
    private Departement departementPour(RoleAgent role, Long departementId, Utilisateur existant) {
        if (role == RoleAgent.ADMIN) {
            if (departementId != null) {
                throw new IllegalArgumentException("Un administrateur n'est rattaché à aucun département");
            }
            return null;
        }
        Long id = departementId;
        if (id == null && existant != null && existant.getDepartement() != null) {
            id = existant.getDepartement().getId();
        }
        if (id == null) {
            throw new IllegalArgumentException("Le département est obligatoire pour un manager ou un agent");
        }
        Departement departement = departementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Département introuvable"));
        boolean inchange = existant != null && existant.getDepartement() != null
                && existant.getDepartement().getId().equals(id);
        if (!departement.isActif() && !inchange) {
            throw new IllegalArgumentException("Ce département est désactivé");
        }
        return departement;
    }

    private Utilisateur charger(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur not found"));
    }
}
