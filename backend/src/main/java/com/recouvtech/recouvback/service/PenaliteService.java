package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.entity.Creance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class PenaliteService {

    /** Seuil de penalisation, en jours de retard. */
    public static final int SEUIL_PENALISATION = 60;

    /**
     * Taux mensuel applique apres le seuil (0,85 %).
     *
     * Constante publique : le texte des relances doit s'y referer plutot que de
     * recopier un taux en dur, une divergence ayant deja annonce 0,83 % aux
     * debiteurs alors que 0,85 % etait facture.
     */
    public static final BigDecimal TAUX_PENALITE_MENSUELLE = new BigDecimal("0.0085");

    /** Taux mensuel formate pour affichage, ex. "0.85". */
    public static String tauxMensuelPourcent() {
        return TAUX_PENALITE_MENSUELLE.multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros().toPlainString();
    }

    public BigDecimal calculerPenalites(Creance creance) {
        if (creance.getEcheance() == null || creance.getMontantFacture() == null) {
            return BigDecimal.ZERO;
        }

        int joursRetard = calculerJoursRetard(creance);
        if (joursRetard < SEUIL_PENALISATION) {
            return BigDecimal.ZERO;
        }

        // Mois complets ecoules APRES le seuil : 0 entre J+60 et J+89, 1 a J+90, etc.
        int moisApresSeuil = (joursRetard - SEUIL_PENALISATION) / 30;
        if (moisApresSeuil <= 0) {
            return BigDecimal.ZERO;
        }

        return creance.getMontantFacture()
                .multiply(TAUX_PENALITE_MENSUELLE)
                .multiply(BigDecimal.valueOf(moisApresSeuil))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public int calculerJoursRetard(Creance creance) {
        if (creance.getEcheance() == null || LocalDate.now().isBefore(creance.getEcheance())) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(creance.getEcheance(), LocalDate.now());
    }

    /**
     * Recalcule si la valeur du jour n'a pas deja ete calculee.
     *
     * Ne persiste rien : l'appelant decide d'enregistrer ou non, afin qu'une
     * simple lecture ne declenche pas d'ecriture.
     */
    public void mettreAJourPenalites(Creance creance) {
        LocalDate aujourdhui = LocalDate.now();

        if (aujourdhui.equals(creance.getDateCalculPenalites())) {
            return;
        }

        creance.setMontantPenalites(calculerPenalites(creance));
        creance.setDateCalculPenalites(aujourdhui);
    }

    public void forcerRecalculPenalites(Creance creance) {
        creance.setMontantPenalites(calculerPenalites(creance));
        creance.setDateCalculPenalites(LocalDate.now());
    }
}
