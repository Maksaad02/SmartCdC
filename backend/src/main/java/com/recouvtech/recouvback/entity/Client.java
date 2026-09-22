package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;

/**
 * L'unicite de raisonSociale, rc, ice et identiteFiscale est appliquee par
 * organisation (contraintes composites en base, migration V2__multitenancy.sql),
 * et non plus globalement : deux cabinets peuvent legitimement recouvrer aupres
 * de la meme societe debitrice.
 */
@Data
@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "telephone", nullable = false)
    private String telephone;

    @Column(name = "rc", nullable = false)
    private String rc;

    @Column(name = "adresse", nullable = false)
    private String adresse;

    @Column(name = "ice", nullable = false)
    private String ice;

    @Column(name = "identite_fiscale", nullable = false)
    private String identiteFiscale;


    /**
     * Organisation proprietaire. Renseignee et filtree automatiquement par
     * Hibernate via @TenantId : ni les services ni les requetes n'ont a y penser.
     */
    @TenantId
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;
    @ManyToOne
    @JoinColumn(name = "id_agent_recouv")
    private Utilisateur agentRecouv;
}
