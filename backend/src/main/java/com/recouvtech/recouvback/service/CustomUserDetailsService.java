package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    /**
     * Renvoie l'entite Utilisateur elle-meme comme principal (elle implemente
     * UserDetails), et non un org.springframework...User generique : le
     * principal doit porter son departement (filtre SQL de chaque transaction).
     *
     * Utilisateur n'est pas cloisonne par @TenantId, precisement pour que cette
     * resolution fonctionne avant que le departement soit connu.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur user = utilisateurRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        // Force le chargement des associations EAGER avant detachement.
        user.getRole().getNom();
        if (user.getDepartement() != null) {
            user.getDepartement().getId();
        }

        return user;
    }
}
