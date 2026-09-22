package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.StatutCreance;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Montants en BigDecimal(19,2) : le type Double ne represente pas exactement les
 * decimales monetaires, et les sommes cumulees derivaient au point qu'un solde
 * n'atteignait jamais exactement zero.
 *
 * @Getter/@Setter plutot que @Data : @Data genere equals/hashCode/toString sur
 * toutes les associations, dont la collection lazy reglements.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("supprimee = false")
public class Creance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "num_facture", nullable = false) // unique par organisation, cf. migration V2
    private String numFacture;

    @Column(name = "date_emission")
    private LocalDate dateEmission;

    @Column(name = "echeance")
    private LocalDate echeance;

    @Column(name = "montant_facture", precision = 19, scale = 2)
    private BigDecimal montantFacture = BigDecimal.ZERO;

    @Column(name = "montant_encaisse", precision = 19, scale = 2)
    private BigDecimal montantEncaisse = BigDecimal.ZERO;

    @Column(name = "montant_penalites", precision = 19, scale = 2)
    private BigDecimal montantPenalites = BigDecimal.ZERO;

    @Column(name = "date_calcul_penalites")
    private LocalDate dateCalculPenalites;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    private StatutCreance statut;

    /** Suppression logique : preserve l'historique des reglements associes. */
    @Column(name = "supprimee", nullable = false)
    private boolean supprimee = false;


    /**
     * Organisation proprietaire. Renseignee et filtree automatiquement par
     * Hibernate via @TenantId : ni les services ni les requetes n'ont a y penser.
     */
    @TenantId
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;
    @ManyToOne
    @JoinColumn(name = "agent_recouv")
    private Utilisateur agentRecouv;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @OneToMany(mappedBy = "creance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reglement> reglements = new ArrayList<>();

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Transient
    public BigDecimal getSolde() {
        return nz(montantFacture).add(nz(montantPenalites)).subtract(nz(montantEncaisse));
    }

    @Transient
    public BigDecimal getMontantTotal() {
        return nz(montantFacture).add(nz(montantPenalites));
    }

    @Transient
    public int getJoursRetard() {
        if (this.echeance == null || LocalDate.now().isBefore(this.echeance)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(this.echeance, LocalDate.now());
    }

    public void setMontantEncaisse(BigDecimal montantEncaisse) {
        this.montantEncaisse = nz(montantEncaisse);
        updateStatut();
    }

    public void setMontantPenalites(BigDecimal montantPenalites) {
        this.montantPenalites = nz(montantPenalites);
        updateStatut();
    }

    /**
     * Le statut se compare a la dette TOTALE (principal + penalites) : comparer au
     * seul montantFacture marquait une creance PAYEE alors que des penalites
     * restaient dues, effacant de fait ce solde.
     *
     * Le retard est evalue avant le paiement partiel, sinon une creance
     * partiellement payee restait indefiniment PARTIELLEMENT_PAYEE sans jamais
     * basculer en EN_RETARD ou PENALISEE.
     */
    public void updateStatut() {
        BigDecimal du = getMontantTotal();
        BigDecimal encaisse = nz(montantEncaisse);

        if (du.signum() > 0 && encaisse.compareTo(du) >= 0) {
            this.statut = StatutCreance.PAYEE;
            return;
        }

        int joursRetard = getJoursRetard();
        if (joursRetard >= 60) {
            this.statut = StatutCreance.PENALISEE;
        } else if (joursRetard > 0) {
            this.statut = StatutCreance.EN_RETARD;
        } else if (encaisse.signum() > 0) {
            this.statut = StatutCreance.PARTIELLEMENT_PAYEE;
        } else {
            this.statut = StatutCreance.IMPAYEE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Creance other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
