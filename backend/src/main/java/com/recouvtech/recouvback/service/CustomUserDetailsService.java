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
     * principal doit porter l'organisation pour alimenter TenantContext.
     *
     * Utilisateur n'est pas cloisonne par @TenantId, precisement pour que cette
     * resolution fonctionne avant que l'organisation soit connue.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur user = utilisateurRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        // Force le chargement des associations EAGER avant detachement.
        user.getRole().getNom();
        user.getOrganisation().getId();

        return user;
    }
}
