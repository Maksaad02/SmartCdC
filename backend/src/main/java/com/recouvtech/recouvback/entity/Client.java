package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

/**
 * L'unicite de raisonSociale, rc, ice et identiteFiscale est globale a l'entreprise
 * (contraintes en base, migration V7) : un meme debiteur ne doit pas exister dans deux
 * departements. Le departement d'un client ne change pas apres creation : ses creances,
 * reglements et relances portent le meme departement (cles etrangeres composites).
 */
// @Getter/@Setter plutot que @Data : voir Creance (equals/toString sur les associations).
@Getter
@Setter
@Entity
@Filter(name = Departement.FILTRE, condition = Departement.CONDITION)
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

    // Facultatifs : NULL quand absents (voir V5), pour que l'unicite ne s'applique qu'aux valeurs renseignees.
    @Column(name = "rc")
    private String rc;

    @Column(name = "adresse", nullable = false)
    private String adresse;

    @Column(name = "ice")
    private String ice;

    @Column(name = "identite_fiscale")
    private String identiteFiscale;


    /**
     * Departement proprietaire (obligatoire). Cloisonnement : filtre Hibernate "departement",
     * active pour les roles MANAGER et AGENT ; l'ADMIN voit tous les departements.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id", nullable = false)
    private Departement departement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agent_recouv")
    private Utilisateur agentRecouv;
}
