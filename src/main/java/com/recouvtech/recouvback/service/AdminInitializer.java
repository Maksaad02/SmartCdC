package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
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
 * Cree le premier compte ADMIN au demarrage, a partir de variables
 * d'environnement.
 *
 * Necessaire depuis que /api/register est reserve aux ADMIN : sans amorcage, il
 * serait impossible de creer le tout premier compte. Ne fait rien si des
 * utilisateurs existent deja, ou si les variables ne sont pas fournies.
 *
 * S'execute apres RoleInitializer (ApplicationReadyEvent plutot que
 * @PostConstruct) pour que les roles soient deja en base.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    private final UtilisateurRepository utilisateurRepository;
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

        Role adminRole = roleRepository.findByNom(RoleAgent.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rôle ADMIN introuvable"));

        Utilisateur admin = new Utilisateur();
        admin.setNom(adminName);
        admin.setEmail(adminEmail);
        admin.setMotDePasse(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);

        utilisateurRepository.save(admin);
        log.info("Compte ADMIN initial cree pour {}. Changez ce mot de passe des la premiere connexion.",
                adminEmail);
    }
}
