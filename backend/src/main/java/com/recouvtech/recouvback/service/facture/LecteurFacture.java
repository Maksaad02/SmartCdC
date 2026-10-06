package com.recouvtech.recouvback.service.facture;

/** Lecture des champs d'une facture par un modele d'IA. Interface : remplacable en test. */
public interface LecteurFacture {

    /** false si aucune cle d'API n'est configuree : la fonctionnalite est alors desactivee. */
    boolean isActif();

    /** Facture numerique : seul son texte, extrait localement, est envoye. */
    FactureLue lireTexte(String texte);

    /** Facture scannee (sans couche texte) : le PDF lui-meme est envoye pour une lecture visuelle. */
    FactureLue lirePdf(byte[] pdf);
}
