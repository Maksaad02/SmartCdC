package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Client payant de la plateforme (un cabinet de recouvrement).
 *
 * Racine du cloisonnement : toute donnee metier appartient a exactement une
 * organisation. Cette entite n'est elle-meme PAS cloisonnee, sans quoi le
 * SUPER_ADMIN ne pourrait plus administrer la plateforme.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, unique = true)
    private String nom;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    public Organisation(String nom) {
        this.nom = nom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Organisation other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
