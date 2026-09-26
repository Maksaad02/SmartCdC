package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Departement (succursale ou filiale interne) de l'entreprise.
 *
 * L'application est deployee en instance dediee : une seule entreprise, decoupee en departements.
 * Client, creance, reglement et relance appartiennent obligatoirement a un departement. Un ADMIN
 * voit tous les departements ; un MANAGER ou un AGENT n'est rattache qu'a un seul et ne voit que celui-la.
 *
 * Le filtre Hibernate "departement" est defini ici (une seule fois) et applique sur les quatre
 * entites metier ; il est active par DepartementFilterTransactionManager pour les roles cloisonnes.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@FilterDef(name = Departement.FILTRE, parameters = @ParamDef(name = Departement.PARAMETRE, type = Long.class))
public class Departement {

    /** Nom du filtre Hibernate de cloisonnement par departement. */
    public static final String FILTRE = "departement";
    /** Parametre du filtre : identifiant du departement de l'appelant. */
    public static final String PARAMETRE = "departementId";
    /** Condition SQL commune aux entites filtrees (colonne departement_id de chaque table metier). */
    public static final String CONDITION = "departement_id = :" + PARAMETRE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, unique = true)
    private String nom;

    /** Code court et stable (ex. SIEGE, CASA) : sert de reference dans les imports et les exports. */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    public Departement(String nom, String code) {
        this.nom = nom;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Departement other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
