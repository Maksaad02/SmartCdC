package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentResolverTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CurrentUser currentUser;
    @InjectMocks private AgentResolver resolver;

    private Utilisateur moi;
    private Utilisateur collegue;

    @BeforeEach
    void setUp() {
        moi = new Utilisateur();
        moi.setEmail("moi@entreprise.test");
        moi.setNom("Moi");
        collegue = new Utilisateur();
        collegue.setEmail("collegue@entreprise.test");
        collegue.setNom("Collegue");
        lenient().when(currentUser.email()).thenReturn("moi@entreprise.test");
        lenient().when(utilisateurRepository.findByEmail("moi@entreprise.test")).thenReturn(Optional.of(moi));
    }

    /** Sans cela, un agent pouvait attribuer ses dossiers a autrui, ou agir au nom d'un autre. */
    @Test
    void unAgentEstToujoursForceASoiMemeMemeSiLaRequeteDesigneUnAutre() {
        when(currentUser.isManagerOrAbove()).thenReturn(false);

        assertSame(moi, resolver.resolve("Collegue", 1L));
        verify(utilisateurRepository, never()).findFirstByNomAndDepartement_IdOrderByIdAgentRecouv("Collegue", 1L);
    }

    @Test
    void unManagerPeutDesignerUnAgentDeCeDepartement() {
        when(currentUser.isManagerOrAbove()).thenReturn(true);
        when(utilisateurRepository.findFirstByNomAndDepartement_IdOrderByIdAgentRecouv("Collegue", 1L)).thenReturn(collegue);

        assertSame(collegue, resolver.resolve("Collegue", 1L));
    }

    @Test
    void unAgentDUnAutreDepartementEstIntrouvable() {
        when(currentUser.isManagerOrAbove()).thenReturn(true);
        when(utilisateurRepository.findFirstByNomAndDepartement_IdOrderByIdAgentRecouv("Collegue", 1L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("Collegue", 1L));
    }

    @Test
    void sansNomOuAvecSonPropreNomOnRetombeSurSoi() {
        when(currentUser.isManagerOrAbove()).thenReturn(true);

        assertSame(moi, resolver.resolve(null, 1L));
        assertSame(moi, resolver.resolve("  ", 1L));
        assertSame(moi, resolver.resolve("Moi", 1L));
    }

    @Test
    void unUtilisateurCourantIntrouvableEstRefuse() {
        when(utilisateurRepository.findByEmail("moi@entreprise.test")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> resolver.resolve(null, 1L));
    }
}
