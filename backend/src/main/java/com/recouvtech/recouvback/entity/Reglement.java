package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Filter(name = Departement.FILTRE, condition = Departement.CONDITION)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reglement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "montant", precision = 19, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_reglement")
    private LocalDate dateReglement;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement")
    private ModePaiement modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut")
    private StatutReglement statut = StatutReglement.NON_EFFECTUE; // Default value

    @Column(name = "reference")
    private String reference;


    /**
     * Departement du reglement : herite de sa creance a la creation, jamais fourni par l'appelant.
     * Cloisonnement : filtre Hibernate "departement".
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id", nullable = false)
    private Departement departement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creance_id")
    private Creance creance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent_recouv")
    private Utilisateur agentRecouv;
}
