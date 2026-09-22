package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.OrganisationRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Organisation;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cree le premier compte SUPER_ADMIN au demarrage, a partir de variables
 * d'environnement.
 *
 * Necessaire depuis que /api/register est reserve aux administrateurs : sans
 * amorcage, il serait impossible de creer le tout premier compte. Ne fait rien
 * si des utilisateurs existent deja, ou si les variables ne sont pas fournies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    /** Doit correspondre a l'organisation inseree par la migration V2. */
    public static final String ORGANISATION_PAR_DEFAUT = "Organisation par defaut";

    private final UtilisateurRepository utilisateurRepository;
    private final OrganisationRepository organisationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-name:Administrateur}")
    private String adminName;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createFirstAdmin() {
        if (utilisateurRepository.count() > 0) {
            return;
        }
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Aucun utilisateur en base et ADMIN_EMAIL/ADMIN_PASSWORD non definis : "
                    + "aucun compte ne peut etre cree. Definissez ces variables puis redemarrez.");
            return;
        }

        Organisation organisation = organisationRepository.findByNom(ORGANISATION_PAR_DEFAUT)
                .orElseGet(() -> organisationRepository.save(new Organisation(ORGANISATION_PAR_DEFAUT)));

        // Le compte d'amorcage est SUPER_ADMIN : c'est l'exploitant de la
        // plateforme, celui qui cree ensuite les organisations clientes.
        Role superAdmin = roleRepository.findByNom(RoleAgent.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rôle SUPER_ADMIN introuvable"));

        Utilisateur admin = new Utilisateur();
        admin.setNom(adminName);
        admin.setEmail(adminEmail);
        admin.setMotDePasse(passwordEncoder.encode(adminPassword));
        admin.setRole(superAdmin);
        admin.setOrganisation(organisation);

        utilisateurRepository.save(admin);
        log.info("Compte SUPER_ADMIN initial cree pour {} (organisation « {} »). "
                        + "Changez ce mot de passe des la premiere connexion.",
                adminEmail, organisation.getNom());
    }
}
