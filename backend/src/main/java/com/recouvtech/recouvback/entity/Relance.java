package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// @Getter/@Setter plutot que @Data : equals/hashCode/toString generes sur toutes les
// associations parcourent creance -> client -> agent, ce qui declenche des chargements
// paresseux (et des boucles) des qu'une relance est comparee ou journalisee.
@Getter
@Setter
@Entity
@Filter(name = Departement.FILTRE, condition = Departement.CONDITION)
@NoArgsConstructor
@AllArgsConstructor
public class Relance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * Departement de la relance : herite de sa creance a la creation, jamais fourni par l'appelant.
     * Cloisonnement : filtre Hibernate "departement".
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id", nullable = false)
    private Departement departement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creance_id", referencedColumnName = "id", nullable = false)
    private Creance creance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_agent_recouv", nullable = false)
    private Utilisateur agentRecouv;

    @Column(name = "date_relance")
    private LocalDate dateRelance;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    @Column(name = "date_programmee")
    private LocalDateTime dateProgrammee;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_relance")
    private TypeRelance typeRelance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_relance")
    private StatutRelance statutRelance;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "agent_envoi")
    private String agentEnvoi;

    // Constructeur pour la création rapide
    public Relance(Creance creance, TypeRelance typeRelance, StatutRelance statutRelance) {
        this.creance = creance;
        this.typeRelance = typeRelance;
        this.statutRelance = statutRelance;
        this.dateCreation = LocalDateTime.now();
    }
}

