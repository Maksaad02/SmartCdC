package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Departement;
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
 * Cree, au demarrage, le departement « Siege » s'il n'en existe aucun, puis le premier compte ADMIN a
 * partir de variables d'environnement.
 *
 * Necessaire car la creation de compte est reservee aux administrateurs : sans amorcage, il serait
 * impossible de creer le tout premier compte. Le departement est cree meme sans variables : un
 * ADMIN peut ensuite en creer d'autres depuis l'application. Ne cree aucun compte si des
 * utilisateurs existent deja, ou si les variables ne sont pas fournies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    /** Doit correspondre au code insere par la migration V7. */
    public static final String DEPARTEMENT_PAR_DEFAUT_CODE = "SIEGE";
    public static final String DEPARTEMENT_PAR_DEFAUT_NOM = "Siège";

    private final UtilisateurRepository utilisateurRepository;
    private final DepartementRepository departementRepository;
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
        if (departementRepository.count() == 0) {
            departementRepository.save(new Departement(DEPARTEMENT_PAR_DEFAUT_NOM, DEPARTEMENT_PAR_DEFAUT_CODE));
        }
        if (utilisateurRepository.count() > 0) {
            return;
        }
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Aucun utilisateur en base et ADMIN_EMAIL/ADMIN_PASSWORD non definis : "
                    + "aucun compte ne peut etre cree. Definissez ces variables puis redemarrez.");
            return;
        }

        Role admin = roleRepository.findByNom(RoleAgent.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rôle ADMIN introuvable"));

        // Un ADMIN n'appartient a aucun departement : il voit toute l'entreprise.
        Utilisateur compte = new Utilisateur();
        compte.setNom(adminName);
        compte.setEmail(adminEmail);
        compte.setMotDePasse(passwordEncoder.encode(adminPassword));
        compte.setRole(admin);

        utilisateurRepository.save(compte);
        log.info("Compte ADMIN initial cree pour {}. Changez ce mot de passe des la premiere connexion.", adminEmail);
    }
}
