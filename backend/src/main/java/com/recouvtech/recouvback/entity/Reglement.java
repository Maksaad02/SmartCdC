package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;

import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reglement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

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
     * Organisation proprietaire. Renseignee et filtree automatiquement par
     * Hibernate via @TenantId : ni les services ni les requetes n'ont a y penser.
     */
    @TenantId
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creance_id")
    private Creance creance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_agent_recouv")
    private Utilisateur agentRecouv;
}
