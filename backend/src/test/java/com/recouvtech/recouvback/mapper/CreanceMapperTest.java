package com.recouvtech.recouvback.mapper;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Utilisateur;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * montantEncaisse et statut d'une creance decoulent des reglements et des
 * penalites. Ils ne doivent pas etre ecrivables depuis une requete, faute de
 * quoi un agent peut solder une dette sans aucun paiement.
 */
class CreanceMapperTest {

    @Test
    void leDtoDeRequeteNExposePasLesChampsFinanciersCalcules() {
        assertThrows(NoSuchFieldException.class,
                () -> CreanceRequestDTO.class.getDeclaredField("montantEncaisse"));
        assertThrows(NoSuchFieldException.class,
                () -> CreanceRequestDTO.class.getDeclaredField("statut"));
    }

    @Test
    void miseAJourNeModifiePasLeMontantEncaisse() {
        Creance creance = new Creance();
        creance.setNumFacture("F-001");
        creance.setEcheance(LocalDate.now().plusDays(30));
        creance.setMontantFacture(new BigDecimal("1000.00"));
        creance.setMontantEncaisse(new BigDecimal("250.00"));

        CreanceRequestDTO dto = new CreanceRequestDTO();
        dto.setEcheance(LocalDate.now().plusDays(60));
        dto.setMontantFacture(new BigDecimal("1200.00"));

        CreanceMapper.updateFromRequestDto(creance, dto, new Utilisateur(), new Client());

        assertEquals(0, new BigDecimal("250.00").compareTo(creance.getMontantEncaisse()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(creance.getMontantFacture()));
    }
}
