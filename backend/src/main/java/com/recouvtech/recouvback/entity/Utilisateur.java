package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "utilisateur")
public class Utilisateur implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAgentRecouv;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * Departement d'appartenance : obligatoire pour un MANAGER ou un AGENT, null pour un ADMIN
     * (qui voit toute l'entreprise). Invariant verifie par UtilisateurService.
     *
     * Volontairement SANS filtre Hibernate : l'authentification resout l'utilisateur par email
     * avant que son departement soit connu. Le cloisonnement des utilisateurs est applique
     * explicitement dans UtilisateurService, sur une surface reduite et relisible.
     * EAGER : le principal authentifie porte son departement (CurrentUser, filtre transactionnel).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departement_id")
    private Departement departement;

    /** Echecs de connexion consecutifs ; remis a zero apres une connexion reussie. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** Compte verrouille jusqu'a cette date apres trop d'echecs (null = non verrouille). */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getNom().name()));
    }

    @Override
    public String getPassword() {
        return motDePasse;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || !lockedUntil.isAfter(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}