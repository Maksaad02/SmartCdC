package com.recouvtech.recouvback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Facture PDF d'origine d'une creance (au plus une par creance), stockee en base : couverte par les
 * sauvegardes MySQL et par le cloisonnement des departements comme le reste des donnees.
 *
 * Departement : toujours celui de la creance (cle etrangere composite en base, migration V8).
 * Le contenu n'est lu que pour le telechargement ; les metadonnees passent par une projection.
 */
@Entity
@Table(name = "creance_document")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = Departement.FILTRE, condition = Departement.CONDITION)
public class CreanceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creance_id", nullable = false, unique = true)
    private Creance creance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id", nullable = false)
    private Departement departement;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "taille", nullable = false)
    private long taille;

    /** Empreinte SHA-256 (hexadecimal) : integrite du fichier et detection de doublons. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Longueur au-dela de 16 Mo : correspond au LONGBLOB de la migration (sinon TINYBLOB attendu). */
    @Lob
    @Column(name = "contenu", nullable = false, length = 32 * 1024 * 1024)
    private byte[] contenu;

    @Column(name = "date_ajout", nullable = false)
    private LocalDateTime dateAjout;
}
