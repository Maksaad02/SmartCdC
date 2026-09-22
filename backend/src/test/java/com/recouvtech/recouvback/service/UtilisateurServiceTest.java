package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurRequestDTO;
import com.recouvtech.recouvback.dto.UtilisateurDTO.UtilisateurResponseDTO;
import com.recouvtech.recouvback.entity.Organisation;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UtilisateurService est la seule voie d'acces a Utilisateur, qui ne porte pas
 * @TenantId (l'authentification doit pouvoir resoudre un compte avant de
 * connaitre l'organisation). Ces tests couvrent donc explicitement le
 * cloisonnement que Hibernate assure ailleurs automatiquement.
 */
@ExtendWith(MockitoExtension.class)
class UtilisateurServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private UtilisateurService utilisateurService;

    private static final Long ORG_A = 1L;
    private static final Long ORG_B = 2L;

    private Organisation organisationA;
    private Role roleAgent;
    private Role roleSuperAdmin;
    private Utilisateur appelant;
    private Utilisateur utilisateurOrgA;
    private UtilisateurRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        organisationA = new Organisation("Cabinet A");
        organisationA.setId(ORG_A);

        roleAgent = new Role();
        roleAgent.setId(1L);
        roleAgent.setNom(RoleAgent.AGENT);

        roleSuperAdmin = new Role();
        roleSuperAdmin.setId(2L);
        roleSuperAdmin.setNom(RoleAgent.SUPER_ADMIN);

        appelant = new Utilisateur();
        appelant.setIdAgentRecouv(1L);
        appelant.setEmail("admin@cabinet-a.test");
        appelant.setOrganisation(organisationA);

        utilisateurOrgA = new Utilisateur();
        utilisateurOrgA.setIdAgentRecouv(2L);
        utilisateurOrgA.setNom("John Doe");
        utilisateurOrgA.setEmail("john@cabinet-a.test");
        utilisateurOrgA.setMotDePasse("password123");
        utilisateurOrgA.setRole(roleAgent);
        utilisateurOrgA.setOrganisation(organisationA);

        requestDTO = new UtilisateurRequestDTO();
        requestDTO.setNom("Jane Doe");
        requestDTO.setEmail("jane@cabinet-a.test");
        requestDTO.setMotDePasse("password456");
        requestDTO.setRoleId(1L);
    }

    @Test
    void creerRattacheAuNouvelUtilisateurLOrganisationDeLAppelant() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(requestDTO.getRoleId())).thenReturn(Optional.of(roleAgent));
        when(currentUser.email()).thenReturn(appelant.getEmail());
        when(utilisateurRepository.findByEmail(appelant.getEmail())).thenReturn(Optional.of(appelant));
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        UtilisateurResponseDTO result = utilisateurService.create(requestDTO);

        assertNotNull(result);
        assertEquals(requestDTO.getEmail(), result.getEmail());
        verify(utilisateurRepository).save(argThat(u ->
                u.getOrganisation() != null && u.getOrganisation().getId().equals(ORG_A)));
    }

    @Test
    void seulUnSuperAdminPeutAttribuerLeRoleSuperAdmin() {
        requestDTO.setRoleId(2L);
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findById(requestDTO.getRoleId())).thenReturn(Optional.of(roleSuperAdmin));
        when(currentUser.isSuperAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void creerAvecEmailExistantEstRefuse() {
        when(utilisateurRepository.existsByEmail(requestDTO.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.create(requestDTO));
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void getAllPourUnAdminDOrganisationNeVoitQueSonOrganisation() {
        when(currentUser.isSuperAdmin()).thenReturn(false);
        when(currentUser.organisationId()).thenReturn(ORG_A);
        when(utilisateurRepository.findByOrganisation_Id(ORG_A))
                .thenReturn(List.of(utilisateurOrgA));

        List<UtilisateurResponseDTO> result = utilisateurService.getAll();

        assertEquals(1, result.size());
        verify(utilisateurRepository, never()).findAll();
    }

    @Test
    void getAllPourUnSuperAdminVoitToutesLesOrganisations() {
        when(currentUser.isSuperAdmin()).thenReturn(true);
        when(utilisateurRepository.findAll()).thenReturn(Arrays.asList(utilisateurOrgA));

        List<UtilisateurResponseDTO> result = utilisateurService.getAll();

        assertEquals(1, result.size());
        verify(utilisateurRepository, never()).findByOrganisation_Id(any());
    }

    @Test
    void getByIdDansSaPropreOrganisationFonctionne() {
        when(currentUser.isSuperAdmin()).thenReturn(false);
        when(currentUser.organisationId()).thenReturn(ORG_A);
        when(utilisateurRepository.findByIdAgentRecouvAndOrganisation_Id(2L, ORG_A))
                .thenReturn(Optional.of(utilisateurOrgA));

        UtilisateurResponseDTO result = utilisateurService.getById(2L);

        assertNotNull(result);
        assertEquals(utilisateurOrgA.getIdAgentRecouv(), result.getId());
    }

    /**
     * Le cas qui compte : un utilisateur d'une autre organisation ne doit pas
     * etre accessible, meme en connaissant son id.
     */
    @Test
    void getByIdSurUnUtilisateurDUneAutreOrganisationEstIntrouvable() {
        when(currentUser.isSuperAdmin()).thenReturn(false);
        when(currentUser.organisationId()).thenReturn(ORG_B);
        when(utilisateurRepository.findByIdAgentRecouvAndOrganisation_Id(2L, ORG_B))
                .thenReturn(Optional.empty());

        assertThrows(RessourceIntrouvableException.class, () -> utilisateurService.getById(2L));
    }

    @Test
    void getByIdInexistantRenvoieRessourceIntrouvable() {
        when(currentUser.isSuperAdmin()).thenReturn(false);
        when(currentUser.organisationId()).thenReturn(ORG_A);
        when(utilisateurRepository.findByIdAgentRecouvAndOrganisation_Id(1L, ORG_A))
                .thenReturn(Optional.empty());

        assertThrows(RessourceIntrouvableException.class, () -> utilisateurService.getById(1L));
    }

    @Test
    void deleteDeSonProprePropreCompteEstRefuse() {
        when(currentUser.isSuperAdmin()).thenReturn(true);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(utilisateurOrgA));
        when(currentUser.email()).thenReturn(utilisateurOrgA.getEmail());

        assertThrows(IllegalArgumentException.class, () -> utilisateurService.delete(2L));
        verify(utilisateurRepository, never()).delete(any());
    }
}
