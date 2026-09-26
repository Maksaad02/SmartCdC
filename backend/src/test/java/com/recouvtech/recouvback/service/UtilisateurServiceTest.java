package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UtilisateurService est la seule voie d'acces a Utilisateur, qui ne porte pas le filtre Hibernate de
 * departement (l'authentification doit pouvoir resoudre un compte avant de connaitre son departement).
 *
 * Invariant applique ici : un ADMIN n'appartient a aucun departement ; un MANAGER ou un AGENT
 * appartient a exactement un departement existant.
 */
@ExtendWith(MockitoExtension.class)
class UtilisateurServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private DepartementRepository departementRepository;
    @Mock private CurrentUser currentUser;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UtilisateurService utilisateurService;

    private static final String HASH_EXISTANT = "$2a$10$hashExistantQuiNeDoitPasEtreEcrase";
    private static final String HASH_NOUVEAU = "$2a$10$nouveauHashProduitParLEncodeur";

    private Departement casa;
    private Role roleAgent;
    private Role roleManager;
    private Role roleAdmin;
    private Utilisateur agentExistant;
    private UtilisateurRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);

        roleAgent = role(1L, RoleAgent.AGENT);
        roleManager = role(2L, RoleAgent.MANAGER);
        roleAdmin = role(3L, RoleAgent.ADMIN);

        agentExistant = new Utilisateur();
        agentExistant.setIdAgentRecouv(2L);
        agentExistant.setNom("John Doe");
        agentExistant.setEmail("john@entreprise.test");
        agentExistant.setMotDePasse(HASH_EXISTANT);
        agentExistant.setRole(roleAgent);
        agentExistant.setDepartement(casa);

        requestDTO = new UtilisateurRequestDTO();
        requestDTO.setNom("Jane Doe");
        requestDTO.setEmail("jane@entreprise.test");
        requestDTO.setMotDePasse("password456-long");
        requestDTO.setRoleId(1L);
        requestDTO.setDepartementId(1L);
    }

    private static Role role(Long id, RoleAgent nom) {
        Role r = new Role();
        r.setId(id);
        r.setNom(nom);
        return r;
    }

    // ------------------------------------------------------------------ creation

    @Test
    void creerUnAgentLeRattacheAuDepartementDemande() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.create(requestDTO);

        assertEquals(1L, result.getDepartementId());
        verify(utilisateurRepository).save(argThat(u -> u.getDepartement() != null && u.getDepartement().getId().equals(1L)));
    }

    @Test
    void creerUnManagerOuUnAgentSansDepartementEstRefuse() {
        requestDTO.setDepartementId(null);
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void creerUnAdminAvecUnDepartementEstRefuse() {
        requestDTO.setRoleId(3L);
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(roleAdmin));

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void creerUnAdminNeLeRattacheAAucunDepartement() {
        requestDTO.setRoleId(3L);
        requestDTO.setDepartementId(null);
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(roleAdmin));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.create(requestDTO);

        assertNull(result.getDepartementId());
    }

    @Test
    void creerDansUnDepartementInexistantOuDesactiveEstRefuse() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));

        casa.setActif(false);
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void creerHacheLeMotDePasseAvantDeLEnregistrer() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(passwordEncoder.encode("password456-long")).thenReturn(HASH_NOUVEAU);
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        utilisateurService.create(requestDTO);

        // Jamais la valeur saisie : c'est le hash qui atteint la base.
        verify(utilisateurRepository).save(argThat(u ->
                HASH_NOUVEAU.equals(u.getMotDePasse()) && !"password456-long".equals(u.getMotDePasse())));
    }

    @Test
    void creerSansMotDePasseEstRefuse() {
        requestDTO.setMotDePasse("  ");
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void creerAvecEmailExistantEstRefuse() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    // -------------------------------------------------------------- modification

    @Test
    void modifierSansMotDePasseConserveLeHashExistant() {
        requestDTO.setMotDePasse(null);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        utilisateurService.update(2L, requestDTO);

        assertEquals(HASH_EXISTANT, agentExistant.getMotDePasse());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void modifierAvecUnNouveauMotDePasseLeHacheEtRevoqueLesSessions() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(passwordEncoder.encode("password456-long")).thenReturn(HASH_NOUVEAU);
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        utilisateurService.update(2L, requestDTO);

        assertEquals(HASH_NOUVEAU, agentExistant.getMotDePasse());
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void modifierSansDepartementConserveLeDepartementActuel() {
        requestDTO.setDepartementId(null);
        requestDTO.setMotDePasse(null);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(roleAgent));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.update(2L, requestDTO);

        assertEquals(1L, result.getDepartementId());
    }

    // ------------------------------------------------------------ changement de role

    @Test
    void changerLeRoleAvecUneValeurVideOuInconnueEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.updateRole(2L, " ", null));
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.updateRole(2L, null, null));

        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.updateRole(2L, "PRESIDENT", null));
        assertThrows(IllegalArgumentException.class, () -> utilisateurService.updateRole(2L, "SUPER_ADMIN", null),
                "SUPER_ADMIN n'existe plus");
    }

    @Test
    void promouvoirUnAgentEnManagerConserveSonDepartement() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(roleRepository.findByNom(RoleAgent.MANAGER)).thenReturn(Optional.of(roleManager));
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.updateRole(2L, "manager", null);

        assertEquals("MANAGER", result.getRole());
        assertEquals(1L, result.getDepartementId());
        // Un role qui change ne doit pas survivre dans une session renouvelable.
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void promouvoirEnAdminRetireLeDepartement() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(roleRepository.findByNom(RoleAgent.ADMIN)).thenReturn(Optional.of(roleAdmin));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.updateRole(2L, "ADMIN", null);

        assertEquals("ADMIN", result.getRole());
        assertNull(result.getDepartementId(), "un ADMIN voit toute l'entreprise : aucun departement");
    }

    @Test
    void retrograderUnAdminSansDepartementEstRefuse() {
        Utilisateur admin = new Utilisateur();
        admin.setIdAgentRecouv(5L);
        admin.setEmail("admin@entreprise.test");
        admin.setRole(roleAdmin);
        when(utilisateurRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByNom(RoleAgent.AGENT)).thenReturn(Optional.of(roleAgent));

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.updateRole(5L, "AGENT", null),
                "un agent doit appartenir a un departement");
        verify(utilisateurRepository, never()).save(any());
    }

    // ------------------------------------------------------------------- lecture

    @Test
    void listerTransmetLesFiltresAuRepository() {
        when(utilisateurRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agentExistant)));

        Page<UtilisateurResponseDTO> result = utilisateurService.list(null, RoleAgent.AGENT, 1L, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("Casablanca", result.getContent().get(0).getDepartementNom());
    }

    @Test
    void getByIdInexistantRenvoieRessourceIntrouvable() {
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RessourceIntrouvableException.class, () -> utilisateurService.getById(1L));
    }

    @Test
    void deleteDeSonProprePropreCompteEstRefuse() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(agentExistant));
        when(currentUser.email()).thenReturn(agentExistant.getEmail());

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.delete(2L));
        verify(utilisateurRepository, never()).delete(any(Utilisateur.class));
    }
}
