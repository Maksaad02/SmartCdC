package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.entity.Departement;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartementScopeTest {

    @Mock private DepartementRepository departementRepository;
    @Mock private CurrentUser currentUser;
    @InjectMocks private DepartementScope scope;

    private Departement casa;
    private Departement rabat;

    @BeforeEach
    void setUp() {
        casa = new Departement("Casablanca", "CASA");
        casa.setId(1L);
        rabat = new Departement("Rabat", "RABAT");
        rabat.setId(2L);
    }

    @Test
    void unAdminChoisitLeDepartement() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(departementRepository.findById(2L)).thenReturn(Optional.of(rabat));

        assertSame(rabat, scope.pourCreation(2L));
    }

    @Test
    void unAdminDoitPreciserUnDepartementExistantEtActif() {
        when(currentUser.isAdmin()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> scope.pourCreation(null));

        when(departementRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> scope.pourCreation(9L));

        rabat.setActif(false);
        when(departementRepository.findById(2L)).thenReturn(Optional.of(rabat));
        assertThrows(IllegalArgumentException.class, () -> scope.pourCreation(2L));
    }

    @Test
    void unManagerCreeToujoursDansSonDepartement() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.departementId()).thenReturn(1L);
        when(departementRepository.findById(1L)).thenReturn(Optional.of(casa));

        assertSame(casa, scope.pourCreation(null));
        assertSame(casa, scope.pourCreation(1L));
    }

    /** Sortie de perimetre : refusee (403), pas ignoree en silence. */
    @Test
    void unManagerNePeutPasCreerDansUnAutreDepartement() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.departementId()).thenReturn(1L);

        assertThrows(AccessDeniedException.class, () -> scope.pourCreation(2L));
    }

    @Test
    void unCompteSansDepartementNePeutRienCreer() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.departementId()).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> scope.pourCreation(null));
    }
}
