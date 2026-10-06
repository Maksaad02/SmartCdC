package com.recouvtech.recouvback.service.facture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.recouvtech.recouvback.dto.FactureDTO.FactureExtractionDTO;
import com.recouvtech.recouvback.exception.ExtractionException;
import com.recouvtech.recouvback.security.CurrentUser;
import com.recouvtech.recouvback.service.ClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lecture d'une facture PDF pour pre-remplir une creance.
 *
 * Hybride : la couche texte est extraite localement et seul ce texte part chez le fournisseur d'IA ;
 * le fichier n'est envoye que pour une facture scannee. Aucune ecriture en base : l'utilisateur relit
 * et enregistre lui-meme. Pas de transaction ici : l'appel au modele dure plusieurs secondes et ne
 * doit pas retenir une connexion a la base.
 */
@Service
public class FactureExtractionService {

    private static final Duration FENETRE = Duration.ofHours(1);

    private final LecteurFacture lecteur;
    private final ClientService clientService;
    private final CurrentUser currentUser;
    private final int parHeure;
    /** Lectures par utilisateur sur une heure : chaque lecture a un cout chez le fournisseur. */
    private final Cache<String, AtomicInteger> compteurs = Caffeine.newBuilder()
            .expireAfterWrite(FENETRE)
            .maximumSize(10_000)
            .build();

    public FactureExtractionService(LecteurFacture lecteur, ClientService clientService, CurrentUser currentUser,
                                    @Value("${app.extraction.par-heure:30}") int parHeure) {
        this.lecteur = lecteur;
        this.clientService = clientService;
        this.currentUser = currentUser;
        this.parHeure = parHeure;
    }

    public boolean isActif() {
        return lecteur.isActif();
    }

    public FactureExtractionDTO extraire(byte[] pdf) {
        if (!lecteur.isActif()) {
            throw new ExtractionException(HttpStatus.SERVICE_UNAVAILABLE, "L'import de factures n'est pas configuré");
        }
        PdfFacture.verifier(pdf);
        String texte = PdfFacture.texte(pdf);
        consommerQuota();

        boolean scan = !PdfFacture.texteExploitable(texte);
        FactureLue lue = scan ? lecteur.lirePdf(pdf) : lecteur.lireTexte(texte);

        List<String> avertissements = new ArrayList<>();
        if (lue.remarques() != null) {
            lue.remarques().stream().filter(r -> r != null && !r.isBlank()).forEach(avertissements::add);
        }
        String numFacture = vide(lue.numFacture());
        LocalDate emission = date(lue.dateEmission(), "La date d'émission", avertissements);
        LocalDate echeance = date(lue.echeance(), "L'échéance", avertissements);
        BigDecimal montant = montant(lue.montantTTC(), avertissements);
        if (numFacture == null) {
            avertissements.add("Numéro de facture non trouvé : à saisir.");
        }
        if (echeance == null) {
            avertissements.add("Échéance non trouvée : à saisir.");
        } else if (emission != null && echeance.isBefore(emission)) {
            avertissements.add("L'échéance précède la date d'émission : à vérifier.");
        }

        String raisonSociale = vide(lue.clientRaisonSociale());
        String ice = vide(lue.clientIce());
        String clientTrouve = clientService.trouverPourFacture(ice, raisonSociale).orElse(null);
        if (clientTrouve == null) {
            avertissements.add(raisonSociale != null
                    ? "Client « " + raisonSociale + " » introuvable parmi vos clients : choisissez-le ou créez-le."
                    : "Client non identifié sur la facture : à choisir.");
        }

        return new FactureExtractionDTO(numFacture, emission, echeance, montant, raisonSociale, ice, clientTrouve,
                scan ? "SCAN" : "TEXTE", avertissements);
    }

    private void consommerQuota() {
        String cle = String.valueOf(currentUser.email());
        if (compteurs.get(cle, k -> new AtomicInteger()).incrementAndGet() > parHeure) {
            throw new ExtractionException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de factures importées en une heure, réessayez plus tard");
        }
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static LocalDate date(String valeur, String libelle, List<String> avertissements) {
        String v = vide(valeur);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            avertissements.add(libelle + " lue (« " + v + " ») est invalide : à saisir.");
            return null;
        }
    }

    private static BigDecimal montant(String valeur, List<String> avertissements) {
        String v = vide(valeur);
        if (v == null) {
            avertissements.add("Montant TTC non trouvé : à saisir.");
            return null;
        }
        try {
            BigDecimal montant = new BigDecimal(v.replace(" ", "").replace(" ", "").replace(',', '.'));
            if (montant.signum() <= 0) {
                avertissements.add("Le montant lu n'est pas positif : à vérifier.");
                return null;
            }
            return montant.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            avertissements.add("Le montant lu (« " + v + " ») est invalide : à saisir.");
            return null;
        }
    }
}
