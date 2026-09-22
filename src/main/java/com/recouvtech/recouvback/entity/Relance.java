package com.recouvtech.recouvback.entity;

import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Relance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * Organisation proprietaire. Renseignee et filtree automatiquement par
     * Hibernate via @TenantId : ni les services ni les requetes n'ont a y penser.
     */
    @TenantId
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;
    @ManyToOne
    @JoinColumn(name = "creance_id", referencedColumnName = "id", nullable = false)
    public Creance creance;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_agent_recouv", nullable = false)
    private Utilisateur agentRecouv;

    @Column(name = "date_relance")
    public LocalDate dateRelance;

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
    public String commentaire;

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

